package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

import io.github.jespersm.jpa.tripwire.indexinator.core.model.Issue;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IndexInfo;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.TableSchema;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueSeverity;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Resolves requirements to relational columns and reports missing indexes.
 */
public class RequirementIssueDetector {

    public List<Issue> detectIssues(Collection<IndexRequirement> requirements, Map<String, TableSchema> schemas) {
        return detectIssues(requirements, schemas, null);
    }

    public List<Issue> detectIssues(Collection<IndexRequirement> requirements,
                                    Map<String, TableSchema> schemas,
                                    EntityManagerFactory entityManagerFactory) {
        List<Issue> issues = new ArrayList<>();
        List<RequirementMappingResolver> resolvers = resolverChain(entityManagerFactory);

        for (IndexRequirement requirement : requirements) {
            ResolvedRequirementMapping mapping = resolveMapping(requirement, resolvers);
            if (mapping == null) {
                continue;
            }

            TableSchema schema = schemas.get(mapping.tableName().toUpperCase());
            if (schema == null) {
                continue;
            }

            List<String> resolvedColumns = mapping.columnNames();

            if (hasCoveringIndex(schema, resolvedColumns, requirement.getIssueType() == IssueType.MISSING_FK_INDEX)) {
                continue;
            }

            issues.add(toIssue(requirement, mapping.tableName(), resolvedColumns));
        }

        return deduplicate(issues);
    }

    /**
     * Check that each entity's table exists and has a primary key, using the same resolver chain as the
     * index requirements. This takes naming strategies and inheritance mappings into account, so for
     * example a {@code SINGLE_TABLE} subclass is checked against its root table.
     *
     * @param metamodel JPA metamodel used to find the identifier attributes
     * @param entityClasses entity classes to check
     * @param schemas inspected database schema keyed by upper-case table name
     * @param entityManagerFactory runtime entity manager factory used to load mapping resolvers
     * @return table-level issues
     */
    public List<Issue> detectTableIssues(Metamodel metamodel,
                                         Collection<Class<?>> entityClasses,
                                         Map<String, TableSchema> schemas,
                                         EntityManagerFactory entityManagerFactory) {
        List<Issue> issues = new ArrayList<>();
        List<RequirementMappingResolver> resolvers = resolverChain(entityManagerFactory);

        for (Class<?> entityClass : entityClasses) {
            List<String> idAttributes = identifierAttributes(metamodel, entityClass);
            if (idAttributes.isEmpty()) {
                continue;
            }
            IndexRequirement requirement = IndexRequirement.forProperties(entityClass, idAttributes, true,
                    RequirementSource.JPA_IDENTIFIER, IssueType.MISSING_PRIMARY_KEY, IssueSeverity.CRITICAL,
                    "Entity '" + entityClass.getSimpleName() + "'");
            ResolvedRequirementMapping mapping = resolveMapping(requirement, resolvers);
            if (mapping == null) {
                continue;
            }

            TableSchema schema = schemas.get(mapping.tableName().toUpperCase());
            if (schema == null) {
                issues.add(new Issue(IssueSeverity.CRITICAL, IssueType.MISSING_PRIMARY_KEY, mapping.tableName(), null,
                        "Table does not exist in database", "Ensure the database schema is generated or updated"));
            } else if (schema.getPrimaryKeyColumns().isEmpty()) {
                issues.add(new Issue(IssueSeverity.CRITICAL, IssueType.MISSING_PRIMARY_KEY, mapping.tableName(), null,
                        "Table has no primary key defined",
                        "Add @Id annotation to an entity field or define a primary key in the database"));
            }
        }

        return deduplicate(issues);
    }

    private List<String> identifierAttributes(Metamodel metamodel, Class<?> entityClass) {
        try {
            EntityType<?> entityType = metamodel.entity(entityClass);
            if (entityType.hasSingleIdAttribute()) {
                return entityType.getSingularAttributes().stream()
                        .filter(SingularAttribute::isId)
                        .map(SingularAttribute::getName)
                        .toList();
            }
            return entityType.getIdClassAttributes().stream().map(SingularAttribute::getName).sorted().toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<RequirementMappingResolver> resolverChain(EntityManagerFactory entityManagerFactory) {
        List<RequirementMappingResolver> resolvers = new ArrayList<>();

        if (entityManagerFactory != null) {
            ServiceLoader<RequirementMappingResolverProvider> loader =
                    ServiceLoader.load(RequirementMappingResolverProvider.class);

            loader.stream()
                    .map(ServiceLoader.Provider::get)
                    .sorted(Comparator.comparingInt(RequirementMappingResolverProvider::priority).reversed())
                    .filter(provider -> provider.supports(entityManagerFactory))
                    .forEach(provider -> resolvers.addAll(provider.createResolvers(entityManagerFactory)));
        }

        // Fall back to reflection only when no provider understands the factory. A provider resolver knows the
        // physical mapping, so a requirement it leaves unresolved should be skipped rather than guessed.
        if (resolvers.isEmpty()) {
            resolvers.add(new ReflectionFallbackMappingResolver());
        }
        return resolvers;
    }

    private ResolvedRequirementMapping resolveMapping(IndexRequirement requirement,
                                                      List<RequirementMappingResolver> resolvers) {
        for (RequirementMappingResolver resolver : resolvers) {
            ResolvedRequirementMapping resolved = resolver.resolve(requirement);
            if (resolved != null && !resolved.columnNames().isEmpty()) {
                return resolved;
            }
        }
        return null;
    }

    private boolean hasCoveringIndex(TableSchema schema, List<String> columns, boolean foreignKey) {
        if (foreignKey) {
            return schema.getIndexes().stream().map(IndexInfo::getColumns)
                    .anyMatch(indexColumns -> indexColumns.size() >= columns.size() &&
                            containsAllIgnoreCase(indexColumns.subList(0, columns.size()), columns));
        }
        if (columns.size() == 1) {
            return schema.hasIndexOnColumn(columns.get(0));
        }

        for (IndexInfo index : schema.getIndexes()) {
            if (containsAllIgnoreCase(index.getColumns(), columns)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAllIgnoreCase(List<String> indexColumns, List<String> requiredColumns) {
        for (String required : requiredColumns) {
            boolean match = indexColumns.stream().anyMatch(existing -> existing.equalsIgnoreCase(required));
            if (!match) {
                return false;
            }
        }
        return true;
    }

    private Issue toIssue(IndexRequirement requirement, String tableName, List<String> resolvedColumns) {
        String columnsForSql = String.join(", ", resolvedColumns);
        String columnDisplay = resolvedColumns.size() == 1
                ? resolvedColumns.get(0)
                : columnsForSql;
        String indexName = "idx_" + tableName.toLowerCase() + "_" +
                String.join("_", resolvedColumns).toLowerCase();
        String createPrefix = requirement.isUnique() ? "CREATE UNIQUE INDEX" : "CREATE INDEX";

        return new Issue(
                requirement.getSeverity(),
                requirement.getIssueType(),
                tableName,
                columnDisplay,
                requirement.getContext() + " requires index on " + columnDisplay,
                createPrefix + " " + indexName + " ON " + tableName + "(" + columnsForSql + ")"
        );
    }

    private List<Issue> deduplicate(List<Issue> issues) {
        Map<String, Issue> deduped = new LinkedHashMap<>();
        for (Issue issue : issues) {
            String key = issue.type() + "|" + issue.tableName() + "|" + issue.columnName();
            deduped.putIfAbsent(key, issue);
        }
        return new ArrayList<>(deduped.values());
    }
}




