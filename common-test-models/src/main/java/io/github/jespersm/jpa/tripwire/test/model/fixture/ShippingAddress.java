package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.Embeddable;

/**
 * Another embedded address type used in ElementCollection.
 * Customer can have multiple ShippingAddress entries in a collection.
 */
@Embeddable
public class ShippingAddress {
	private Address address;
	private String instructions;
	private String label; // e.g., "home", "work", "parent"

	protected ShippingAddress() {
	}

	public ShippingAddress(Address address, String instructions, String label) {
		this.address = address;
		this.instructions = instructions;
		this.label = label;
	}

	public Address getAddress() {
		return address;
	}

	public String getInstructions() {
		return instructions;
	}

	public String getLabel() {
		return label;
	}
}
