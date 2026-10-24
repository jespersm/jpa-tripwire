package io.github.jespersm.jpa.tripwire.test;

import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass;
import io.github.jespersm.jpa.tripwire.test.entity.Student;
import io.github.jespersm.jpa.tripwire.test.repository.StudentRepository;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.CollectionLoadGuard;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.EnableUnselectinatorAlwaysOn;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Shared, Docker-free demo executed unchanged by the Spring Boot 3.5 and 4 modules. */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:always_on;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("test")
@EnableUnselectinatorAlwaysOn(ratio = 0.5, minimumGroupSize = 3, minimumLazyLoads = 2,
        includes = Student.class)
@Transactional
class AlwaysOnUnselectinatorDemoTest {
    @Autowired
    StudentRepository students;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void repositoryResultsAreGuardedWithoutAnObservationBlock() {
        var result = students.findAll();
        assertEquals(5, result.size());
        result.get(0).getClasses().size();
        result.get(1).getClasses().size();
        var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                () -> result.get(2).getClasses().size());
        assertEquals("classes", failure.violation().association());
        assertEquals(5, failure.violation().groupSize());
        assertEquals(3, failure.violation().lazyLoadCount());
        assertTrue(failure.violation().origins().stream().anyMatch(origin ->
                origin.endpoint().methodName().equals("findAll")));
    }

    @Test
    void aSingleCollectionLoadEstablishesTheGuardedGroup() {
        var schoolClass = entityManager.find(SchoolClass.class, 1L);
        var result = schoolClass.getStudents().stream().toList();
        assertEquals(3, result.size());
        result.get(0).getClasses().size();
        var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                () -> result.get(1).getClasses().size());
        assertEquals("classes", failure.violation().association());
        assertEquals(3, failure.violation().groupSize());
        assertEquals(2, failure.violation().lazyLoadCount());
    }

    @Test
    void fetchJoinAvoidsTheLazySelects() {
        var result = entityManager.createQuery(
                "select distinct s from Student s left join fetch s.classes", Student.class).getResultList();
        assertEquals(5, result.size());
        assertDoesNotThrow(() -> result.forEach(student -> student.getClasses().size()));
    }
}
