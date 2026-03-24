package io.github.jespersm.twca.indexinator.core.model;

/**
 * Metadata about a relationship in a JPA entity
 */
public class RelationshipMetadata {
    private final RelationshipType type;
    private final String fieldName;
    private final String joinColumnName;
    private final String joinTableName;
    private final String referencedTableName;
    private final Class<?> targetEntity;

    public RelationshipMetadata(RelationshipType type, String fieldName,
                                String joinColumnName, String joinTableName, String referencedTableName,
                                Class<?> targetEntity) {
        this.type = type;
        this.fieldName = fieldName;
        this.joinColumnName = joinColumnName;
        this.joinTableName = joinTableName;
        this.referencedTableName = referencedTableName;
        this.targetEntity = targetEntity;
    }

    public RelationshipType getType() {
        return type;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getJoinColumnName() {
        return joinColumnName;
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public Class<?> getTargetEntity() {
        return targetEntity;
    }

    public String getJoinTableName() {
        return joinTableName;
    }

    public enum RelationshipType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }
}
