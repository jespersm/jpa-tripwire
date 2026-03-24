package io.github.jespersm.twca.indexinator.core.requirement;

/**
 * Source of an index requirement.
 */
public enum RequirementSource {
    JPA_RELATIONSHIP,
    JPA_UNIQUE_COLUMN,
    JPA_DECLARED_INDEX,
    SPRING_DATA_QUERY
}

