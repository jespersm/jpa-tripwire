package io.github.jespersm.jpa.tripwire.test

import io.github.jespersm.jpa.tripwire.indexinator.core.Indexinator
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueSeverity
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType
import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass
import io.github.jespersm.jpa.tripwire.test.entity.Student
import io.github.jespersm.jpa.tripwire.test.entity.Teacher
import io.github.jespersm.jpa.tripwire.test.repository.SchoolClassRepository
import io.github.jespersm.jpa.tripwire.test.repository.StudentRepository
import io.github.jespersm.jpa.tripwire.test.repository.TeacherRepository
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.UnselectinatorSpringConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import spock.lang.Unroll

@SpringBootTest(classes = TestApplication)
@Import(UnselectinatorSpringConfiguration)
@ActiveProfiles("test")
class IndexinatorIntegrationSpec extends AbstractTripwireSpec {

    def "detects missing indexes for mapped entities and repository queries"() {
        given:
        def indexinator = new Indexinator()

        when:
        def report
        dataSource.connection.withCloseable { connection ->
            report = indexinator.inspect(
                    connection,
                    [Teacher, Student, SchoolClass],
                    [SchoolClassRepository]
            )
        }
        def fkIssues = report.issues.findAll { it.type() == IssueType.MISSING_FK_INDEX }
        def fkColumns = fkIssues.findResults { it.columnName()?.toLowerCase() }
        def queryIssues = report.issues.findAll { it.type() == IssueType.MISSING_QUERY_INDEX }
        def uniqueIssues = report.issues.findAll { it.type() == IssueType.MISSING_UNIQUE_INDEX }

        then:
        report != null
        report.tablesInspected > 0
        report.entitiesAnalyzed == 3
        report.hasIssues()
        fkIssues.size() >= 2
        fkColumns.containsAll(["teacher_id", "advisor_id"])
        report.getIssuesBySeverity(IssueSeverity.HIGH).size() > 0
        queryIssues.size() >= 1
        uniqueIssues.size() >= 1
    }

    @Unroll
    def "builder mode '#mode' supports integration inspection"() {
        when:
        def report
        dataSource.connection.withCloseable { connection ->
            report = inspector.call(connection)
        }

        then:
        report != null
        report.entitiesAnalyzed == 3
        report.hasIssues()
        report.issues.any { it.type() == IssueType.MISSING_QUERY_INDEX }

        where:
        mode              | inspector
        "explicit classes" | { connection ->
            Indexinator.builder()
                    .withEntities(Teacher, Student, SchoolClass)
                    .withRepositories(TeacherRepository, StudentRepository, SchoolClassRepository)
                    .build()
                    .inspect(connection)
        }
        "package scan"     | { connection ->
            Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.jpa.tripwire.test.entity")
                    .withScannedRepositories("io.github.jespersm.jpa.tripwire.test.repository")
                    .build()
                    .inspect(connection)
        }
    }

    def "builder exclusions limit analyzed entities"() {
        when:
        def report
        dataSource.connection.withCloseable { connection ->
            report = Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.jpa.tripwire.test.entity")
                    .exceptEntity(Teacher)
                    .build()
                    .inspect(connection)
        }

        then:
        report != null
        report.entitiesAnalyzed == 2
    }
}


