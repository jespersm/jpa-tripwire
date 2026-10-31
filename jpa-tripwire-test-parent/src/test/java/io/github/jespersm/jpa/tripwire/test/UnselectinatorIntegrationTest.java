package io.github.jespersm.jpa.tripwire.test;

import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass;
import io.github.jespersm.jpa.tripwire.test.entity.Student;
import io.github.jespersm.jpa.tripwire.test.repository.SchoolClassRepository;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazyLoadKind;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazySelectReport;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservationResult;
import io.github.jespersm.jpa.tripwire.unselectinator.core.TrackedLoadEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Indexinator using Spring Boot 3.5 + Testcontainers PostgreSQL
 */
class UnselectinatorIntegrationTest extends AbstractTripwireTest {
    @PersistenceContext
    private EntityManager persistenceContextEntityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional
    void testLazyLoadingProducesNPlusOneSelects() {
        SchoolClass schoolClass = schoolClassRepository.findByCourseCode("CS101")
                .orElseThrow(() -> new AssertionError("Expected to find seeded class CS101"));

        // This intentionally demonstrates a classic N+1 pattern:
        // 1 query loads the class, 1 query initializes schoolClass.students,
        // then 1 additional query per student initializes student.classes lazily.
        List<String> studentEnrollmentSummaries = schoolClass.getStudents().stream()
                .sorted(Comparator.comparing(Student::getId))
                .map(student -> "%s (advisor: %s) is enrolled in %d classes".formatted(
                        student.getFirstName(),
                        student.getAdvisor().getLastName(),
                        student.getClasses().size()))
                .sorted()
                .toList();

        assertEquals(
                List.of(
                        "Alice (advisor: Smith) is enrolled in 3 classes",
                        "Bob (advisor: Smith) is enrolled in 3 classes",
                        "Diana (advisor: Brown) is enrolled in 2 classes"
                ),
                studentEnrollmentSummaries
        );
    }

    @Test
    @Transactional
    void testUnselectinatorAttributesLazySelectsToRepositoryMethod() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            SchoolClass schoolClass = schoolClassRepository.findByCourseCode("CS101")
                    .orElseThrow(() -> new AssertionError("Expected to find seeded class CS101"));

            return schoolClass.getStudents().stream()
                    .sorted(Comparator.comparing(Student::getId))
                    .map(student -> "%s (advisor: %s) is enrolled in %d classes".formatted(
                            student.getFirstName(),
                            student.getAdvisor().getLastName(),
                            student.getClasses().size()))
                    .sorted()
                    .toList();
        });

        assertEquals(
                List.of(
                        "Alice (advisor: Smith) is enrolled in 3 classes",
                        "Bob (advisor: Smith) is enrolled in 3 classes",
                        "Diana (advisor: Brown) is enrolled in 2 classes"
                ),
                observation.value()
        );

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy(SchoolClassRepository.class.getName() + "#findByCourseCode"));
    }

    @Test
    @Transactional
    void testUnselectinatorTracksEntityManagerQueriesDirectly() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            SchoolClass schoolClass = entityManager.createQuery(
                            "select sc from SchoolClass sc where sc.courseCode = :courseCode",
                            SchoolClass.class
                    )
                    .setParameter("courseCode", "CS101")
                    .getSingleResult();

            return formatStudents(schoolClass);
        });

        assertExpectedStudents(observation);

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    @Test
    @Transactional
    void testUnselectinatorTracksEntityManagerQueriesIndirectly() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            SchoolClass schoolClass = schoolClassService.loadByQuery("CS101");
            assertTrue(persistenceContextEntityManager.contains(schoolClass),
                    "Service injection and PersistenceContext must share the transaction's EntityManager");

            return formatStudents(schoolClass);
        });

        assertExpectedStudents(observation);

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    @Test
    @Transactional
    void testUnselectinatorTracksPersistenceContextQueries() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            SchoolClass schoolClass = persistenceContextEntityManager.createQuery(
                            "select sc from SchoolClass sc where sc.courseCode = :courseCode",
                            SchoolClass.class)
                    .setParameter("courseCode", "CS101")
                    .getSingleResult();
            return formatStudents(schoolClass);
        });

        assertExpectedStudents(observation);
        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
        assertTrue(report.loadEvents().stream()
                .anyMatch(TrackedLoadEvent::explicitFetchLoad), "The initial query must be an explicit fetch");
    }

    @Test
    void testUnselectinatorTracksFactoryCreatedEntityManager() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            try (EntityManager applicationEntityManager = entityManagerFactory.createEntityManager()) {
                SchoolClass schoolClass = applicationEntityManager.createQuery(
                                "select sc from SchoolClass sc where sc.courseCode = :courseCode",
                                SchoolClass.class)
                        .setParameter("courseCode", "CS101")
                        .getSingleResult();
                return formatStudents(schoolClass);
            }
        });

        assertExpectedStudents(observation);
        assertExpectedLazyLoads(observation.report());
        assertEquals(4L, observation.report()
                .countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    @Test
    @Transactional
    void testObservedQueryPreservesExceptionsAndClosesFetchScope() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {
            assertThrows(NoResultException.class, () -> schoolClassService.loadByQuery("missing"));
            return formatStudents(schoolClassService.loadByQuery("CS101"));
        });

        assertExpectedStudents(observation);
        assertExpectedLazyLoads(observation.report());
    }

    @Test
    @Transactional
    void testUnselectinatorTracksEntityManagerCriteriaBuilderQueries() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {

            SchoolClass schoolClass = schoolClassService.loadByCriteriaBuilder("CS101");

            return formatStudents(schoolClass);
        });

        assertExpectedStudents(observation);

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    @Test
    @Transactional
    void testUnselectinatorTracksEntityManagerCriteriaBuilderQueriesAlso() {
        ObservationResult<List<String>> observation = unselectinator.observe(() -> {

            SchoolClass schoolClass = schoolClassService.loadByCriteriaBuilder("CS101");

            return formatStudents(schoolClass);
        });

        assertExpectedStudents(observation);

        LazySelectReport report = observation.report();
        assertExpectedLazyLoads(report);
        assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
    }

    private static void assertExpectedLazyLoads(LazySelectReport report) {
        // Traverse students by id so earlier classes initialize later advisors.
        // SchoolClass.teacher is eager. Teachers loaded while initializing classes
        // must not be reported again as lazy Student.advisor loads.
        assertEquals(4, report.getLazySelectCount(),
                "Expected one students collection load and three classes collection loads");
        assertEquals(1L, report.countLazySelectsByRelation("students"));
        assertEquals(3L, report.countLazySelectsByRelation("classes"));
        assertEquals(0L, report.lazySelectEvents().stream()
                .filter(event -> event.kind() == LazyLoadKind.ENTITY_LOAD)
                .count());
    }

    private static void assertExpectedStudents(ObservationResult<List<String>> observation) {
        assertEquals(
                List.of(
                        "Alice is enrolled in 3 classes",
                        "Bob is enrolled in 3 classes",
                        "Diana is enrolled in 2 classes"
                ),
                observation.value()
        );
    }

    private static @NotNull List<String> formatStudents(SchoolClass schoolClass) {
        return schoolClass.getStudents().stream()
                .sorted(Comparator.comparing(Student::getId))
                .map(student -> student.getFirstName() + " is enrolled in " + student.getClasses().size() + " classes")
                .sorted()
                .toList();
    }
}
