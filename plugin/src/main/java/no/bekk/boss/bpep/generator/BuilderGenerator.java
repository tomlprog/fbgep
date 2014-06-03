package no.bekk.boss.bpep.generator;

import static no.bekk.boss.bpep.resolver.Resolver.getName;
import static no.bekk.boss.bpep.resolver.Resolver.getType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class BuilderGenerator implements Generator {

	private final boolean createCopyConstructor;
	private final boolean createBuildFactoryMethodOnBean;
	private final boolean formatSource;
	private final boolean useWithPrefix;
	private final boolean generateAddedRemovedMethodsForCollections;
	private final boolean generateVarargMethodsForCollections;

	public void generate(ICompilationUnit cu, List<IField> fields) {

		try {
			removeOldClassConstructor(cu);
			removeOldBuilderClass(cu);
			
			IType clazz = cu.getTypes()[0];
			String clazzName = clazz.getElementName();
            String builderClassName = /*clazzName +*/ "Builder";

			IBuffer buffer = cu.getBuffer();
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			pw.println("public static class " + builderClassName + " {");


			int pos = clazz.getSourceRange().getOffset() + clazz.getSourceRange().getLength() - 1;

			createFieldDeclarations(pw, fields);

			if (createCopyConstructor) {
				createCopyConstructor(pw, clazz, fields, builderClassName);
			}

			createBuilderMethods(pw, fields, builderClassName);
			createPrivateBuilderConstructor(pw, clazz, fields);
			pw.println("}");
			if ( createBuildFactoryMethodOnBean ) {
			  createBuildFactoryMethodOnBean(pw, clazz, builderClassName);
			}
			createStaticBuilderMethod(pw, clazz, builderClassName);
			
			if (formatSource) {
				pw.println();
				buffer.replace(pos, 0, sw.toString());
				String builderSource = buffer.getContents();
			
				TextEdit text = ToolFactory.createCodeFormatter(null).format(CodeFormatter.K_COMPILATION_UNIT, builderSource, 0, builderSource.length(), 0, "\n");
				// text is null if source cannot be formatted
				if (text != null) {
					Document simpleDocument = new Document(builderSource);
					text.apply(simpleDocument);
					buffer.setContents(simpleDocument.get());
				} 
			} else {
				buffer.replace(pos, 0, sw.toString());	
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (MalformedTreeException e) {
			e.printStackTrace();
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}

	private void removeOldBuilderClass(ICompilationUnit cu) throws JavaModelException {
		for (IType type : cu.getTypes()[0].getTypes()) {
			if (type.getElementName().equals("Builder") && type.isClass()) {
				type.delete(true, null);
				break;
			}
		}
	}

	private void removeOldClassConstructor(ICompilationUnit cu) throws JavaModelException {
		for (IMethod method : cu.getTypes()[0].getMethods()) {
			if (method.isConstructor() && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].equals("QBuilder;")) {
				method.delete(true, null);
				break;
			}
		}
	}

	private void createCopyConstructor(PrintWriter pw, IType clazz, List<IField> fields, String builderClassName) {
		String clazzName = clazz.getElementName();
		pw.println("public " + builderClassName + "(){}");
		pw.println("public " + builderClassName + "(" + clazzName + " object){");
		for (IField field : fields) {
			pw.println("this." + getName(field) + "=object." + getName(field)
					+ ";");
		}
		pw.println("}");

	}

	private void createPrivateBuilderConstructor(PrintWriter pw, IType clazz, List<IField> fields) {
        String clazzName = clazz.getElementName();
        pw.println("public " + clazzName + " build(){");
        pw.println("return new " + clazzName +"(");
        Iterator<IField> iterator = fields.iterator();
        while(iterator.hasNext()) {
            IField field = iterator.next();
            String name = getName(field);
            pw.print(name);
            if (iterator.hasNext()) {
                pw.print(",");
            }
            
        }
        pw.println(");");
        pw.println("}");
    }

	private void createBuilderMethods(PrintWriter pw, List<IField> fields, String builderClassName) throws JavaModelException {
		for (IField field : fields) {
			String fieldName = getName(field);
			String fieldType = getType(field);
			String baseName = getFieldBaseName(fieldName);
			String parameterName = baseName;
			String methodNameSuffix = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
			String methodNamePrefix = (this.useWithPrefix) ?  (" with" + methodNameSuffix) : (baseName);
			boolean isCollection;
			Matcher matcher = Pattern.compile(
					"\\w*(Collection|List|Set)<(\\w+)>").matcher(fieldType);
			isCollection = (matcher.find());

			//@formatter:off
			if ( !isCollection ) {
			    printJavadoc(pw, baseName, "Sets", false);
				pw.println("public " + builderClassName + " " + methodNamePrefix + "(" + fieldType + " " + parameterName + ") {");
				pw.println("  this." + baseName + "=" + parameterName + ";");
				pw.println("return this;");
				pw.println("}");
			} else  {
			    printJavadoc(pw, baseName, "Sets", true);
			    pw.println("public " + builderClassName + " " + methodNamePrefix + "(" + fieldType + " " + parameterName + ") {");
				pw.println("  this." + baseName + " = new" + methodNameSuffix + "(check" + methodNameSuffix + "(" + parameterName + "));");
				pw.println("  return this;");
				pw.println("}");
	
				String ptype = matcher.group(2);
	
				if ( generateVarargMethodsForCollections ) {
					printJavadoc(pw, baseName, "Sets", true);
					pw.println("public " + builderClassName + " " + methodNamePrefix + "(" + ptype + " ... " + parameterName + ") {");
					pw.println("  return with"+methodNameSuffix+"(check" + methodNameSuffix + "(" + parameterName + "));");
					pw.println("}");
				}
				
				if ( generateAddedRemovedMethodsForCollections ) {
				    printJavadoc(pw, baseName, "Adds to", true);
					pw.println("public " + builderClassName + " " + methodNamePrefix + "Added(" + fieldType + " " + parameterName + ") {");
				    pw.println("  init"+methodNameSuffix+"();");
				    pw.println("  this." + baseName + ".addAll(check" + methodNameSuffix + "(" + parameterName + "));");
				    pw.println("  return this;");
					pw.println("}");
					
					if ( generateVarargMethodsForCollections ) {
						printJavadoc(pw, baseName, "Adds to", true);
						pw.println("public " + builderClassName + " " + methodNamePrefix + "Added(" + ptype + " ... " + parameterName + ") {");
						pw.println("return with"+methodNameSuffix+"Added(check" + methodNameSuffix + "(" + parameterName + "));");
						pw.println("}");
					}
					
				    printJavadoc(pw, baseName, "Removes from", true);
					pw.println("public " + builderClassName + " " + methodNamePrefix + "Removed(" + fieldType + " " + parameterName + ") {");
				    pw.println("init"+methodNameSuffix+"();");
				    pw.println("this." + baseName + ".removeAll(check" + methodNameSuffix + "(" + parameterName + "));");
				    pw.println("return this;");
					pw.println("}");
		
					if ( generateVarargMethodsForCollections ) {
						printJavadoc(pw, baseName, "Removes from", true);
						pw.println("public " + builderClassName + " " + methodNamePrefix + "Removed(" + ptype + " ... " + parameterName + ") {");
						pw.println("return with"+methodNameSuffix+"Removed(check" + methodNameSuffix + "(" + parameterName + "));");
						pw.println("}");
					}
					
				    pw.println("private void init" + methodNameSuffix + "() {");
				    pw.println("  if (this." + baseName + " == null) {");
				    pw.println("    this." + baseName + " = new" + methodNameSuffix + "(Collections.<" + ptype + ">emptySet());");
				    pw.println("  }");
				    pw.println("}");
		
				    pw.println("private Collection<" + ptype + "> new" + methodNameSuffix + "(Collection<" + ptype + "> " + parameterName + ") {");
				    pw.println("  return new TreeSet<" + ptype + ">(" + parameterName +");");
				    pw.println("}");
				}	
				
				pw.println("private " + fieldType + " check" + methodNameSuffix + "(" + fieldType + " " + parameterName + ") {");
			    pw.println("  if("+parameterName+" == null) {");
			    pw.println("    throw new IllegalArgumentException(\"The "+parameterName+" argument is required; it must not be null\");");
			    pw.println("  }");
			    pw.println("  if("+parameterName + ".contains(null)) {");
			    pw.println("    throw new IllegalArgumentException(\"The "+parameterName+" argument contains at least one null element; all elements must be not null\");");
			    pw.println("  }");
			    pw.println("  return " + parameterName + ";");
			    pw.println("}");

				if ( generateVarargMethodsForCollections ) {
					pw.println("private " + fieldType + " check" + methodNameSuffix + "(" + ptype + " ... " + parameterName + ") {");
				    pw.println("  if("+parameterName+" == null) {");
				    pw.println("    throw new IllegalArgumentException(\"The "+parameterName+" argument is required; it must not be null\");");
				    pw.println("  }");
				    pw.println("  return check" + methodNameSuffix + "(Arrays.asList("+parameterName+"));");
				    pw.println("}");
				}
			}

			//
//			pw.println("/*\nfieldName="+fieldName);
//			IType fieldIType = field.getType(fieldName, 1);
//			pw.println("fieldType="+fieldIType);
//			pw.println("fieldType.classFile="+fieldIType.getClassFile());
//			pw.println("fieldType.compilationUnit="+fieldIType.getCompilationUnit());
//			pw.println("ftfqn="+Arrays.deepToString(fieldIType.resolveType(fieldName)));
//			pw.println("fttqn="+JavaModelUtil.getResolvedTypeName(field.getTypeSignature(), fieldIType));
//			pw.println("TypeSignature="+field.getTypeSignature());
//			pw.println("SignatureSimpleName="+Signature.getSignatureSimpleName(field.getTypeSignature()));
//			pw.println("TypeParameters="+Arrays.asList(Signature.getTypeArguments(field.getTypeSignature())));
//			pw.println("TypeArguments="+Arrays.asList(Signature.getTypeArguments(field.getTypeSignature())));
//			pw.println("TypeErasure="+Signature.getTypeErasure(field.getTypeSignature()));
//			ITypeRoot typeRoot = field.getTypeRoot();
//			pw.println("TypeRoot="+typeRoot);
//			pw.println("*/");
		}
	}

	private void printJavadoc(PrintWriter pw, String fieldName, String verb, boolean throwsException) {
		pw.println("/**");
		pw.println(" * "+verb+" the {@link #"+fieldName+"} property of this builder");
		pw.println(" * @param "+fieldName+"");
		pw.println(" * @return this builder");
		if ( throwsException ) {
			pw.println(" * @throws IllegalArgumentException if "+fieldName+" is null or contains a null element");
		}
		pw.println(" */");
	}

	private void printAddedJavadoc(PrintWriter pw, String fieldName) {
		pw.println("/**");
		pw.println(" * Adds  the {@link #"+fieldName+"} property of this builder");
		pw.println(" * @param "+fieldName+"");
		pw.println(" * @return this builder");
		pw.println(" */");
	}

	private String getFieldBaseName(String fieldName) {
		IJavaProject javaProject = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject());
		return NamingConventions.getBaseName(NamingConventions.VK_INSTANCE_FIELD, fieldName, javaProject);
	}

	private void createFieldDeclarations(PrintWriter pw, List<IField> fields) throws JavaModelException {
		for (IField field : fields) {
			pw.println("private " + getType(field) + " " + getName(field) + ";");
		}
	}

	private void createBuildFactoryMethodOnBean(PrintWriter pw, IType clazz,
			String builderClassName) {
		String methodName = "build";
	  pw.println("/**");
	  pw.println(" * Creates a new {@link "+builderClassName+"} populated with the properties of this object. This is a convenience method which calls the");
	  pw.println(" * {@link #builder("+clazz.getElementName()+")} method with this object as the passed parameter.");
	  pw.println(" * @return a new "+builderClassName+" populated with this object's property values");
	  pw.println(" */");
      pw.println("public " + builderClassName + " " + methodName + "(){");
      pw.println("return new " + builderClassName + "(this);\n}");
	}
	
	private void createStaticBuilderMethod(PrintWriter pw, IType clazz, String builderClassName)
    {
        String clazzName = clazz.getElementName();
//        String methodName = clazzName.substring(0, 1).toLowerCase() + clazzName.substring(1);
		String methodName = "builder";
		pw.println("/**");
		pw.println(" * Creates a new {@link "+builderClassName+"} of {@link "+clazz.getElementName()+"} objects.");
		pw.println(" * @return a new "+builderClassName);
		pw.println("");
		pw.println(" */");
        pw.println("public static " + builderClassName + " " + methodName + "(){");
        pw.println("return new " + builderClassName + "();\n}");
        if ( this.createCopyConstructor ) {
    		pw.println("/**");
    		pw.println(" * Creates a new {@link "+builderClassName+"} of {@link "+clazz.getElementName()+"} objects.");
    		pw.println(" * The new builder is populated with the properties of the passed object.");
    		pw.println(" * @return a new "+builderClassName+" populated with the passed object's property values");
    		pw.println("");
    		pw.println(" */");
            pw.println("public static " + builderClassName + " " + methodName + "("+clazzName+" object){");
            pw.println("return new " + builderClassName + "(object);\n}");
        }
    }

	public static class Builder {
		boolean useWithPrefix;
		boolean generateAddedRemovedMethodsForCollections;
		boolean generateVarargMethodsForCollections;
		boolean createCopyConstructor;
		boolean createBuildFactoryMethodOnBean;
		boolean formatSource;

		public Builder useWithPrefix(boolean useWithPrefixParam) {
			this.useWithPrefix = useWithPrefixParam;
			return this;
		}

		public Builder generateAddedRemovedMethodsForCollections(
				boolean generateAddedRemovedMethodsForCollections) {
			this.generateAddedRemovedMethodsForCollections = generateAddedRemovedMethodsForCollections;
			return this;
		}

		public Builder generateVarargMethodsForCollections(
				boolean generateVarargMethodsForCollections) {
			this.generateVarargMethodsForCollections = generateVarargMethodsForCollections;
			return this;
		}

		public Builder createCopyConstructor(boolean createCopyConstructorParam) {
			this.createCopyConstructor = createCopyConstructorParam;
			return this;
		}

		public Builder createBuildFactoryMethodOnBean(boolean createBuildFactoryMethodOnBeanParam) {
			this.createBuildFactoryMethodOnBean = createBuildFactoryMethodOnBeanParam;
			return this;
		}

		public Builder formatSource(boolean formatSourceParam) {
			this.formatSource = formatSourceParam;
			return this;
		}

		public BuilderGenerator build() {
			return new BuilderGenerator(this);
		}
	}

	BuilderGenerator(Builder builder) {
		this.useWithPrefix = builder.useWithPrefix;
		this.createCopyConstructor = builder.createCopyConstructor;
		this.createBuildFactoryMethodOnBean = builder.createBuildFactoryMethodOnBean;
		this.formatSource = builder.formatSource;
		this.generateAddedRemovedMethodsForCollections = builder.generateAddedRemovedMethodsForCollections;
		this.generateVarargMethodsForCollections = builder.generateVarargMethodsForCollections;
	}
}
