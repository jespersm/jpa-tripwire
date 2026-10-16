package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Order: Demonstrates:
 * - Composite primary key via @EmbeddedId (OrderId embeddable)
 * - ManyToOne relationship to Customer (FK-index requirement, HIGH severity)
 * - OneToOne relationship to ShippingInfo (owning side with FK)
 * - OneToMany relationship to LineItem (inverse side)
 * - OneToMany relationship to OrderComment (inverse side)
 */
@Entity
@Table(name = "orders")
public class Order {
	@EmbeddedId
	@AttributeOverrides({
		@AttributeOverride(name = "orderYear", column = @Column(name = "id_year")),
		@AttributeOverride(name = "orderNumber", column = @Column(name = "id_number"))
	})
	private OrderId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@JoinColumn(name = "shipping_info_id")
	private ShippingInfo shippingInfo;

	@Column(nullable = false)
	private LocalDateTime orderDate;

	@Column(precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<LineItem> lineItems = new HashSet<>();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<OrderComment> comments = new HashSet<>();

	protected Order() {
	}

	public Order(Integer orderYear, Long orderNumber, Customer customer, LocalDateTime orderDate) {
		this.id = new OrderId(orderYear, orderNumber);
		this.customer = customer;
		this.orderDate = orderDate;
	}

	public OrderId getId() {
		return id;
	}

	public Integer getOrderYear() {
		return id.getOrderYear();
	}

	public Long getOrderNumber() {
		return id.getOrderNumber();
	}

	public Customer getCustomer() {
		return customer;
	}

	public ShippingInfo getShippingInfo() {
		return shippingInfo;
	}

	public void setShippingInfo(ShippingInfo shippingInfo) {
		this.shippingInfo = shippingInfo;
	}

	public LocalDateTime getOrderDate() {
		return orderDate;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public Set<LineItem> getLineItems() {
		return lineItems;
	}

	public void addLineItem(LineItem lineItem) {
		this.lineItems.add(lineItem);
	}

	public Set<OrderComment> getComments() {
		return comments;
	}

	public void addComment(OrderComment comment) {
		this.comments.add(comment);
	}
}
