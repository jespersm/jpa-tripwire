package io.github.jespersm.twca.indexinator.core.requirement;

import io.github.jespersm.twca.indexinator.core.model.Issue;
import io.github.jespersm.twca.indexinator.core.model.IndexInfo;
import io.github.jespersm.twca.indexinator.core.model.TableSchema;
import jakarta.persistence.EntityManagerFactory;

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

            if (hasCoveringIndex(schema, resolvedColumns)) {
                continue;
            }

            issues.add(toIssue(requirement, mapping.tableName(), resolvedColumns));
        }

        return deduplicate(issues);
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

        // Always include a deterministic fallback so behavior stays stable without providers.
        resolvers.add(new ReflectionFallbackMappingResolver());
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

    private boolean hasCoveringIndex(TableSchema schema, List<String> columns) {
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
            String key = issue.getType() + "|" + issue.getTableName() + "|" + issue.getColumnName();
            deduped.putIfAbsent(key, issue);
        }
        return new ArrayList<>(deduped.values());
    }
}




