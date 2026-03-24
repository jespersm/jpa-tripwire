package io.github.jespersm.twca.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about a repository query method
 */
public class QueryMethodMetadata {
    private final String methodName;
    private final List<String> queriedFields;
    private final QueryType queryType;

    public QueryMethodMetadata(String methodName, List<String> queriedFields, QueryType queryType) {
        this.methodName = methodName;
        this.queriedFields = new ArrayList<>(queriedFields);
        this.queryType = queryType;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getQueriedFields() {
        return new ArrayList<>(queriedFields);
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public enum QueryType {
        FIND,
        COUNT,
        DELETE,
        EXISTS,
        CUSTOM
    }

    @Override
    public String toString() {
        return String.format("%s (queries: %s)", methodName, queriedFields);
    }
}
