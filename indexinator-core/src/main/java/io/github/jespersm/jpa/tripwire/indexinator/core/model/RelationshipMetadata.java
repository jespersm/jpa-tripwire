package io.github.jespersm.jpa.tripwire.indexinator.core.model;

/**
 * Metadata about a relationship in a JPA entity.
 *
 * @param type relationship cardinality
 * @param fieldName Java field name declaring the relationship
 * @param joinColumnName owning-side join column name, when applicable
 * @param joinTableName join table name for many-to-many mappings, when applicable
 * @param referencedTableName referenced table name, when known
 * @param targetEntity target entity Java type
 */
public record RelationshipMetadata(RelationshipType type, String fieldName, String joinColumnName, String joinTableName,
                                   String referencedTableName, Class<?> targetEntity) {

    /**
     * Supported JPA relationship cardinalities.
     */
    public enum RelationshipType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY,
        ELEMENT_COLLECTION
    }
}
