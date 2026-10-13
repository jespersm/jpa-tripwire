package io.github.jespersm.jpa.tripwire.indexinator.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema information for a database table.
 */
public class TableSchema {
    private final String tableName;
    private final List<IndexInfo> indexes;
    private final List<ForeignKeyInfo> foreignKeys;
    private final List<String> primaryKeyColumns;

    /**
     * @param tableName table name
     */
    public TableSchema(String tableName) {
        this.tableName = tableName;
        this.indexes = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
        this.primaryKeyColumns = new ArrayList<>();
    }

    /** @param index index to register */
    public void addIndex(IndexInfo index) {
        this.indexes.add(index);
    }

    /** @param foreignKey foreign key to register */
    public void addForeignKey(ForeignKeyInfo foreignKey) {
        this.foreignKeys.add(foreignKey);
    }

    /** @param columnName primary key column name to register */
    public void addPrimaryKeyColumn(String columnName) {
        this.primaryKeyColumns.add(columnName);
    }

    /** @return table name */
    public String getTableName() {
        return tableName;
    }

    /** @return defensive copy of indexes */
    public List<IndexInfo> getIndexes() {
        return new ArrayList<>(indexes);
    }

    /** @return defensive copy of foreign keys */
    public List<ForeignKeyInfo> getForeignKeys() {
        return new ArrayList<>(foreignKeys);
    }

    /** @return defensive copy of primary key column names */
    public List<String> getPrimaryKeyColumns() {
        return new ArrayList<>(primaryKeyColumns);
    }

    /**
     * @param columnName column to look up
     * @return {@code true} when any index contains the provided column name
     */
    public boolean hasIndexOnColumn(String columnName) {
        return indexes.stream()
                .anyMatch(index -> index.getColumns().contains(columnName.toUpperCase())
                        || index.getColumns().contains(columnName.toLowerCase())
                        || index.getColumns().contains(columnName));
    }

    /**
     * @param columnName leading column to look up
     * @return {@code true} when an index can be used to look up the provided column directly
     */
    public boolean hasIndexStartingWithColumn(String columnName) {
        return indexes.stream()
                .map(IndexInfo::getColumns)
                .filter(columns -> !columns.isEmpty())
                .map(columns -> columns.get(0))
                .anyMatch(column -> column.equalsIgnoreCase(columnName));
    }
}
