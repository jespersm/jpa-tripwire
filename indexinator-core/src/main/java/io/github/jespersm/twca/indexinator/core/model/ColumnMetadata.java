package io.github.jespersm.twca.indexinator.core.model;

/**
 * Metadata about a column in a JPA entity
 */
public class ColumnMetadata {
    private final String fieldName;
    private final String columnName;
    private final boolean isPrimaryKey;
    private final boolean isUnique;
    private final boolean isNullable;
    private final Class<?> fieldType;

    public ColumnMetadata(String fieldName, String columnName, boolean isPrimaryKey,
                          boolean isUnique, boolean isNullable, Class<?> fieldType) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.isPrimaryKey = isPrimaryKey;
        this.isUnique = isUnique;
        this.isNullable = isNullable;
        this.fieldType = fieldType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public boolean isUnique() {
        return isUnique;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public Class<?> getFieldType() {
        return fieldType;
    }
}
