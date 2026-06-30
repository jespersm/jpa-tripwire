package io.github.jespersm.jpa.tripwire.librarytest;

import io.github.jespersm.jpa.tripwire.indexinator.core.Indexinator;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.InspectionReport;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import io.github.jespersm.jpa.tripwire.librarytest.entity.Author;
import io.github.jespersm.jpa.tripwire.librarytest.entity.Book;
import io.github.jespersm.jpa.tripwire.librarytest.entity.BookCopy;
import io.github.jespersm.jpa.tripwire.librarytest.entity.Borrower;
import io.github.jespersm.jpa.tripwire.librarytest.entity.BorrowerProfile;
import io.github.jespersm.jpa.tripwire.librarytest.entity.LibraryBranch;
import io.github.jespersm.jpa.tripwire.librarytest.entity.Loan;
import io.github.jespersm.jpa.tripwire.librarytest.repository.BookCopyRepository;
import io.github.jespersm.jpa.tripwire.librarytest.repository.LoanRepository;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryIndexinatorIntegrationTest extends AbstractLibraryTripwireTest {

    private static final List<Class<?>> LIBRARY_ENTITY_CLASSES = List.of(
            LibraryBranch.class,
            Author.class,
            Book.class,
            BorrowerProfile.class,
            Borrower.class,
            BookCopy.class,
            Loan.class
    );

    @Test
    void testRepositoryQueriesStillWorkInLibraryPersistenceUnit() {
        List<BookCopy> centralOnLoanCopies = bookCopyRepository.findAllByStatusAndBranchName(
                "ON_LOAN",
                "Central Library"
        );

        assertEquals(2, centralOnLoanCopies.size());
        assertTrue(bookCopyRepository.existsByBarcode("COPY-0002"));
        assertEquals(2, loanRepository.findAllByBorrowerCardNumberAndReturnedAtIsNull("CARD-1001").size());
    }

    @Test
    void testIndexinatorDetectsLibraryModelIndexRequirements() throws Exception {
        Indexinator indexinator = new Indexinator();

        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = indexinator.inspect(
                    connection,
                    entityManagerFactory,
                    LIBRARY_ENTITY_CLASSES,
                    List.of(BookCopyRepository.class, LoanRepository.class)
            );

            System.out.println(report);

            assertNotNull(report, "Report should not be null");
            assertEquals(7, report.getEntitiesAnalyzed(), "Should analyze only library entities");

            assertIssue(report, IssueType.MISSING_FK_INDEX, "book_copies", "book_id");
            assertIssue(report, IssueType.MISSING_FK_INDEX, "book_copies", "branch_id");
            assertIssue(report, IssueType.MISSING_FK_INDEX, "loans", "copy_id");
            assertIssue(report, IssueType.MISSING_FK_INDEX, "loans", "borrower_id");
            assertIssue(report, IssueType.MISSING_FK_INDEX, "borrowers", "profile_id");

            assertIssue(report, IssueType.MISSING_UNIQUE_INDEX, "books", "isbn");
            assertIssue(report, IssueType.MISSING_UNIQUE_INDEX, "book_copies", "barcode");
            assertIssue(report, IssueType.MISSING_UNIQUE_INDEX, "borrowers", "card_number");

            assertIssue(report, IssueType.MISSING_DECLARED_INDEX, "book_copies", "branch_id, status");
            assertIssue(report, IssueType.MISSING_DECLARED_INDEX, "loans", "borrower_id, returned_at");

            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "book_copies", "status");
            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "book_copies", "branch_id");
            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "book_copies", "barcode");
            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "book_copies", "book_id");
            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "loans", "borrower_id");
            assertIssue(report, IssueType.MISSING_QUERY_INDEX, "loans", "returned_at");

            assertIssue(report, IssueType.POTENTIAL_COMPOSITE_INDEX, "book_copies", "status, branch_id");
            assertIssue(report, IssueType.POTENTIAL_COMPOSITE_INDEX, "loans", "borrower_id, returned_at");
        }
    }

    @Test
    void testBuilderPatternWithLibraryPackageScanning() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.jpa.tripwire.librarytest.entity")
                    .withScannedRepositories("io.github.jespersm.jpa.tripwire.librarytest.repository")
                    .build()
                    .inspect(connection);

            assertNotNull(report, "Report should not be null");
            assertTrue(report.hasIssues(), "Should detect missing indexes");
            assertEquals(7, report.getEntitiesAnalyzed(), "Should find only library entities via package scan");
        }
    }

    private void assertIssue(InspectionReport report, IssueType type, String tableName, String columnName) {
        boolean found = report.getIssues().stream()
                .anyMatch(issue -> issue.type() == type &&
                        issue.tableName().equalsIgnoreCase(tableName) &&
                        issue.columnName() != null &&
                        issue.columnName().equalsIgnoreCase(columnName));

        assertTrue(found, () -> "Should detect " + type + " on " + tableName + "." + columnName +
                ". Issues were: " + report.getIssues());
    }
}
