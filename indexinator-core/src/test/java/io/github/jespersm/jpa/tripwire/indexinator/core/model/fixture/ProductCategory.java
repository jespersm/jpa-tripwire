package io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * ProductCategory: Inverse side of ManyToMany with Product.
 * Tests ManyToMany join-table scenarios.
 */
@Entity
@Table(name = "product_categories")
public class ProductCategory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String name;

	@ManyToMany(mappedBy = "categories")
	private Set<Product> products = new HashSet<>();

	protected ProductCategory() {
	}

	public ProductCategory(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<Product> getProducts() {
		return products;
	}
}
