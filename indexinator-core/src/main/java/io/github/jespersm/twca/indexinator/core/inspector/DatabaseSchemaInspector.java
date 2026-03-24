package io.github.jespersm.twca.indexinator.core.inspector;

import io.github.jespersm.twca.indexinator.core.model.ForeignKeyInfo;
import io.github.jespersm.twca.indexinator.core.model.IndexInfo;
import io.github.jespersm.twca.indexinator.core.model.TableSchema;

import java.sql.*;
import java.util.*;

/**
 * Inspects database schema using JDBC metadata
 */
public class DatabaseSchemaInspector {

    private final Connection connection;

    public DatabaseSchemaInspector(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inspect all tables in the database
     */
    public Map<String, TableSchema> inspectDatabase() throws SQLException {
        Map<String, TableSchema> tables = new HashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();

        // Get all tables
        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                TableSchema schema = inspectTable(tableName);
                tables.put(tableName.toUpperCase(), schema);
            }
        }

        return tables;
    }

    /**
     * Inspect a specific table
     */
    public TableSchema inspectTable(String tableName) throws SQLException {
        TableSchema schema = new TableSchema(tableName);
        DatabaseMetaData metaData = connection.getMetaData();

        // Get primary keys
        try (ResultSet rs = metaData.getPrimaryKeys(null, null, tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                schema.addPrimaryKeyColumn(columnName);
            }
        }

        // Get indexes
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
            Map<String, IndexInfo> indexMap = new HashMap<>();

            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue;

                boolean isUnique = !rs.getBoolean("NON_UNIQUE");
                String columnName = rs.getString("COLUMN_NAME");

                IndexInfo index = indexMap.computeIfAbsent(
                        indexName,
                        name -> new IndexInfo(name, tableName, isUnique)
                );
                index.addColumn(columnName);
            }

            indexMap.values().forEach(schema::addIndex);
        }

        // Get foreign keys
        try (ResultSet rs = metaData.getImportedKeys(null, null, tableName)) {
            while (rs.next()) {
                String constraintName = rs.getString("FK_NAME");
                String columnName = rs.getString("FKCOLUMN_NAME");
                String referencedTable = rs.getString("PKTABLE_NAME");
                String referencedColumn = rs.getString("PKCOLUMN_NAME");

                ForeignKeyInfo fk = new ForeignKeyInfo(
                        constraintName, tableName, columnName, referencedTable, referencedColumn
                );
                schema.addForeignKey(fk);
            }
        }

        return schema;
    }

    /**
     * Get list of all table names in the database
     */
    public List<String> getTableNames() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        }

        return tableNames;
    }
}
