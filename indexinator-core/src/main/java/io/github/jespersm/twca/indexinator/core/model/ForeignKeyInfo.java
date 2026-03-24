package io.github.jespersm.twca.indexinator.core.model;

/**
 * Information about a foreign key constraint
 */
public class ForeignKeyInfo {
    private final String constraintName;
    private final String tableName;
    private final String columnName;
    private final String referencedTableName;
    private final String referencedColumnName;

    public ForeignKeyInfo(String constraintName, String tableName, String columnName,
                          String referencedTableName, String referencedColumnName) {
        this.constraintName = constraintName;
        this.tableName = tableName;
        this.columnName = columnName;
        this.referencedTableName = referencedTableName;
        this.referencedColumnName = referencedColumnName;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public String getReferencedColumnName() {
        return referencedColumnName;
    }
}
