package io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture;

import jakarta.persistence.*;

/**
 * ShippingInfo: Demonstrates OneToOne (non-owning side):
 * - Owned by Order (Order has FK to ShippingInfo)
 * - This side has mappedBy, so no FK-index requirement here
 * - Tests OneToOne non-owning relationship exclusion
 */
@Entity
@Table(name = "shipping_info")
public class ShippingInfo {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String carrier;

	@Column(nullable = false, length = 50)
	private String trackingNumber;

	@Embedded
	private Address address;

	@OneToOne(mappedBy = "shippingInfo")
	private Order order;

	protected ShippingInfo() {
	}

	public ShippingInfo(String carrier, String trackingNumber, Address address) {
		this.carrier = carrier;
		this.trackingNumber = trackingNumber;
		this.address = address;
	}

	public Long getId() {
		return id;
	}

	public String getCarrier() {
		return carrier;
	}

	public String getTrackingNumber() {
		return trackingNumber;
	}

	public Address getAddress() {
		return address;
	}

	public Order getOrder() {
		return order;
	}
}
