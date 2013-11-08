package no.bekk.boss.bpep.example;
public class Person {
    private final String firstname;
    private final String lastname;
    private final String address;
    private final String zipcode;
    private final String city;

    public Person(String firstname, String lastname, String address,
            String zipcode, String city) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.address = address;
        this.zipcode = zipcode;
        this.city = city;
    }

    public static class PersonBuilder {
        private String firstname;
        private String lastname;
        private String address;
        private String zipcode;
        private String city;

        public PersonBuilder withFirstname(String firstname) {
            this.firstname = firstname;
            return this;
        }

        public PersonBuilder withLastname(String lastname) {
            this.lastname = lastname;
            return this;
        }

        public PersonBuilder withAddress(String address) {
            this.address = address;
            return this;
        }

        public PersonBuilder withZipcode(String zipcode) {
            this.zipcode = zipcode;
            return this;
        }

        public PersonBuilder withCity(String city) {
            this.city = city;
            return this;
        }

        public Person build() {
            return new Person(firstname, lastname, address, zipcode, city);
        }

        public static PersonBuilder person() {
            return new PersonBuilder();
        }
    }

}
