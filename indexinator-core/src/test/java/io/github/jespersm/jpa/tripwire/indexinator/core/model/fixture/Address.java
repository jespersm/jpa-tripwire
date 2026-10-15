package io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture;

import jakarta.persistence.Embeddable;

/**
 * Embedded address value object.
 * Demonstrates @Embeddable: Customer has one Address, and OrderComment can have Note.
 */
@Embeddable
public class Address {
	private String street;
	private String city;
	private String zipCode;
	private String country;

	protected Address() {
	}

	public Address(String street, String city, String zipCode, String country) {
		this.street = street;
		this.city = city;
		this.zipCode = zipCode;
		this.country = country;
	}

	public String getStreet() {
		return street;
	}

	public String getCity() {
		return city;
	}

	public String getZipCode() {
		return zipCode;
	}

	public String getCountry() {
		return country;
	}
}
