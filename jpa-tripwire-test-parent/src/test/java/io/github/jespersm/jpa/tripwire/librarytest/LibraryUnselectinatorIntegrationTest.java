package io.github.jespersm.jpa.tripwire.librarytest;

import io.github.jespersm.jpa.tripwire.librarytest.entity.Loan;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazyLoadKind;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazySelectReport;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservationResult;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryUnselectinatorIntegrationTest extends AbstractLibraryTripwireTest {

    @Test
    @Transactional
    void testUnselectinatorTracksLibraryRepositoryTraversal() {
        ObservationResult<List<String>> observation = unselectinator.observe(() ->
                loanRepository.findAllByReturnedAtIsNull().stream()
                        .sorted(Comparator.comparing(Loan::getId))
                        .map(this::summarizeLoan)
                        .sorted()
                        .toList()
        );

        assertEquals(expectedLoanSummaries(), observation.value());

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(18L, report.countLazySelectsInitiatedBy(
                "io.github.jespersm.jpa.tripwire.librarytest.repository.LoanRepository#findAllByReturnedAtIsNull"
        ));
    }

    @Test
    @Transactional
    void testUnselectinatorTracksLibraryEntityManagerTraversal() {
        ObservationResult<List<String>> observation = unselectinator.observe(() ->
                entityManager.createQuery(
                                "select loan from Loan loan where loan.returnedAt is null order by loan.id",
                                Loan.class
                        )
                        .getResultList()
                        .stream()
                        .map(this::summarizeLoan)
                        .sorted()
                        .toList()
        );

        assertEquals(expectedLoanSummaries(), observation.value());

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(18L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    private static void assertExpectedLazyLoads(LazySelectReport report) {
        assertEquals(18, report.getLazySelectCount(),
                "Expected seven collection loads and eleven to-one entity loads");
        assertEquals(2L, report.countLazySelectsByRelation("loans"));
        assertEquals(2L, report.countLazySelectsByRelation("copies"));
        assertEquals(3L, report.countLazySelectsByRelation("authors"));
        assertEquals(2L, report.countLazySelectsByRelation("borrower"));
        assertEquals(2L, report.countLazySelectsByRelation("profile"));
        // The first branch.copies initialization also loads the second loan's
        // copy. Accessing that copy later does not perform a lazy entity load.
        assertEquals(2L, report.countLazySelectsByRelation("copy"));
        assertEquals(3L, report.countLazySelectsByRelation("book"));
        assertEquals(2L, report.countLazySelectsByRelation("branch"));
        assertEquals(11L, report.lazySelectEvents().stream()
                .filter(event -> event.kind() == LazyLoadKind.ENTITY_LOAD)
                .count());
    }

    private List<String> expectedLoanSummaries() {
        return List.of(
                "Ida Berg (Student) borrowed The Ministry for the Future from Harbor Branch [authors=1, borrowerLoans=1, branchCopies=2]",
                "Nora Holm (Resident) borrowed Parable of the Sower from Central Library [authors=1, borrowerLoans=2, branchCopies=3]",
                "Nora Holm (Resident) borrowed The Left Hand of Darkness from Central Library [authors=1, borrowerLoans=2, branchCopies=3]"
        );
    }

    private String summarizeLoan(Loan loan) {
        String borrowerName = loan.getBorrower().getFullName();
        String membershipLevel = loan.getBorrower().getProfile().getMembershipLevel();
        String title = loan.getCopy().getBook().getTitle();
        String branchName = loan.getCopy().getBranch().getName();
        int authorCount = loan.getCopy().getBook().getAuthors().size();
        int borrowerLoanCount = loan.getBorrower().getLoans().size();
        int branchCopyCount = loan.getCopy().getBranch().getCopies().size();

        return "%s (%s) borrowed %s from %s [authors=%d, borrowerLoans=%d, branchCopies=%d]".formatted(
                borrowerName,
                membershipLevel,
                title,
                branchName,
                authorCount,
                borrowerLoanCount,
                branchCopyCount
        );
    }
}
