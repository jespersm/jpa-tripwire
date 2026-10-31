package io.github.jespersm.jpa.tripwire.test;

import io.github.jespersm.jpa.tripwire.indexinator.core.Indexinator;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.InspectionReport;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.Issue;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueSeverity;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import io.github.jespersm.jpa.tripwire.test.entity.SchoolClass;
import io.github.jespersm.jpa.tripwire.test.entity.Student;
import io.github.jespersm.jpa.tripwire.test.entity.Teacher;
import io.github.jespersm.jpa.tripwire.test.repository.SchoolClassRepository;
import io.github.jespersm.jpa.tripwire.test.repository.StudentRepository;
import io.github.jespersm.jpa.tripwire.test.repository.TeacherRepository;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Indexinator using Spring Boot + Testcontainers PostgreSQL
 */
class IndexinatorIntegrationTest extends AbstractTripwireTest {

    @Test
    @Transactional
    void testModelPrefixSearchWorks() {
        List<SchoolClass> schoolClasses = schoolClassRepository.findAllByCourseNameStartsWith("Data");
        assertEquals(2, schoolClasses.size());
        assertEquals(2, schoolClasses.iterator().next().getStudents().size());
    }

    @Test
    void testIndexinatorDetectsMissingIndexes() throws Exception {
        Indexinator indexinator = new Indexinator();

        try (Connection connection = dataSource.getConnection()) {
            // Test with repository analysis
            InspectionReport report = indexinator.inspect(
                    connection,
                    List.of(Teacher.class, Student.class, SchoolClass.class),
                    List.of(SchoolClassRepository.class)
            );

            // With our static SQL schema, we intentionally omit indexes on FK columns!
            // This allows Indexinator to detect real issues.

            // Verify the tool ran successfully
            assertNotNull(report, "Report should not be null");
            assertTrue(report.getTablesInspected() > 0, "Should inspect at least one table");
            assertEquals(3, report.getEntitiesAnalyzed(), "Should analyze 3 entities");

            // We expect to find missing FK indexes!
            assertTrue(report.hasIssues(), "Should detect missing indexes in the schema");

            // We expect at least 2 missing FK indexes:
            // 1. classes.teacher_id (ManyToOne)
            // 2. students.advisor_id (ManyToOne)
            List<Issue> fkIndexIssues = report.getIssues().stream()
                    .filter(issue -> issue.type() == IssueType.MISSING_FK_INDEX)
                    .toList();

            assertTrue(fkIndexIssues.size() >= 2,
                    "Should detect at least 2 missing FK indexes. Found: " + fkIndexIssues.size());

            // Verify specific issues
            boolean foundTeacherIdIssue = fkIndexIssues.stream()
                    .anyMatch(issue -> issue.columnName() != null &&
                            issue.columnName().equalsIgnoreCase("teacher_id"));

            boolean foundAdvisorIdIssue = fkIndexIssues.stream()
                    .anyMatch(issue -> issue.columnName() != null &&
                            issue.columnName().equalsIgnoreCase("advisor_id"));

            assertTrue(foundTeacherIdIssue,
                    "Should detect missing index on classes.teacher_id");
            assertTrue(foundAdvisorIdIssue,
                    "Should detect missing index on students.advisor_id");

            // Verify issue severities
            long highSeverityCount = report.getIssuesBySeverity(IssueSeverity.HIGH).size();
            assertTrue(highSeverityCount > 0,
                    "Should have HIGH severity issues for ManyToOne FKs");

            // Check for repository query index issues
            List<Issue> queryIndexIssues = report.getIssues().stream()
                    .filter(issue -> issue.type() == IssueType.MISSING_QUERY_INDEX)
                    .toList();

            // We should find at least 1 query index issue (course_name from findAllByCourseNameStartsWith)
            assertEquals(3, queryIndexIssues.size(),
                    "Should detect both missing query index.");

            // Check for missing unique indexes issues
            List<Issue> queryUniqueIssues = report.getIssues().stream()
                    .filter(issue -> issue.type() == IssueType.MISSING_UNIQUE_INDEX)
                    .toList();

            // We should find at least 1 query index issue (course_name from findAllByCourseNameStartsWith)
            assertEquals(1, queryUniqueIssues.size(),
                    "Should detect at least 1 missing unique index. Found: " + queryUniqueIssues.size());
        }
    }

    @Test
    void testIndexinatorReportStructure() throws Exception {
        Indexinator indexinator = new Indexinator();

        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = indexinator.inspect(
                    connection,
                    Teacher.class,
                    Student.class,
                    SchoolClass.class
            );

            // Verify report structure
            assertNotNull(report.getTimestamp(), "Report should have a timestamp");
            assertTrue(report.getTablesInspected() > 0, "Should inspect at least one table");
            assertEquals(3, report.getEntitiesAnalyzed(), "Should analyze 3 entities");

            // Verify report can be converted to string
            String reportString = report.toString();
            assertNotNull(reportString, "Report should convert to string");
            assertTrue(reportString.contains("Indexinator"), "Report should contain 'Indexinator'");
        }
    }

    @Test
    void testBuilderPatternWithExplicitClasses() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = Indexinator.builder()
                    .withEntities(Teacher.class, Student.class, SchoolClass.class)
                    .withRepositories(TeacherRepository.class, StudentRepository.class, SchoolClassRepository.class)
                    .build()
                    .inspect(connection);

            // Should produce same results as traditional approach
            assertNotNull(report, "Report should not be null");
            assertTrue(report.hasIssues(), "Should detect missing indexes");
            assertEquals(3, report.getEntitiesAnalyzed(), "Should analyze 3 entities");
        }
    }

    @Test
    void testBuilderPatternWithPackageScanning() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.jpa.tripwire.test.entity")
                    .withScannedRepositories("io.github.jespersm.jpa.tripwire.test.repository")
                    .build()
                    .inspect(connection);

            // Should find all 3 entities via scanning
            assertNotNull(report, "Report should not be null");
            assertTrue(report.hasIssues(), "Should detect missing indexes");
            assertEquals(3, report.getEntitiesAnalyzed(), "Should find 3 entities via package scan");

            // Should detect repository query issues via scanning
            List<Issue> queryIndexIssues = report.getIssues().stream()
                    .filter(issue -> issue.type() == IssueType.MISSING_QUERY_INDEX)
                    .toList();
            assertFalse(queryIndexIssues.isEmpty(),
                    "Should detect query index issues from scanned repositories");
        }
    }

    @Test
    void testBuilderPatternWithExclusions() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.jpa.tripwire.test.entity")
                    .exceptEntity(Teacher.class)
                    .build()
                    .inspect(connection);

            // Should only analyze 2 entities (Student and SchoolClass)
            assertNotNull(report, "Report should not be null");
            assertEquals(2, report.getEntitiesAnalyzed(), "Should analyze only 2 entities after exclusion");
        }
    }
}

