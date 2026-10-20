package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

/**
 * Fallback resolver used when Hibernate metadata is not available.
 */
public class ReflectionFallbackMappingResolver implements RequirementMappingResolver {

    @Override
    public ResolvedRequirementMapping resolve(IndexRequirement requirement) {
        if (requirement.getPropertyPaths().size() == 1) {
            Field field = findField(requirement.getEntityClass(), requirement.getPropertyPaths().get(0));
            if (field != null && field.isAnnotationPresent(ElementCollection.class)) {
                return resolveCollection(requirement.getEntityClass(), field);
            }
        }
        String tableName = resolveTableName(requirement.getEntityClass());
        List<String> columns = new ArrayList<>();

        for (String propertyPath : requirement.getPropertyPaths()) {
            String columnName = propertyPathToColumn(requirement.getEntityClass(), propertyPath);
            if (columnName == null) {
                return null;
            }
            columns.add(columnName);
        }

        return columns.isEmpty() ? null : new ResolvedRequirementMapping(tableName, columns);
    }

    private ResolvedRequirementMapping resolveCollection(Class<?> entityClass, Field field) {
        CollectionTable table = field.getAnnotation(CollectionTable.class);
        Entity entity = entityClass.getAnnotation(Entity.class);
        String entityName = entity != null && !entity.name().isBlank() ? entity.name() : entityClass.getSimpleName();
        String tableName = table != null && !table.name().isBlank()
                ? table.name() : entityName + "_" + field.getName();
        List<String> columns = new ArrayList<>();
        if (table != null && table.joinColumns().length > 0) {
            for (JoinColumn joinColumn : table.joinColumns()) {
                if (joinColumn.name().isBlank()) {
                    String defaultColumn = defaultCollectionJoinColumn(entityClass, entityName);
                    if (table.joinColumns().length != 1 || defaultColumn == null) {
                        return null;
                    }
                    columns.add(defaultColumn);
                } else {
                    columns.add(joinColumn.name());
                }
            }
        } else {
            String defaultColumn = defaultCollectionJoinColumn(entityClass, entityName);
            if (defaultColumn == null) {
                return null;
            }
            columns.add(defaultColumn);
        }
        return new ResolvedRequirementMapping(tableName, columns);
    }

    private String defaultCollectionJoinColumn(Class<?> entityClass, String entityName) {
        // JPA's default join column uses the entity name and its single primary-key column.
        List<String> idColumns = new ArrayList<>();
        for (Class<?> current = entityClass; current != null; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                if (candidate.isAnnotationPresent(Id.class)) {
                    idColumns.add(propertyPathToColumn(entityClass, candidate.getName()));
                }
            }
        }
        return idColumns.size() == 1 ? entityName + "_" + idColumns.get(0) : null;
    }

    private String resolveTableName(Class<?> entityClass) {
        jakarta.persistence.Table table = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        return camelCaseToUnderscore(entityClass.getSimpleName());
    }

    private String propertyPathToColumn(Class<?> entityClass, String propertyPath) {
        String[] segments = propertyPath.split("\\.");
        if (segments.length == 0) {
            return null;
        }

        Field field = findField(entityClass, segments[0]);
        if (field == null) {
            return null;
        }

        jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }

        jakarta.persistence.JoinColumn joinColumn = field.getAnnotation(jakarta.persistence.JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isBlank()) {
            return joinColumn.name();
        }

        if (field.isAnnotationPresent(jakarta.persistence.ManyToOne.class) ||
            field.isAnnotationPresent(jakarta.persistence.OneToOne.class)) {
            return camelCaseToUnderscore(field.getName()) + "_id";
        }

        return camelCaseToUnderscore(field.getName());
    }

    private Field findField(Class<?> entityClass, String fieldName) {
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String camelCaseToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

