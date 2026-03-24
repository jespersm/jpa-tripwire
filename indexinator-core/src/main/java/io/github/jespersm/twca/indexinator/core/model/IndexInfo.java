package io.github.jespersm.twca.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about a database index
 */
public class IndexInfo {
    private final String indexName;
    private final String tableName;
    private final List<String> columns;
    private final boolean isUnique;

    public IndexInfo(String indexName, String tableName, boolean isUnique) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.isUnique = isUnique;
        this.columns = new ArrayList<>();
    }

    public void addColumn(String columnName) {
        this.columns.add(columnName);
    }

    public String getIndexName() {
        return indexName;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return new ArrayList<>(columns);
    }

    public boolean isUnique() {
        return isUnique;
    }

    public boolean isComposite() {
        return columns.size() > 1;
    }
}
