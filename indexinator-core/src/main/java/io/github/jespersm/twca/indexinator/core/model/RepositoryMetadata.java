package io.github.jespersm.twca.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata extracted from a Spring Data repository interface
 */
public class RepositoryMetadata {
    private final Class<?> repositoryInterface;
    private final Class<?> entityClass;
    private final String entityTableName;
    private final List<QueryMethodMetadata> queryMethods;

    public RepositoryMetadata(Class<?> repositoryInterface, Class<?> entityClass, String entityTableName) {
        this.repositoryInterface = repositoryInterface;
        this.entityClass = entityClass;
        this.entityTableName = entityTableName;
        this.queryMethods = new ArrayList<>();
    }

    public void addQueryMethod(QueryMethodMetadata queryMethod) {
        this.queryMethods.add(queryMethod);
    }

    public Class<?> getRepositoryInterface() {
        return repositoryInterface;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getEntityTableName() {
        return entityTableName;
    }

    public List<QueryMethodMetadata> getQueryMethods() {
        return new ArrayList<>(queryMethods);
    }

    @Override
    public String toString() {
        return String.format("Repository: %s (entity: %s, methods: %d)",
                repositoryInterface.getSimpleName(),
                entityClass.getSimpleName(),
                queryMethods.size());
    }
}
