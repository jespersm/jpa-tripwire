package io.github.jespersm.jpa.tripwire.librarytest;

import io.github.jespersm.jpa.tripwire.indexinator.core.Indexinator;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.InspectionReport;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.Issue;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that Indexinator reports physical table and column names when given an EntityManagerFactory:
 * camelCase join column names, JOINED and SINGLE_TABLE inheritance, and lookups by primary key.
 */
class LibraryPhysicalNamingIndexinatorIntegrationTest extends AbstractLibraryTripwireTest {

    private static final String NAMING_FIXTURES = "io.github.jespersm.jpa.tripwire.librarytest.naming";

    @Test
    void testBuilderWithEntityManagerFactoryUsesPhysicalNames() throws Exception {
        InspectionReport report = inspect(true);

        // SINGLE_TABLE subclasses live in the root table.
        assertNone(report, issue -> issue.type() == IssueType.MISSING_PRIMARY_KEY);

        // @JoinColumn(name = "borrowerId") is borrower_id in the schema, and it is indexed.
        assertNone(report, issue -> issue.columnName() != null &&
                issue.columnName().toLowerCase().startsWith("borrower"));
        // Same for @JoinColumn(name = "loanId") in the SINGLE_TABLE hierarchy.
        assertNone(report, issue -> issue.tableName().equalsIgnoreCase("notices"));

        // JOINED: the subclass column is reported on the subclass table, not on the root table.
        assertIssue(report, IssueType.MISSING_FK_INDEX, "late_fees", "loan_id");
        assertNone(report, issue -> issue.tableName().equalsIgnoreCase("fees") &&
                "loan_id".equalsIgnoreCase(issue.columnName()));

        // findAllByPaidIsFalse needs an index on paid, but findByIdAndPaidIsFalse is covered by the primary key.
        assertIssue(report, IssueType.MISSING_QUERY_INDEX, "fees", "paid");
        assertNone(report, issue -> issue.type() == IssueType.POTENTIAL_COMPOSITE_INDEX &&
                issue.tableName().equalsIgnoreCase("fees"));

        // A query through a collection only needs the plain column; the join itself goes via book_authors.
        assertIssue(report, IssueType.MISSING_QUERY_INDEX, "books", "genre");
        assertNone(report, issue -> issue.columnName() != null &&
                issue.columnName().toLowerCase().contains("authors"));
    }

    @Test
    void testBuilderWithoutEntityManagerFactoryKeepsAnnotationNames() throws Exception {
        InspectionReport report = inspect(false);

        // Documents the reflection-only behaviour: names come straight from the annotations.
        assertIssue(report, IssueType.MISSING_FK_INDEX, "fees", "borrowerId");
        assertTrue(report.getIssues().stream().anyMatch(issue -> issue.type() == IssueType.MISSING_PRIMARY_KEY &&
                        issue.tableName().equalsIgnoreCase("overdue_notice")),
                () -> "Expected the SINGLE_TABLE subclass to be looked up as its own table. Issues were: " +
                        report.getIssues());
    }

    private InspectionReport inspect(boolean withEntityManagerFactory) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            var builder = Indexinator.builder()
                    .withScannedEntities(NAMING_FIXTURES)
                    .withScannedRepositories(NAMING_FIXTURES);
            if (withEntityManagerFactory) {
                builder.withEntityManagerFactory(entityManagerFactory);
            }
            return builder.build().inspect(connection);
        }
    }

    private void assertIssue(InspectionReport report, IssueType type, String tableName, String columnName) {
        assertTrue(report.getIssues().stream().anyMatch(issue -> issue.type() == type &&
                        issue.tableName().equalsIgnoreCase(tableName) &&
                        columnName.equalsIgnoreCase(issue.columnName())),
                () -> "Should detect " + type + " on " + tableName + "." + columnName +
                        ". Issues were: " + report.getIssues());
    }

    private void assertNone(InspectionReport report, Predicate<Issue> predicate) {
        assertTrue(report.getIssues().stream().noneMatch(predicate),
                () -> "Unexpected issues: " + report.getIssues().stream().filter(predicate).toList());
    }
}
