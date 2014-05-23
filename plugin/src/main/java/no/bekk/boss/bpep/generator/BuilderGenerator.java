package no.bekk.boss.bpep.generator;

import static no.bekk.boss.bpep.resolver.Resolver.getName;
import static no.bekk.boss.bpep.resolver.Resolver.getType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

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
	private final boolean formatSource;
	private final boolean useWithPrefix;

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
		pw.println("public " + builderClassName + "(" + clazzName + " bean){");
		for (IField field : fields) {
			pw.println("this." + getName(field) + "=bean." + getName(field)
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
			if ( this.useWithPrefix ) {
				String methodNameSuffix = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
				pw.println("public " + builderClassName + " with" + methodNameSuffix + "(" + fieldType + " " + parameterName + ") {");
			} else {
				pw.println("public " + builderClassName + " " + baseName + "(" + fieldType + " " + parameterName + ") {");
			}
			pw.println("this." + fieldName + "=" + parameterName + ";");
			pw.println("return this;\n}");

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

	private String getFieldBaseName(String fieldName) {
		IJavaProject javaProject = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject());
		return NamingConventions.getBaseName(NamingConventions.VK_INSTANCE_FIELD, fieldName, javaProject);
	}

	private void createFieldDeclarations(PrintWriter pw, List<IField> fields) throws JavaModelException {
		for (IField field : fields) {
			pw.println("private " + getType(field) + " " + getName(field) + ";");
		}
	}
	
	private void createStaticBuilderMethod(PrintWriter pw, IType clazz, String builderClassName)
    {
        String clazzName = clazz.getElementName();
//        String methodName = clazzName.substring(0, 1).toLowerCase() + clazzName.substring(1);
		String methodName = "builder";
        pw.println("public static " + builderClassName + " " + methodName + "(){");
        pw.println("return new " + builderClassName + "();\n}");
        if ( this.createCopyConstructor ) {
            pw.println("public static " + builderClassName + " " + methodName + "("+clazzName+" bean){");
            pw.println("return new " + builderClassName + "(bean);\n}");
        }
    }

	public static class Builder {
		boolean useWithPrefix;
		boolean createCopyConstructor;
		boolean formatSource;

		public Builder useWithPrefix(boolean useWithPrefixParam) {
			this.useWithPrefix = useWithPrefixParam;
			return this;
		}

		public Builder createCopyConstructor(boolean createCopyConstructorParam) {
			this.createCopyConstructor = createCopyConstructorParam;
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
		this.formatSource = builder.formatSource;
	}
}
