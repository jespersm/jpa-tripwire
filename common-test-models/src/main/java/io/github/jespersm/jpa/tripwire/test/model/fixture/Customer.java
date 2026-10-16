package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Customer: Demonstrates:
 * - @Embedded single Address object
 * - @ElementCollection ShippingAddress (reuses Address embeddable)
 * - OneToMany relationship to Order (inverse side)
 */
@Entity
@Table(name = "customers")
public class Customer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 100)
	private String email;

	@Column(nullable = false, length = 100)
	private String firstName;

	@Column(nullable = false, length = 100)
	private String lastName;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "street", column = @Column(name = "billing_street")),
			@AttributeOverride(name = "city", column = @Column(name = "billing_city")),
			@AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip")),
			@AttributeOverride(name = "country", column = @Column(name = "billing_country"))
	})
	private Address billingAddress;

	@ElementCollection
	@CollectionTable(name = "customer_shipping_addresses", joinColumns = @JoinColumn(name = "customer_id"))
	private Set<ShippingAddress> shippingAddresses = new HashSet<>();

	@OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<Order> orders = new HashSet<>();

	protected Customer() {
	}

	public Customer(String email, String firstName, String lastName, Address billingAddress) {
		this.email = email;
		this.firstName = firstName;
		this.lastName = lastName;
		this.billingAddress = billingAddress;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public Address getBillingAddress() {
		return billingAddress;
	}

	public Set<ShippingAddress> getShippingAddresses() {
		return shippingAddresses;
	}

	public void addShippingAddress(ShippingAddress address) {
		this.shippingAddresses.add(address);
	}

	public Set<Order> getOrders() {
		return orders;
	}

	public void addOrder(Order order) {
		this.orders.add(order);
	}
}
