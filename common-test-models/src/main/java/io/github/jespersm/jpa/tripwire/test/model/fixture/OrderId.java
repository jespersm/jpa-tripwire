package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite ID as an embeddable value object for Order (year + orderNumber).
 * Demonstrates using @Embeddable for a composite primary key via @EmbeddedId.
 */
@Embeddable
public class OrderId implements Serializable {
	private Integer orderYear;
	private Long orderNumber;

	protected OrderId() {
	}

	public OrderId(Integer orderYear, Long orderNumber) {
		this.orderYear = orderYear;
		this.orderNumber = orderNumber;
	}

	public Integer getOrderYear() {
		return orderYear;
	}

	public Long getOrderNumber() {
		return orderNumber;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		OrderId orderId = (OrderId) o;
		return Objects.equals(orderYear, orderId.orderYear) &&
		       Objects.equals(orderNumber, orderId.orderNumber);
	}

	@Override
	public int hashCode() {
		return Objects.hash(orderYear, orderNumber);
	}
}
