package io.github.jespersm.twca.indexinator.core.repository;

import io.github.jespersm.twca.indexinator.core.model.QueryMethodMetadata;
import io.github.jespersm.twca.indexinator.core.model.QueryMethodMetadata.QueryType;
import io.github.jespersm.twca.indexinator.core.model.RepositoryMetadata;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

// Reflective workaround: Part.getProperty() return type changed between Spring Data 3.x and 4.x;
// we resolve the methods once at class-load time and call them reflectively.

/**
 * Analyzes Spring Data JPA repositories to extract query method information
 * using Spring's PartTree parser
 */
public class RepositoryQueryAnalyzer {

    // Reflective handle for Part.getProperty() – return type changed between SD 3.x and 4.x
    private static final java.lang.reflect.Method GET_PROPERTY_METHOD;
    // Reflective handle for the toDotPath() method on whatever getProperty() returns
    private static final java.lang.reflect.Method TO_DOT_PATH_METHOD;

    static {
        java.lang.reflect.Method getProperty = null;
        java.lang.reflect.Method toDotPath = null;
        try {
            getProperty = Part.class.getMethod("getProperty");
            toDotPath = getProperty.getReturnType().getMethod("toDotPath");
        } catch (Exception e) {
            // Will surface at call time
        }
        GET_PROPERTY_METHOD = getProperty;
        TO_DOT_PATH_METHOD = toDotPath;
    }

    /**
     * Version-safe equivalent of {@code part.getProperty().toDotPath()}.
     */
    private static String toDotPath(Part part) {
        try {
            Object property = GET_PROPERTY_METHOD.invoke(part);
            return (String) TO_DOT_PATH_METHOD.invoke(property);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot call Part.getProperty().toDotPath() – Spring Data API mismatch?", e);
        }
    }

    /**
     * Analyze a repository interface to extract all query methods
     */
    public RepositoryMetadata analyzeRepository(Class<?> repositoryInterface) {
        if (!isRepository(repositoryInterface)) {
            return null;
        }

        // Extract entity class from generic type
        Class<?> entityClass = getEntityClass(repositoryInterface);
        if (entityClass == null) {
            return null;
        }

        // Get table name from entity
        String tableName = getTableName(entityClass);

        RepositoryMetadata metadata = new RepositoryMetadata(
                repositoryInterface,
                entityClass,
                tableName
        );

        // Analyze all methods
        for (Method method : repositoryInterface.getMethods()) {
            QueryMethodMetadata queryMethod = parseMethodName(method, entityClass);
            if (queryMethod != null) {
                metadata.addQueryMethod(queryMethod);
            }
        }

        return metadata;
    }

    /**
     * Parse a query method using Spring's PartTree
     */
    public QueryMethodMetadata parseMethodName(Method method, Class<?> entityClass) {
        String methodName = method.getName();

        if (!isQueryMethod(methodName)) {
            return null;
        }

        try {
            // Use Spring's PartTree to parse the method name!
            PartTree partTree = new PartTree(methodName, entityClass);

            List<String> queriedFields = new ArrayList<>();

            // Extract all properties from the PartTree
            for (PartTree.OrPart orPart : partTree) {
                for (Part part : orPart) {
                    // part.getProperty() gives us the property path (reflective – return type changed in SD 4)
                    String propertyPath = toDotPath(part);
                    queriedFields.add(propertyPath);
                }
            }

            // Determine query type
            QueryType queryType = determineQueryType(partTree);

            return new QueryMethodMetadata(methodName, queriedFields, queryType);

        } catch (Exception e) {
            // If PartTree can't parse it, it's probably:
            // - A custom @Query method
            // - A method from CrudRepository/JpaRepository
            // - An invalid method name
            return null;
        }
    }

    /**
     * Check if a method name looks like a query method
     */
    private boolean isQueryMethod(String methodName) {
        return methodName.startsWith("findBy") ||
               methodName.startsWith("findAllBy") ||
               methodName.startsWith("getBy") ||
               methodName.startsWith("queryBy") ||
               methodName.startsWith("readBy") ||
               methodName.startsWith("streamBy") ||
               methodName.startsWith("countBy") ||
               methodName.startsWith("existsBy") ||
               methodName.startsWith("deleteBy") ||
               methodName.startsWith("removeBy");
    }

    /**
     * Determine the query type from PartTree
     */
    private QueryType determineQueryType(PartTree partTree) {
        if (partTree.isCountProjection()) {
            return QueryType.COUNT;
        } else if (partTree.isExistsProjection()) {
            return QueryType.EXISTS;
        } else if (partTree.isDelete()) {
            return QueryType.DELETE;
        } else {
            return QueryType.FIND;
        }
    }

    /**
     * Check if a class is a Spring Data repository
     */
    private boolean isRepository(Class<?> clazz) {
        if (!clazz.isInterface()) {
            return false;
        }

        // Check if it extends Repository or its subinterfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            String name = iface.getName();
            if (name.equals("org.springframework.data.repository.Repository") ||
                name.equals("org.springframework.data.repository.CrudRepository") ||
                name.equals("org.springframework.data.jpa.repository.JpaRepository") ||
                name.equals("org.springframework.data.repository.PagingAndSortingRepository")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract entity class from repository generic type
     */
    private Class<?> getEntityClass(Class<?> repositoryInterface) {
        // Look through all generic interfaces to find Repository<Entity, ID>
        for (Type genericInterface : repositoryInterface.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();

                if (rawType instanceof Class<?> rawClass) {
                    String name = rawClass.getName();

                    // Check if this is a repository interface
                    if (name.contains("Repository")) {
                        Type[] typeArguments = parameterizedType.getActualTypeArguments();
                        if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                            return (Class<?>) typeArguments[0];
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get table name from entity class
     */
    private String getTableName(Class<?> entityClass) {
        if (!entityClass.isAnnotationPresent(Entity.class)) {
            return null;
        }

        // Check for @Table annotation
        if (entityClass.isAnnotationPresent(Table.class)) {
            Table table = entityClass.getAnnotation(Table.class);
            if (!table.name().isEmpty()) {
                return table.name();
            }
        }

        // Default: convert CamelCase to snake_case
        return camelCaseToUnderscore(entityClass.getSimpleName());
    }

    private String camelCaseToUnderscore(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
