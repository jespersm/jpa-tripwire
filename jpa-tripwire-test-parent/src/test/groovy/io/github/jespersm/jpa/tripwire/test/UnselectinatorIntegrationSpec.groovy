package io.github.jespersm.jpa.tripwire.test

import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass
import io.github.jespersm.jpa.tripwire.test.repository.SchoolClassRepository
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazyLoadKind
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.UnselectinatorSpringConfiguration
import jakarta.transaction.Transactional
import java.util.function.Supplier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = TestApplication)
@Import(UnselectinatorSpringConfiguration)
@ActiveProfiles("test")
class UnselectinatorIntegrationSpec extends AbstractTripwireSpec {

    @Transactional
    def "lazy loading still reproduces the expected enrollment summaries"() {
        when:
        SchoolClass schoolClass = schoolClassRepository.findByCourseCode("CS101")
                .orElseThrow { new AssertionError("Expected to find seeded class CS101") }

        def summaries = schoolClass.students.toList().sort { it.id }
                .collect { student ->
                    "${student.firstName} (advisor: ${student.advisor.lastName}) is enrolled in ${student.classes.size()} classes"
                }
                .sort()

        then:
        summaries == [
                "Alice (advisor: Smith) is enrolled in 3 classes",
                "Bob (advisor: Smith) is enrolled in 3 classes",
                "Diana (advisor: Brown) is enrolled in 2 classes"
        ]
    }

    @Transactional
    def "unselectinator attributes lazy selects to repository endpoints"() {
        when:
        def observation = unselectinator.observe({
            SchoolClass schoolClass = schoolClassRepository.findByCourseCode("CS101")
                    .orElseThrow { new AssertionError("Expected to find seeded class CS101") }

            schoolClass.students.toList().sort { it.id }
                    .collect { student ->
                        "${student.firstName} (advisor: ${student.advisor.lastName}) is enrolled in ${student.classes.size()} classes"
                    }
                    .sort()
        } as Supplier<List<String>>)

        then:
        observation.value() == [
                "Alice (advisor: Smith) is enrolled in 3 classes",
                "Bob (advisor: Smith) is enrolled in 3 classes",
                "Diana (advisor: Brown) is enrolled in 2 classes"
        ]

        and:
        def report = observation.report()
        // Student id order lets earlier eager SchoolClass.teacher loads initialize later advisors.
        report.lazySelectCount == 4
        report.countLazySelectsByRelation("students") == 1L
        report.countLazySelectsByRelation("classes") == 3L
        report.lazySelectEvents().count {
            it.kind() == LazyLoadKind.ENTITY_LOAD
        } == 0
        report.countLazySelectsInitiatedBy("${SchoolClassRepository.name}#findByCourseCode") == 4L
    }

    @Transactional
    def "unselectinator also tracks entity manager initiated fetches"() {
        when:
        def observation = unselectinator.observe({
            SchoolClass schoolClass = entityManager.createQuery(
                    "select sc from SchoolClass sc where sc.courseCode = :courseCode",
                    SchoolClass
            )
                    .setParameter("courseCode", "CS101")
                    .singleResult

            schoolClass.students.toList().sort { it.id }
                    .collect { student -> "${student.firstName} is enrolled in ${student.classes.size()} classes" }
                    .sort()
        } as Supplier<List<String>>)

        then:
        observation.value() == [
                "Alice is enrolled in 3 classes",
                "Bob is enrolled in 3 classes",
                "Diana is enrolled in 2 classes"
        ]

        and:
        def report = observation.report()
        // Student id order lets earlier eager SchoolClass.teacher loads initialize later advisors.
        report.lazySelectCount == 4
        report.countLazySelectsByRelation("students") == 1L
        report.countLazySelectsByRelation("classes") == 3L
        report.lazySelectEvents().count {
            it.kind() == LazyLoadKind.ENTITY_LOAD
        } == 0
        report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery") == 4L
    }
}




