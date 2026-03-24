package io.github.jespersm.twca.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema information for a database table
 */
public class TableSchema {
    private final String tableName;
    private final List<IndexInfo> indexes;
    private final List<ForeignKeyInfo> foreignKeys;
    private final List<String> primaryKeyColumns;

    public TableSchema(String tableName) {
        this.tableName = tableName;
        this.indexes = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
        this.primaryKeyColumns = new ArrayList<>();
    }

    public void addIndex(IndexInfo index) {
        this.indexes.add(index);
    }

    public void addForeignKey(ForeignKeyInfo foreignKey) {
        this.foreignKeys.add(foreignKey);
    }

    public void addPrimaryKeyColumn(String columnName) {
        this.primaryKeyColumns.add(columnName);
    }

    public String getTableName() {
        return tableName;
    }

    public List<IndexInfo> getIndexes() {
        return new ArrayList<>(indexes);
    }

    public List<ForeignKeyInfo> getForeignKeys() {
        return new ArrayList<>(foreignKeys);
    }

    public List<String> getPrimaryKeyColumns() {
        return new ArrayList<>(primaryKeyColumns);
    }

    public boolean hasIndexOnColumn(String columnName) {
        return indexes.stream()
                .anyMatch(index -> index.getColumns().contains(columnName.toUpperCase())
                        || index.getColumns().contains(columnName.toLowerCase())
                        || index.getColumns().contains(columnName));
    }
}
