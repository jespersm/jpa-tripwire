package io.github.jespersm.twca.indexinator.core.requirement;

import io.github.jespersm.twca.indexinator.core.model.IssueSeverity;
import io.github.jespersm.twca.indexinator.core.model.IssueType;
import io.github.jespersm.twca.indexinator.core.model.QueryMethodMetadata;
import io.github.jespersm.twca.indexinator.core.model.RepositoryMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Collects index requirements from JPA metamodel and Spring Data repository metadata.
 */
public class MetamodelIndexRequirementCollector {

    public List<IndexRequirement> collect(Metamodel metamodel,
                                          Collection<Class<?>> entityClasses,
                                          List<RepositoryMetadata> repositories) {
        List<IndexRequirement> requirements = new ArrayList<>();

        for (Class<?> entityClass : entityClasses) {
            EntityType<?> entityType = getEntityType(metamodel, entityClass);
            if (entityType == null) {
                continue;
            }

            requirements.addAll(collectRelationshipRequirements(entityClass, entityType));
            requirements.addAll(collectUniqueColumnRequirements(entityClass, entityType));
            requirements.addAll(collectDeclaredIndexRequirements(entityClass));
        }

        requirements.addAll(collectRepositoryRequirements(repositories));
        return requirements;
    }

    private EntityType<?> getEntityType(Metamodel metamodel, Class<?> entityClass) {
        try {
            return metamodel.entity(entityClass);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<IndexRequirement> collectRelationshipRequirements(Class<?> entityClass,
                                                                   EntityType<?> entityType) {
        List<IndexRequirement> requirements = new ArrayList<>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            Attribute.PersistentAttributeType type = attribute.getPersistentAttributeType();
            if (type != Attribute.PersistentAttributeType.MANY_TO_ONE &&
                type != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                continue;
            }

            if (type == Attribute.PersistentAttributeType.ONE_TO_ONE && !isOwningOneToOne(entityClass, attribute.getName())) {
                continue;
            }

            IssueSeverity severity = type == Attribute.PersistentAttributeType.MANY_TO_ONE
                    ? IssueSeverity.HIGH
                    : IssueSeverity.MEDIUM;

            requirements.add(IndexRequirement.forProperties(
                    entityClass,
                    List.of(attribute.getName()),
                    false,
                    RequirementSource.JPA_RELATIONSHIP,
                    IssueType.MISSING_FK_INDEX,
                    severity,
                    "Relationship attribute '" + attribute.getName() + "'"
            ));
        }

        return requirements;
    }

    private List<IndexRequirement> collectUniqueColumnRequirements(Class<?> entityClass,
                                                                   EntityType<?> entityType) {
        List<IndexRequirement> requirements = new ArrayList<>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            Field field = findField(entityClass, attribute.getName());
            if (field == null) {
                continue;
            }

            Column column = field.getAnnotation(Column.class);
            if (column == null || !column.unique()) {
                continue;
            }

            requirements.add(IndexRequirement.forProperties(
                    entityClass,
                    List.of(attribute.getName()),
                    true,
                    RequirementSource.JPA_UNIQUE_COLUMN,
                    IssueType.MISSING_UNIQUE_INDEX,
                    IssueSeverity.MEDIUM,
                    "Unique attribute '" + attribute.getName() + "'"
            ));
        }

        return requirements;
    }

    private List<IndexRequirement> collectDeclaredIndexRequirements(Class<?> entityClass) {
        List<IndexRequirement> requirements = new ArrayList<>();
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            return requirements;
        }

        for (Index index : table.indexes()) {
            List<String> propertyPaths = resolveColumnNamesToPropertyPaths(entityClass, splitColumnList(index.columnList()));
            if (propertyPaths.isEmpty()) {
                continue;
            }

            requirements.add(IndexRequirement.forProperties(
                    entityClass,
                    propertyPaths,
                    index.unique(),
                    RequirementSource.JPA_DECLARED_INDEX,
                    IssueType.MISSING_DECLARED_INDEX,
                    IssueSeverity.MEDIUM,
                    "Declared @Table index '" + index.name() + "'"
            ));
        }

        for (UniqueConstraint constraint : table.uniqueConstraints()) {
            List<String> columns = Arrays.stream(constraint.columnNames())
                    .filter(name -> name != null && !name.isBlank())
                    .toList();

            List<String> propertyPaths = resolveColumnNamesToPropertyPaths(entityClass, columns);

            if (propertyPaths.isEmpty()) {
                continue;
            }

            requirements.add(IndexRequirement.forProperties(
                    entityClass,
                    propertyPaths,
                    true,
                    RequirementSource.JPA_DECLARED_INDEX,
                    IssueType.MISSING_UNIQUE_INDEX,
                    IssueSeverity.MEDIUM,
                    "Declared unique constraint '" + constraint.name() + "'"
            ));
        }

        return requirements;
    }

    private List<IndexRequirement> collectRepositoryRequirements(List<RepositoryMetadata> repositories) {
        List<IndexRequirement> requirements = new ArrayList<>();

        for (RepositoryMetadata repository : repositories) {
            Class<?> entityClass = repository.getEntityClass();

            for (QueryMethodMetadata queryMethod : repository.getQueryMethods()) {
                List<String> fields = queryMethod.getQueriedFields();
                if (fields.isEmpty()) {
                    continue;
                }

                for (String field : fields) {
                    requirements.add(IndexRequirement.forProperties(
                            entityClass,
                            List.of(field),
                            false,
                            RequirementSource.SPRING_DATA_QUERY,
                            IssueType.MISSING_QUERY_INDEX,
                            IssueSeverity.MEDIUM,
                            repository.getRepositoryInterface().getSimpleName() + "." + queryMethod.getMethodName()
                    ));
                }

                if (fields.size() > 1) {
                    requirements.add(IndexRequirement.forProperties(
                            entityClass,
                            fields,
                            false,
                            RequirementSource.SPRING_DATA_QUERY,
                            IssueType.POTENTIAL_COMPOSITE_INDEX,
                            IssueSeverity.LOW,
                            repository.getRepositoryInterface().getSimpleName() + "." + queryMethod.getMethodName()
                    ));
                }
            }
        }

        return requirements;
    }

    private boolean isOwningOneToOne(Class<?> entityClass, String fieldName) {
        Field field = findField(entityClass, fieldName);
        if (field == null) {
            return true;
        }

        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne == null) {
            return true;
        }

        return oneToOne.mappedBy().isBlank();
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

    private List<String> splitColumnList(String columnList) {
        if (columnList == null || columnList.isBlank()) {
            return List.of();
        }

        return Arrays.stream(columnList.split(","))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private List<String> resolveColumnNamesToPropertyPaths(Class<?> entityClass, List<String> columnNames) {
        List<String> propertyPaths = new ArrayList<>();
        for (String columnName : columnNames) {
            String propertyPath = findPropertyByColumnName(entityClass, columnName);
            if (propertyPath == null) {
                return List.of();
            }
            propertyPaths.add(propertyPath);
        }
        return propertyPaths;
    }

    private String findPropertyByColumnName(Class<?> entityClass, String columnName) {
        String normalized = columnName.trim();
        Class<?> current = entityClass;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                String mappedColumnName = mappedColumnName(field);
                if (mappedColumnName.equalsIgnoreCase(normalized)) {
                    return field.getName();
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private String mappedColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
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

    private String camelCaseToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

