package no.bekk.boss.bpep.example;

import java.util.Collection;

public class Person {
	private final String firstname;
	private final String lastname;
	private final String address;
	private final String zipcode;
	private final String city;
	private final Collection<Person> dependents;

	public Person(String firstname, String lastname, String address,
			String zipcode, String city, Collection<Person> dependents) {
		this.firstname = firstname;
		this.lastname = lastname;
		this.address = address;
		this.zipcode = zipcode;
		this.city = city;
		this.dependents = dependents;
	}

}
