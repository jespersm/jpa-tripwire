package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

/**
 * Source of an index requirement.
 */
public enum RequirementSource {
    /** Requirement inferred from relationship mappings such as {@code @ManyToOne}. */
    JPA_RELATIONSHIP,
    /** Requirement inferred from unique column declarations such as {@code @Column(unique=true)}. */
    JPA_UNIQUE_COLUMN,
    /** Requirement inferred from explicit JPA index declarations such as {@code @Table(indexes=...)}. */
    JPA_DECLARED_INDEX,
    /** Requirement inferred from Spring Data derived query methods. */
    SPRING_DATA_QUERY,
    /** Requirement inferred from the entity identifier, used to check the entity's table and primary key. */
    JPA_IDENTIFIER
}

