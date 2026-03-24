package io.github.jespersm.twca.indexinator.core.analyzer;

import io.github.jespersm.twca.indexinator.core.model.ColumnMetadata;
import io.github.jespersm.twca.indexinator.core.model.EntityMetadata;
import io.github.jespersm.twca.indexinator.core.model.RelationshipMetadata;
import io.github.jespersm.twca.indexinator.core.model.RelationshipMetadata.RelationshipType;
import jakarta.persistence.*;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Analyzes JPA entities to extract metadata about tables, columns, and relationships
 */
public class EntityAnalyzer {

    /**
     * Analyze a collection of JPA entity classes
     */
    public List<EntityMetadata> analyzeEntities(Collection<Class<?>> entityClasses) {
        List<EntityMetadata> results = new ArrayList<>();
        for (Class<?> entityClass : entityClasses) {
            EntityMetadata metadata = analyzeEntity(entityClass);
            if (metadata != null) {
                results.add(metadata);
            }
        }
        return results;
    }

    /**
     * Analyze a single JPA entity class
     */
    public EntityMetadata analyzeEntity(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            return null;
        }

        String entityName = getEntityName(entityClass);
        String tableName = getTableName(entityClass);

        EntityMetadata metadata = new EntityMetadata(entityName, tableName, entityClass);

        // Analyze fields
        analyzeFields(entityClass, metadata);

        return metadata;
    }

    private void analyzeFields(Class<?> entityClass, EntityMetadata metadata) {
        // Get all fields including inherited ones
        List<Field> allFields = getAllFields(entityClass);

        for (Field field : allFields) {
            field.setAccessible(true);

            // Check if it's a relationship
            if (isRelationship(field)) {
                analyzeRelationship(field, metadata);
            } else if (isColumn(field)) {
                analyzeColumn(field, metadata);
            }
        }
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private boolean isRelationship(Field field) {
        return field.isAnnotationPresent(OneToOne.class) ||
               field.isAnnotationPresent(OneToMany.class) ||
               field.isAnnotationPresent(ManyToOne.class) ||
               field.isAnnotationPresent(ManyToMany.class);
    }

    private boolean isColumn(Field field) {
        return !field.isAnnotationPresent(Transient.class) &&
               !isRelationship(field);
    }

    private void analyzeColumn(Field field, EntityMetadata metadata) {
        String fieldName = field.getName();
        String columnName = getColumnName(field);
        boolean isPrimaryKey = field.isAnnotationPresent(Id.class);
        boolean isUnique = isUniqueColumn(field);
        boolean isNullable = isNullableColumn(field);

        ColumnMetadata column = new ColumnMetadata(
                fieldName, columnName, isPrimaryKey, isUnique, isNullable, field.getType()
        );

        metadata.addColumn(column);
    }

    private void analyzeRelationship(Field field, EntityMetadata metadata) {
        RelationshipType type = getRelationshipType(field);
        String fieldName = field.getName();
        String joinColumnName = getJoinColumnName(field, fieldName);
        String joinTableName = getJoinTableName(field, fieldName);
        Class<?> targetEntity = getTargetEntity(field);
        String referencedTableName = targetEntity != null ? getTableName(targetEntity) : null;

        RelationshipMetadata relationship = new RelationshipMetadata(
                type, fieldName, joinColumnName, joinTableName, referencedTableName, targetEntity
        );

        metadata.addRelationship(relationship);
    }

    private String getEntityName(Class<?> entityClass) {
        Entity entity = entityClass.getAnnotation(Entity.class);
        if (entity != null && !entity.name().isEmpty()) {
            return entity.name();
        }
        return entityClass.getSimpleName();
    }

    private String getTableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null && !table.name().isEmpty()) {
            return table.name();
        }
        return camelCaseToUnderscore(entityClass.getSimpleName());
    }

    private String getColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()) {
            return column.name();
        }
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isEmpty()) {
            return joinColumn.name();
        }
        return camelCaseToUnderscore(field.getName());
    }

    private boolean isUniqueColumn(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column != null && column.unique();
    }

    private boolean isNullableColumn(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column == null || column.nullable();
    }

    private RelationshipType getRelationshipType(Field field) {
        if (field.isAnnotationPresent(OneToOne.class)) return RelationshipType.ONE_TO_ONE;
        if (field.isAnnotationPresent(OneToMany.class)) return RelationshipType.ONE_TO_MANY;
        if (field.isAnnotationPresent(ManyToOne.class)) return RelationshipType.MANY_TO_ONE;
        if (field.isAnnotationPresent(ManyToMany.class)) return RelationshipType.MANY_TO_MANY;
        return null;
    }

    private boolean isOwningSide(Field field) {
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne != null) {
            return oneToOne.mappedBy() == null;
        }
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany != null) {
            return oneToMany.mappedBy() == null;
        }
        if (field.isAnnotationPresent(ManyToOne.class)) {
            return true;
        }
        ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
        if (manyToMany != null) {
            return manyToMany.mappedBy() == null;
        }
        return false;
    }

    private String getJoinColumnName(Field field, String defaultName) {
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isEmpty()) {
            return joinColumn.name();
        }

        // For ManyToOne and OneToOne, default is fieldName_id
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
            return camelCaseToUnderscore(defaultName) + "_id";
        }
        // For ManyToOne and OneToOne, default is fieldName_id
        if (field.isAnnotationPresent(ManyToMany.class) && field.isAnnotationPresent(JoinTable.class)) {
            return camelCaseToUnderscore(defaultName) + "_id";
        }

        return null;
    }

    private String getJoinTableName(Field field, String defaultName) {
        JoinTable joinTable = field.getAnnotation(JoinTable.class);
        if (joinTable != null && !joinTable.name().isEmpty()) {
            return joinTable.name();
        }
        return null;
    }

    private Class<?> getTargetEntity(Field field) {
        // Try to get target entity from annotation
        if (field.isAnnotationPresent(OneToOne.class)) {
            OneToOne annotation = field.getAnnotation(OneToOne.class);
            if (annotation.targetEntity() != void.class) {
                return annotation.targetEntity();
            }
        }
        if (field.isAnnotationPresent(OneToMany.class)) {
            OneToMany annotation = field.getAnnotation(OneToMany.class);
            if (annotation.targetEntity() != void.class) {
                return annotation.targetEntity();
            }
        }
        if (field.isAnnotationPresent(ManyToOne.class)) {
            ManyToOne annotation = field.getAnnotation(ManyToOne.class);
            if (annotation.targetEntity() != void.class) {
                return annotation.targetEntity();
            }
        }
        if (field.isAnnotationPresent(ManyToMany.class)) {
            ManyToMany annotation = field.getAnnotation(ManyToMany.class);
            if (annotation.targetEntity() != void.class) {
                return annotation.targetEntity();
            }
        }

        // Infer from field type
        if (Collection.class.isAssignableFrom(field.getType())) {
            // For collections, we can't easily infer the generic type at runtime
            return null;
        }

        return field.getType();
    }

    private String camelCaseToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
