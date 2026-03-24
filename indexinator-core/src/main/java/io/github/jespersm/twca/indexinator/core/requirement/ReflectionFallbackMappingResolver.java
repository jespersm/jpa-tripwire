package io.github.jespersm.twca.indexinator.core.requirement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Fallback resolver used when Hibernate metadata is not available.
 */
public class ReflectionFallbackMappingResolver implements RequirementMappingResolver {

    @Override
    public ResolvedRequirementMapping resolve(IndexRequirement requirement) {
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

