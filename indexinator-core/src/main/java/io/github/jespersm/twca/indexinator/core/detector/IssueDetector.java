package io.github.jespersm.twca.indexinator.core.detector;

import io.github.jespersm.twca.indexinator.core.model.*;
import io.github.jespersm.twca.indexinator.core.model.RelationshipMetadata.RelationshipType;

import java.util.*;

/**
 * Detects issues by comparing JPA entity metadata with actual database schema
 */
public class IssueDetector {

    /**
     * Detect all issues (entities only)
     */
    public List<Issue> detectIssues(List<EntityMetadata> entities, Map<String, TableSchema> schemas) {
        return detectIssues(entities, new ArrayList<>(), schemas);
    }

    /**
     * Detect all issues including repository query methods
     */
    public List<Issue> detectIssues(List<EntityMetadata> entities,
                                     List<RepositoryMetadata> repositories,
                                     Map<String, TableSchema> schemas) {
        List<Issue> issues = new ArrayList<>();

        for (EntityMetadata entity : entities) {
            String tableName = entity.getTableName().toUpperCase();
            TableSchema schema = schemas.get(tableName);

            if (schema == null) {
                // Table doesn't exist - this is a critical issue
                issues.add(new Issue(
                        IssueSeverity.CRITICAL,
                        IssueType.MISSING_PRIMARY_KEY,
                        entity.getTableName(),
                        null,
                        "Table does not exist in database",
                        "Ensure the database schema is generated or updated"
                ));
                continue;
            }

            // Check for missing primary key
            issues.addAll(checkMissingPrimaryKey(entity, schema));

            // Check for missing indexes on foreign keys
            issues.addAll(checkMissingForeignKeyIndexes(entity, schema));

            // Check for missing join table
            issues.addAll(checkMissingJoinTables(entity, schemas));

            // Check for missing indexes on unique columns
            issues.addAll(checkMissingUniqueIndexes(entity, schema));
        }

        // Check repository query methods
        if (!repositories.isEmpty()) {
            issues.addAll(checkRepositoryQueryIndexes(repositories, entities, schemas));
        }

        return issues;
    }

    /**
     * Check if table has a primary key
     */
    private List<Issue> checkMissingPrimaryKey(EntityMetadata entity, TableSchema schema) {
        List<Issue> issues = new ArrayList<>();

        if (schema.getPrimaryKeyColumns().isEmpty()) {
            issues.add(new Issue(
                    IssueSeverity.CRITICAL,
                    IssueType.MISSING_PRIMARY_KEY,
                    entity.getTableName(),
                    null,
                    "Table has no primary key defined",
                    "Add @Id annotation to an entity field or define a primary key in the database"
            ));
        }

        return issues;
    }

    /**
     * Check for missing indexes on foreign key columns
     */
    private List<Issue> checkMissingForeignKeyIndexes(EntityMetadata entity, TableSchema schema) {
        List<Issue> issues = new ArrayList<>();

        for (RelationshipMetadata relationship : entity.getRelationships()) {
            // Only check relationships that have a join column in this table
            if (relationship.getJoinColumnName() == null) {
                continue;
            }

            // ManyToOne and OneToOne relationships should have indexes on the FK column
            if (relationship.getType() == RelationshipType.MANY_TO_ONE ||
                relationship.getType() == RelationshipType.ONE_TO_ONE) {

                String joinColumn = relationship.getJoinColumnName();

                if (!schema.hasIndexOnColumn(joinColumn)) {
                    IssueSeverity severity = relationship.getType() == RelationshipType.MANY_TO_ONE
                            ? IssueSeverity.HIGH
                            : IssueSeverity.MEDIUM;

                    issues.add(new Issue(
                            severity,
                            IssueType.MISSING_FK_INDEX,
                            entity.getTableName(),
                            joinColumn,
                            String.format("Foreign key column '%s' (referencing %s) has no index",
                                    joinColumn, relationship.getReferencedTableName()),
                            String.format("Add @Index annotation or create index: CREATE INDEX idx_%s_%s ON %s(%s)",
                                    entity.getTableName().toLowerCase(), joinColumn.toLowerCase(),
                                    entity.getTableName(), joinColumn)
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Check for missing indexes on foreign key columns
     */
    private List<Issue> checkMissingJoinTables(EntityMetadata entity, Map<String, TableSchema> schemas) {
        List<Issue> issues = new ArrayList<>();

        for (RelationshipMetadata relationship : entity.getRelationships()) {
            // Only check relationships that have a join column in this table
            String joinTableName = relationship.getJoinTableName();
            if (joinTableName == null) {
                continue;
            }
            TableSchema schema = schemas.get(joinTableName);

            if (schema == null) {
                // Table doesn't exist - this is a critical issue
                issues.add(new Issue(
                        IssueSeverity.CRITICAL,
                        IssueType.MISSING_PRIMARY_KEY,
                        joinTableName,
                        null,
                        "Join table does not exist in database",
                        "Ensure the database schema is generated or updated"
                ));
                continue;
            }
        }
        return issues;
    }

    /**
     * Check for missing indexes on unique columns
     */
    private List<Issue> checkMissingUniqueIndexes(EntityMetadata entity, TableSchema schema) {
        List<Issue> issues = new ArrayList<>();

        for (ColumnMetadata column : entity.getColumns()) {
            if (column.isUnique() && !column.isPrimaryKey()) {
                String columnName = column.getColumnName();

                if (!schema.hasIndexOnColumn(columnName)) {
                    issues.add(new Issue(
                            IssueSeverity.MEDIUM,
                            IssueType.MISSING_UNIQUE_INDEX,
                            entity.getTableName(),
                            columnName,
                            String.format("Unique column '%s' has no index", columnName),
                            String.format("Most databases automatically create indexes for unique constraints, " +
                                    "but verify with: CREATE UNIQUE INDEX idx_%s_%s ON %s(%s)",
                                    entity.getTableName().toLowerCase(), columnName.toLowerCase(),
                                    entity.getTableName(), columnName)
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Check for missing indexes on columns used in repository query methods
     */
    private List<Issue> checkRepositoryQueryIndexes(List<RepositoryMetadata> repositories,
                                                      List<EntityMetadata> entities,
                                                      Map<String, TableSchema> schemas) {
        List<Issue> issues = new ArrayList<>();

        for (RepositoryMetadata repository : repositories) {
            String tableName = repository.getEntityTableName().toUpperCase();
            TableSchema schema = schemas.get(tableName);

            if (schema == null) {
                continue; // Table doesn't exist, already reported
            }

            // Get the entity metadata for this repository's entity
            EntityMetadata entityMetadata = entities.stream()
                    .filter(e -> e.getEntityClass().equals(repository.getEntityClass()))
                    .findFirst()
                    .orElse(null);

            if (entityMetadata == null) {
                continue;
            }

            // Check each query method
            for (QueryMethodMetadata queryMethod : repository.getQueryMethods()) {
                issues.addAll(checkQueryMethodIndexes(queryMethod, repository, entityMetadata, schema));
            }
        }

        return issues;
    }

    /**
     * Check if a specific query method's columns are indexed
     */
    private List<Issue> checkQueryMethodIndexes(QueryMethodMetadata queryMethod,
                                                  RepositoryMetadata repository,
                                                  EntityMetadata entity,
                                                  TableSchema schema) {
        List<Issue> issues = new ArrayList<>();

        for (String fieldPath : queryMethod.getQueriedFields()) {
            // Convert field path to column name
            String columnName = fieldPathToColumnName(fieldPath, entity);

            if (columnName == null) {
                continue; // Could not resolve field to column
            }

            // Skip if it's a primary key, FK, or unique column (already checked elsewhere)
            if (isPrimaryKey(columnName, entity) ||
                isForeignKey(columnName, entity) ||
                isUniqueColumn(columnName, entity)) {
                continue;
            }

            // Check if the column has an index
            if (!schema.hasIndexOnColumn(columnName)) {
                issues.add(new Issue(
                        IssueSeverity.MEDIUM,
                        IssueType.MISSING_QUERY_INDEX,
                        entity.getTableName(),
                        columnName,
                        String.format("Repository method '%s.%s' queries column '%s' without an index",
                                repository.getRepositoryInterface().getSimpleName(),
                                queryMethod.getMethodName(),
                                columnName),
                        String.format("CREATE INDEX idx_%s_%s ON %s(%s)",
                                entity.getTableName().toLowerCase(),
                                columnName.toLowerCase(),
                                entity.getTableName(),
                                columnName)
                ));
            }
        }

        // If query uses multiple fields, suggest composite index
        if (queryMethod.getQueriedFields().size() > 1) {
            List<String> columnNames = queryMethod.getQueriedFields().stream()
                    .map(field -> fieldPathToColumnName(field, entity))
                    .filter(col -> col != null)
                    .toList();

            if (columnNames.size() > 1) {
                // Check if there's a composite index covering these columns
                boolean hasCompositeIndex = schema.getIndexes().stream()
                        .anyMatch(index -> index.getColumns().containsAll(columnNames));

                if (!hasCompositeIndex) {
                    issues.add(new Issue(
                            IssueSeverity.LOW,
                            IssueType.POTENTIAL_COMPOSITE_INDEX,
                            entity.getTableName(),
                            String.join(", ", columnNames),
                            String.format("Repository method '%s.%s' queries multiple columns that could benefit from a composite index",
                                    repository.getRepositoryInterface().getSimpleName(),
                                    queryMethod.getMethodName()),
                            String.format("Consider: CREATE INDEX idx_%s_%s ON %s(%s)",
                                    entity.getTableName().toLowerCase(),
                                    String.join("_", columnNames).toLowerCase(),
                                    entity.getTableName(),
                                    String.join(", ", columnNames))
                    ));
                }
            }
        }

        return issues;
    }

    /**
     * Convert a field path (e.g., "courseName" or "teacher.department") to column name
     */
    private String fieldPathToColumnName(String fieldPath, EntityMetadata entity) {
        // For now, handle simple field names only
        // TODO: Handle nested paths like "teacher.department.name"
        String[] parts = fieldPath.split("\\.");

        if (parts.length == 1) {
            // Simple field name
            String fieldName = parts[0];

            // Find the column in entity metadata
            for (ColumnMetadata column : entity.getColumns()) {
                if (column.getFieldName().equals(fieldName)) {
                    return column.getColumnName();
                }
            }

            // Check if it's a relationship field (use join column name)
            for (RelationshipMetadata relationship : entity.getRelationships()) {
                if (relationship.getFieldName().equals(fieldName)) {
                    return relationship.getJoinColumnName();
                }
            }
        }

        // For nested paths, return null for now
        // This would require traversing the entity graph
        return null;
    }

    private boolean isPrimaryKey(String columnName, EntityMetadata entity) {
        return entity.getColumns().stream()
                .anyMatch(col -> col.getColumnName().equals(columnName) && col.isPrimaryKey());
    }

    private boolean isForeignKey(String columnName, EntityMetadata entity) {
        return entity.getRelationships().stream()
                .anyMatch(rel -> columnName.equals(rel.getJoinColumnName()));
    }

    private boolean isUniqueColumn(String columnName, EntityMetadata entity) {
        return entity.getColumns().stream()
                .anyMatch(col -> col.getColumnName().equals(columnName) && col.isUnique());
    }
}
