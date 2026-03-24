package io.github.jespersm.twca.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata extracted from a JPA entity
 */
public class EntityMetadata {
    private final String entityName;
    private final String tableName;
    private final Class<?> entityClass;
    private final List<ColumnMetadata> columns;
    private final List<RelationshipMetadata> relationships;

    public EntityMetadata(String entityName, String tableName, Class<?> entityClass) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.entityClass = entityClass;
        this.columns = new ArrayList<>();
        this.relationships = new ArrayList<>();
    }

    public void addColumn(ColumnMetadata column) {
        this.columns.add(column);
    }

    public void addRelationship(RelationshipMetadata relationship) {
        this.relationships.add(relationship);
    }

    public String getEntityName() {
        return entityName;
    }

    public String getTableName() {
        return tableName;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public List<ColumnMetadata> getColumns() {
        return new ArrayList<>(columns);
    }

    public List<RelationshipMetadata> getRelationships() {
        return new ArrayList<>(relationships);
    }
}
