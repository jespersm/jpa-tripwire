package io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture;

import jakarta.persistence.*;

/**
 * LineItem: Demonstrates:
 * - ManyToOne relationship to Order (FK-index requirement, HIGH severity)
 * - ManyToOne relationship to Product (FK-index requirement, HIGH severity)
 * - Uses composite key columns from Order's embeddable OrderId
 */
@Entity
@Table(name = "line_items")
public class LineItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "order_year", referencedColumnName = "id_year", nullable = false),
			@JoinColumn(name = "order_number", referencedColumnName = "id_number", nullable = false)
	})
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private Integer quantity;

	@Column(precision = 10, scale = 2)
	private java.math.BigDecimal unitPrice;

	protected LineItem() {
	}

	public LineItem(Order order, Product product, Integer quantity, java.math.BigDecimal unitPrice) {
		this.order = order;
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() {
		return id;
	}

	public Order getOrder() {
		return order;
	}

	public Product getProduct() {
		return product;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public java.math.BigDecimal getUnitPrice() {
		return unitPrice;
	}
}
