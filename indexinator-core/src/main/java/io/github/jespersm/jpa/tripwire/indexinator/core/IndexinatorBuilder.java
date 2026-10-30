package io.github.jespersm.jpa.tripwire.indexinator.core;

import io.github.jespersm.jpa.tripwire.indexinator.core.model.InspectionReport;
import io.github.jespersm.jpa.tripwire.indexinator.core.scanner.EntityScanner;
import io.github.jespersm.jpa.tripwire.indexinator.core.scanner.RepositoryScanner;
import jakarta.persistence.EntityManagerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Fluent builder for creating a configured Indexinator inspection run.
 * <p>
 * The builder supports explicit class registration and package scanning for both
 * entities and repositories.
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
    private EntityManagerFactory entityManagerFactory;

    /**
     * Add explicit entity classes to analyze.
     *
     * @param entities entity classes to include
     * @return this builder
     */
    public IndexinatorBuilder withEntities(Class<?>... entities) {
        this.entityClasses.addAll(Arrays.asList(entities));
        return this;
    }

    /**
     * Add explicit entity classes to analyze.
     *
     * @param entities entity classes to include
     * @return this builder
     */
    public IndexinatorBuilder withEntities(Collection<Class<?>> entities) {
        this.entityClasses.addAll(entities);
        return this;
    }

    /**
     * Enable entity scanning for the provided base packages.
     *
     * @param basePackages package names to scan
     * @return this builder
     */
    public IndexinatorBuilder withScannedEntities(String... basePackages) {
        this.scanEntities = true;
        this.entityPackages = basePackages;
        return this;
    }

    /**
     * Exclude entity classes from the final inspection set.
     *
     * @param entities entity classes to exclude
     * @return this builder
     */
    public IndexinatorBuilder exceptEntity(Class<?>... entities) {
        this.excludedEntities.addAll(Arrays.asList(entities));
        return this;
    }

    /**
     * Add explicit repository interfaces to analyze.
     *
     * @param repositories repository interfaces to include
     * @return this builder
     */
    public IndexinatorBuilder withRepositories(Class<?>... repositories) {
        this.repositoryClasses.addAll(Arrays.asList(repositories));
        return this;
    }

    /**
     * Add explicit repository interfaces to analyze.
     *
     * @param repositories repository interfaces to include
     * @return this builder
     */
    public IndexinatorBuilder withRepositories(Collection<Class<?>> repositories) {
        this.repositoryClasses.addAll(repositories);
        return this;
    }

    /**
     * Enable repository scanning for the provided base packages.
     *
     * @param basePackages package names to scan
     * @return this builder
     */
    public IndexinatorBuilder withScannedRepositories(String... basePackages) {
        this.scanRepositories = true;
        this.repositoryPackages = basePackages;
        return this;
    }

    /**
     * Exclude repository interfaces from the final inspection set.
     *
     * @param repositories repository interfaces to exclude
     * @return this builder
     */
    public IndexinatorBuilder exceptRepository(Class<?>... repositories) {
        this.excludedRepositories.addAll(Arrays.asList(repositories));
        return this;
    }

    /**
     * Use the runtime {@link EntityManagerFactory} to resolve physical table and column names.
     * <p>
     * With a factory, requirements are resolved through the JPA provider's mapping metadata
     * (for example Hibernate), so naming strategies and inheritance mappings are taken into account.
     *
     * @param entityManagerFactory runtime entity manager factory
     * @return this builder
     */
    public IndexinatorBuilder withEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        return this;
    }

    /**
     * Materialize the configured inspection input.
     *
     * @return configured inspection runner
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

        return new ConfiguredIndexinator(finalEntities, finalRepositories, entityManagerFactory);
    }

    /**
     * Immutable inspection input prepared by {@link IndexinatorBuilder}.
     */
    public static class ConfiguredIndexinator {
        private final Set<Class<?>> entities;
        private final Set<Class<?>> repositories;
        private final EntityManagerFactory entityManagerFactory;
        private final Indexinator indexinator;

        ConfiguredIndexinator(Set<Class<?>> entities, Set<Class<?>> repositories,
                              EntityManagerFactory entityManagerFactory) {
            this.entities = entities;
            this.repositories = repositories;
            this.entityManagerFactory = entityManagerFactory;
            this.indexinator = new Indexinator();
        }

        /**
         * Run an inspection against the configured entities and repositories.
         *
         * @param connection JDBC connection used for schema inspection
         * @return inspection report
         * @throws SQLException if schema inspection fails
         */
        public InspectionReport inspect(Connection connection) throws SQLException {
            if (entityManagerFactory != null) {
                return indexinator.inspect(connection, entityManagerFactory, entities, repositories);
            }
            if (repositories.isEmpty()) {
                return indexinator.inspect(connection, entities);
            } else {
                return indexinator.inspect(connection, entities, repositories);
            }
        }

        /**
         * @return immutable view of configured entity classes
         */
        public Set<Class<?>> getEntities() {
            return Collections.unmodifiableSet(entities);
        }

        /**
         * @return immutable view of configured repository interfaces
         */
        public Set<Class<?>> getRepositories() {
            return Collections.unmodifiableSet(repositories);
        }
    }
}
