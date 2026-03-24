package io.github.jespersm.twca.indexinator.core.model;

/**
 * Represents a database issue detected by Indexinator
 */
public class Issue {
    private final IssueSeverity severity;
    private final IssueType type;
    private final String tableName;
    private final String columnName;
    private final String description;
    private final String recommendation;

    public Issue(IssueSeverity severity, IssueType type, String tableName,
                 String columnName, String description, String recommendation) {
        this.severity = severity;
        this.type = type;
        this.tableName = tableName;
        this.columnName = columnName;
        this.description = description;
        this.recommendation = recommendation;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public IssueType getType() {
        return type;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getDescription() {
        return description;
    }

    public String getRecommendation() {
        return recommendation;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s.%s: %s (Recommendation: %s)",
                severity, type, tableName, columnName != null ? columnName : "N/A",
                description, recommendation);
    }
}
