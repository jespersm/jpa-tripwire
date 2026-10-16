package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.*;

/**
 * ProductReview: Owning side of ManyToOne relationship to Product.
 * Tests FK-index requirement on ManyToOne (HIGH severity).
 */
@Entity
@Table(name = "product_reviews")
public class ProductReview {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Integer rating;

	@Column(length = 1000)
	private String comment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	protected ProductReview() {
	}

	public ProductReview(Integer rating, String comment, Product product) {
		this.rating = rating;
		this.comment = comment;
		this.product = product;
	}

	public Long getId() {
		return id;
	}

	public Integer getRating() {
		return rating;
	}

	public String getComment() {
		return comment;
	}

	public Product getProduct() {
		return product;
	}
}
