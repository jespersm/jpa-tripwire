package io.github.jespersm.jpa.tripwire.test.model.fixture;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * OrderComment: Demonstrates:
 * - ManyToOne relationship to Order (FK-index requirement, HIGH severity)
 * - Uses composite key columns from Order's embeddable OrderId
 * - @Embedded Note (embedded value object)
 * - @ElementCollection tags (tests ElementCollection scenario)
 */
@Entity
@Table(name = "order_comments")
public class OrderComment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "order_year", referencedColumnName = "id_year", nullable = false),
			@JoinColumn(name = "order_number", referencedColumnName = "id_number", nullable = false)
	})
	private Order order;

	@Embedded
	private Note note;

	@ElementCollection
	@CollectionTable(name = "comment_tags", joinColumns = @JoinColumn(name = "comment_id"))
	@Column(name = "tag")
	private Set<String> tags = new HashSet<>();

	protected OrderComment() {
	}

	public OrderComment(Order order, Note note) {
		this.order = order;
		this.note = note;
	}

	public Long getId() {
		return id;
	}

	public Order getOrder() {
		return order;
	}

	public Note getNote() {
		return note;
	}

	public Set<String> getTags() {
		return tags;
	}

	public void addTag(String tag) {
		this.tags.add(tag);
	}
}
