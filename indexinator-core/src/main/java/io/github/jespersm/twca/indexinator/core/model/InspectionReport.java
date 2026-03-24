package io.github.jespersm.twca.indexinator.core.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Report containing all issues found during database inspection
 */
public class InspectionReport {
    private final LocalDateTime timestamp;
    private final List<Issue> issues;
    private final int tablesInspected;
    private final int entitiesAnalyzed;

    public InspectionReport(List<Issue> issues, int tablesInspected, int entitiesAnalyzed) {
        this.timestamp = LocalDateTime.now();
        this.issues = new ArrayList<>(issues);
        this.tablesInspected = tablesInspected;
        this.entitiesAnalyzed = entitiesAnalyzed;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public List<Issue> getIssues() {
        return new ArrayList<>(issues);
    }

    public int getIssueCount() {
        return issues.size();
    }

    public int getTablesInspected() {
        return tablesInspected;
    }

    public int getEntitiesAnalyzed() {
        return entitiesAnalyzed;
    }

    public List<Issue> getIssuesBySeverity(IssueSeverity severity) {
        return issues.stream()
                .filter(issue -> issue.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    public Map<IssueSeverity, Long> getIssueCountBySeverity() {
        return issues.stream()
                .collect(Collectors.groupingBy(Issue::getSeverity, Collectors.counting()));
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean hasCriticalIssues() {
        return issues.stream().anyMatch(issue -> issue.getSeverity() == IssueSeverity.CRITICAL);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Indexinator Inspection Report ===\n");
        sb.append("Timestamp: ").append(timestamp).append("\n");
        sb.append("Tables Inspected: ").append(tablesInspected).append("\n");
        sb.append("Entities Analyzed: ").append(entitiesAnalyzed).append("\n");
        sb.append("Total Issues Found: ").append(issues.size()).append("\n\n");

        Map<IssueSeverity, Long> severityCounts = getIssueCountBySeverity();
        sb.append("Issues by Severity:\n");
        for (IssueSeverity severity : IssueSeverity.values()) {
            long count = severityCounts.getOrDefault(severity, 0L);
            if (count > 0) {
                sb.append("  ").append(severity).append(": ").append(count).append("\n");
            }
        }

        if (!issues.isEmpty()) {
            sb.append("\n=== Detailed Issues ===\n");
            for (Issue issue : issues) {
                sb.append(issue).append("\n");
            }
        }

        return sb.toString();
    }
}
