package io.github.jespersm.twca.indexinator.core;

import io.github.jespersm.twca.indexinator.core.model.InspectionReport;
import io.github.jespersm.twca.indexinator.core.scanner.EntityScanner;
import io.github.jespersm.twca.indexinator.core.scanner.RepositoryScanner;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Builder for Indexinator with support for classpath scanning
 */
public class IndexinatorBuilder {

    private final Set<Class<?>> entityClasses = new HashSet<>();
    private final Set<Class<?>> repositoryClasses = new HashSet<>();
    private final Set<Class<?>> excludedEntities = new HashSet<>();
    private final Set<Class<?>> excludedRepositories = new HashSet<>();

    private final EntityScanner entityScanner = new EntityScanner();
    private final RepositoryScanner repositoryScanner = new RepositoryScanner();

    private boolean scanEntities = false;
    private String[] entityPackages = new String[0];
    private boolean scanRepositories = false;
    private String[] repositoryPackages = new String[0];

    /**
     * Add specific entity classes to analyze
     */
    public IndexinatorBuilder withEntities(Class<?>... entities) {
        this.entityClasses.addAll(Arrays.asList(entities));
        return this;
    }

    /**
     * Add specific entity classes to analyze
     */
    public IndexinatorBuilder withEntities(Collection<Class<?>> entities) {
        this.entityClasses.addAll(entities);
        return this;
    }

    /**
     * Scan classpath for entity classes in specified packages
     */
    public IndexinatorBuilder withScannedEntities(String... basePackages) {
        this.scanEntities = true;
        this.entityPackages = basePackages;
        return this;
    }

    /**
     * Exclude specific entity classes from analysis
     */
    public IndexinatorBuilder exceptEntity(Class<?>... entities) {
        this.excludedEntities.addAll(Arrays.asList(entities));
        return this;
    }

    /**
     * Add specific repository classes to analyze
     */
    public IndexinatorBuilder withRepositories(Class<?>... repositories) {
        this.repositoryClasses.addAll(Arrays.asList(repositories));
        return this;
    }

    /**
     * Add specific repository classes to analyze
     */
    public IndexinatorBuilder withRepositories(Collection<Class<?>> repositories) {
        this.repositoryClasses.addAll(repositories);
        return this;
    }

    /**
     * Scan classpath for repository interfaces in specified packages
     */
    public IndexinatorBuilder withScannedRepositories(String... basePackages) {
        this.scanRepositories = true;
        this.repositoryPackages = basePackages;
        return this;
    }

    /**
     * Exclude specific repository interfaces from analysis
     */
    public IndexinatorBuilder exceptRepository(Class<?>... repositories) {
        this.excludedRepositories.addAll(Arrays.asList(repositories));
        return this;
    }

    /**
     * Build the configured Indexinator instance
     */
    public ConfiguredIndexinator build() {
        Set<Class<?>> finalEntities = new HashSet<>(entityClasses);
        Set<Class<?>> finalRepositories = new HashSet<>(repositoryClasses);

        // Scan for entities if requested
        if (scanEntities) {
            if (entityPackages.length == 0) {
                throw new IllegalStateException(
                        "withScannedEntities() called but no packages specified. " +
                        "Use withScannedEntities(\"com.myapp.entity\") instead."
                );
            }
            finalEntities.addAll(entityScanner.scanForEntities(entityPackages));
        }

        // Scan for repositories if requested
        if (scanRepositories) {
            if (repositoryPackages.length == 0) {
                throw new IllegalStateException(
                        "withScannedRepositories() called but no packages specified. " +
                        "Use withScannedRepositories(\"com.myapp.repository\") instead."
                );
            }
            finalRepositories.addAll(repositoryScanner.scanForRepositories(repositoryPackages));
        }

        // Apply exclusions
        finalEntities.removeAll(excludedEntities);
        finalRepositories.removeAll(excludedRepositories);

        if (finalEntities.isEmpty()) {
            throw new IllegalStateException(
                    "No entities configured. Use withEntities() or withScannedEntities() to add entity classes."
            );
        }

        return new ConfiguredIndexinator(finalEntities, finalRepositories);
    }

    /**
     * Configured Indexinator instance ready for inspection
     */
    public static class ConfiguredIndexinator {
        private final Set<Class<?>> entities;
        private final Set<Class<?>> repositories;
        private final Indexinator indexinator;

        ConfiguredIndexinator(Set<Class<?>> entities, Set<Class<?>> repositories) {
            this.entities = entities;
            this.repositories = repositories;
            this.indexinator = new Indexinator();
        }

        /**
         * Inspect the database using the configured entities and repositories
         */
        public InspectionReport inspect(Connection connection) throws SQLException {
            if (repositories.isEmpty()) {
                return indexinator.inspect(connection, entities);
            } else {
                return indexinator.inspect(connection, entities, repositories);
            }
        }

        /**
         * Get the configured entities
         */
        public Set<Class<?>> getEntities() {
            return Collections.unmodifiableSet(entities);
        }

        /**
         * Get the configured repositories
         */
        public Set<Class<?>> getRepositories() {
            return Collections.unmodifiableSet(repositories);
        }
    }
}
