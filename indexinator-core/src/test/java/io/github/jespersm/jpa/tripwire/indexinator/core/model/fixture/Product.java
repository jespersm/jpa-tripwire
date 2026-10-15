package io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Product: Demonstrates:
 * - OneToMany relationship to ProductReview (inverse)
 * - ManyToMany relationship with ProductCategory (owning side)
 * - OneToMany relationship to LineItem (inverse)
 */
@Entity
@Table(name = "products")
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 100)
	private String sku;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(precision = 10, scale = 2)
	private BigDecimal price;

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ProductReview> reviews = new HashSet<>();

	@ManyToMany
	@JoinTable(
			name = "product_category_mapping",
			joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "category_id")
	)
	private Set<ProductCategory> categories = new HashSet<>();

	@OneToMany(mappedBy = "product")
	private Set<LineItem> lineItems = new HashSet<>();

	protected Product() {
	}

	public Product(String sku, String name, BigDecimal price) {
		this.sku = sku;
		this.name = name;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public Set<ProductReview> getReviews() {
		return reviews;
	}

	public Set<ProductCategory> getCategories() {
		return categories;
	}

	public Set<LineItem> getLineItems() {
		return lineItems;
	}

	public void addReview(ProductReview review) {
		this.reviews.add(review);
	}

	public void addCategory(ProductCategory category) {
		this.categories.add(category);
		category.getProducts().add(this);
	}

	public void addLineItem(LineItem lineItem) {
		this.lineItems.add(lineItem);
	}
}
