package io.github.jespersm.jpa.tripwire.indexinator.core;

import io.github.jespersm.jpa.tripwire.indexinator.core.analyzer.EntityAnalyzer;
import io.github.jespersm.jpa.tripwire.indexinator.core.detector.IssueDetector;
import io.github.jespersm.jpa.tripwire.indexinator.core.inspector.DatabaseSchemaInspector;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.*;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.IndexRequirement;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.MetamodelIndexRequirementCollector;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.RequirementIssueDetector;
import io.github.jespersm.jpa.tripwire.indexinator.core.repository.RepositoryQueryAnalyzer;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Primary facade for running Indexinator inspections.
 * <p>
 * This type coordinates entity/repository analysis, schema inspection, and issue detection,
 * and exposes convenient overloads for reflection-based and metamodel-first pipelines.
 */
public class Indexinator {

    private final EntityAnalyzer entityAnalyzer;
    private final RepositoryQueryAnalyzer repositoryAnalyzer;
    private final IssueDetector issueDetector;
    private final MetamodelIndexRequirementCollector requirementCollector;
    private final RequirementIssueDetector requirementIssueDetector;

    public Indexinator() {
        this.entityAnalyzer = new EntityAnalyzer();
        this.repositoryAnalyzer = new RepositoryQueryAnalyzer();
        this.issueDetector = new IssueDetector();
        this.requirementCollector = new MetamodelIndexRequirementCollector();
        this.requirementIssueDetector = new RequirementIssueDetector();
    }

    /**
     * Inspect JPA entities against a database connection
     *
     * @param connection JDBC connection to the database
     * @param entityClasses Collection of JPA entity classes to analyze
     * @return InspectionReport containing all detected issues
     * @throws SQLException if database inspection fails
     */
    public InspectionReport inspect(Connection connection, Collection<Class<?>> entityClasses) throws SQLException {
        // Analyze JPA entities
        List<EntityMetadata> entities = entityAnalyzer.analyzeEntities(entityClasses);

        // Inspect database schema
        DatabaseSchemaInspector schemaInspector = new DatabaseSchemaInspector(connection);
        Map<String, TableSchema> schemas = schemaInspector.inspectDatabase();

        // Detect issues
        List<Issue> issues = issueDetector.detectIssues(entities, schemas);

        return new InspectionReport(issues, schemas.size(), entities.size());
    }

    /**
     * Inspect specific JPA entity classes
     *
     * @param connection JDBC connection to the database
     * @param entityClasses varargs of JPA entity classes
     * @return InspectionReport containing all detected issues
     * @throws SQLException if database inspection fails
     */
    public InspectionReport inspect(Connection connection, Class<?>... entityClasses) throws SQLException {
        return inspect(connection, Arrays.asList(entityClasses));
    }

    /**
     * Inspect JPA entities and repositories against a database connection
     *
     * @param connection JDBC connection to the database
     * @param entityClasses Collection of JPA entity classes to analyze
     * @param repositoryClasses Collection of Spring Data repository interfaces to analyze
     * @return InspectionReport containing all detected issues
     * @throws SQLException if database inspection fails
     */
    public InspectionReport inspect(Connection connection,
                                     Collection<Class<?>> entityClasses,
                                     Collection<Class<?>> repositoryClasses) throws SQLException {
        // Analyze JPA entities
        List<EntityMetadata> entities = entityAnalyzer.analyzeEntities(entityClasses);

        // Analyze repositories
        List<RepositoryMetadata> repositories = new ArrayList<>();
        for (Class<?> repoClass : repositoryClasses) {
            RepositoryMetadata repoMetadata = repositoryAnalyzer.analyzeRepository(repoClass);
            if (repoMetadata != null) {
                repositories.add(repoMetadata);
            }
        }

        // Inspect database schema
        DatabaseSchemaInspector schemaInspector = new DatabaseSchemaInspector(connection);
        Map<String, TableSchema> schemas = schemaInspector.inspectDatabase();

        // Detect issues (including repository query methods)
        List<Issue> issues = issueDetector.detectIssues(entities, repositories, schemas);

        return new InspectionReport(issues, schemas.size(), entities.size());
    }

    /**
     * Inspect entities and repositories using metamodel-driven requirements plus legacy checks.
     *
     * @param connection JDBC connection used for schema inspection
     * @param entityManagerFactory JPA factory used to access the metamodel and mapping providers
     * @param entityClasses entity classes to analyze
     * @param repositoryClasses repository interfaces to analyze
     * @return combined report containing both legacy and requirement-driven findings
     * @throws SQLException if schema inspection fails
     */
    public InspectionReport inspect(Connection connection,
                                    EntityManagerFactory entityManagerFactory,
                                    Collection<Class<?>> entityClasses,
                                    Collection<Class<?>> repositoryClasses) throws SQLException {
        List<EntityMetadata> entities = entityAnalyzer.analyzeEntities(entityClasses);

        List<RepositoryMetadata> repositories = new ArrayList<>();
        for (Class<?> repoClass : repositoryClasses) {
            RepositoryMetadata repoMetadata = repositoryAnalyzer.analyzeRepository(repoClass);
            if (repoMetadata != null) {
                repositories.add(repoMetadata);
            }
        }

        DatabaseSchemaInspector schemaInspector = new DatabaseSchemaInspector(connection);
        Map<String, TableSchema> schemas = schemaInspector.inspectDatabase();

        // The legacy checks read table and column names from annotations, which misses naming strategies and
        // inheritance mappings. Keep only the join-table checks, which the requirement pipeline doesn't cover yet.
        List<Issue> legacyIssues = issueDetector.detectIssues(entities, schemas).stream()
                .filter(issue -> issue.type() == IssueType.MISSING_JOIN_TABLE ||
                        issue.type() == IssueType.MISSING_JOIN_TABLE_INDEX)
                .toList();

        Metamodel metamodel = entityManagerFactory.getMetamodel();
        List<Issue> tableIssues = requirementIssueDetector.detectTableIssues(metamodel, entityClasses, schemas,
                entityManagerFactory);
        List<IndexRequirement> requirements = requirementCollector.collect(metamodel, entityClasses, repositories);
        List<Issue> requirementIssues = requirementIssueDetector.detectIssues(requirements, schemas, entityManagerFactory);

        List<Issue> mergedIssues = mergeDistinctIssues(legacyIssues,
                Stream.concat(tableIssues.stream(), requirementIssues.stream()).toList());
        return new InspectionReport(mergedIssues, schemas.size(), entities.size());
    }

    /**
     * Varargs convenience overload for metamodel-driven inspections.
     *
     * @param connection JDBC connection used for schema inspection
     * @param entityManagerFactory JPA factory used to access the metamodel and mapping providers
     * @param entityClasses entity classes to analyze
     * @param repositoryClasses repository interfaces to analyze
     * @return combined report containing both legacy and requirement-driven findings
     * @throws SQLException if schema inspection fails
     */
    public InspectionReport inspect(Connection connection,
                                    EntityManagerFactory entityManagerFactory,
                                    Collection<Class<?>> entityClasses,
                                    Class<?>... repositoryClasses) throws SQLException {
        return inspect(connection, entityManagerFactory, entityClasses, Arrays.asList(repositoryClasses));
    }

    private List<Issue> mergeDistinctIssues(List<Issue> legacyIssues, List<Issue> requirementIssues) {
        Map<String, Issue> deduped = new LinkedHashMap<>();

        for (Issue issue : legacyIssues) {
            deduped.putIfAbsent(issueKey(issue), issue);
        }

        for (Issue issue : requirementIssues) {
            deduped.putIfAbsent(issueKey(issue), issue);
        }

        return new ArrayList<>(deduped.values());
    }

    private String issueKey(Issue issue) {
        return issue.type() + "|" + issue.tableName() + "|" + issue.columnName();
    }

    /**
     * Create a builder for configuring Indexinator with classpath scanning
     *
     * @return IndexinatorBuilder instance
     */
    public static IndexinatorBuilder builder() {
        return new IndexinatorBuilder();
    }
}
