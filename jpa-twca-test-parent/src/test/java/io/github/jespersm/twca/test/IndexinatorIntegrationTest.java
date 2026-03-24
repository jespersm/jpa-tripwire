package io.github.jespersm.twca.test;

import io.github.jespersm.twca.indexinator.core.Indexinator;
import io.github.jespersm.twca.indexinator.core.model.InspectionReport;
import io.github.jespersm.twca.indexinator.core.model.Issue;
import io.github.jespersm.twca.indexinator.core.model.IssueSeverity;
import io.github.jespersm.twca.indexinator.core.model.IssueType;
import io.github.jespersm.twca.test.entity.SchoolClass;
import io.github.jespersm.twca.test.entity.Student;
import io.github.jespersm.twca.test.entity.Teacher;
import io.github.jespersm.twca.test.repository.SchoolClassRepository;
import io.github.jespersm.twca.test.repository.StudentRepository;
import io.github.jespersm.twca.test.repository.TeacherRepository;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Indexinator using Spring Boot 3.5 + Testcontainers PostgreSQL
 */
class IndexinatorIntegrationTest extends AbstractTwcaTest {

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

            // Print the report for debugging
            System.out.println(report);

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
                    .filter(issue -> issue.getType() == IssueType.MISSING_FK_INDEX)
                    .toList();

            assertTrue(fkIndexIssues.size() >= 2,
                    "Should detect at least 2 missing FK indexes. Found: " + fkIndexIssues.size());

            // Verify specific issues
            boolean foundTeacherIdIssue = fkIndexIssues.stream()
                    .anyMatch(issue -> issue.getColumnName() != null &&
                            issue.getColumnName().equalsIgnoreCase("teacher_id"));

            boolean foundAdvisorIdIssue = fkIndexIssues.stream()
                    .anyMatch(issue -> issue.getColumnName() != null &&
                            issue.getColumnName().equalsIgnoreCase("advisor_id"));

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
                    .filter(issue -> issue.getType() == IssueType.MISSING_QUERY_INDEX)
                    .toList();

            // We should find at least 1 query index issue (course_name from findAllByCourseNameStartsWith)
            assertEquals(3, queryIndexIssues.size(),
                    "Should detect both missing query index.");

            // Check for missing unique indexes issues
            List<Issue> queryUniqueIssues = report.getIssues().stream()
                    .filter(issue -> issue.getType() == IssueType.MISSING_UNIQUE_INDEX)
                    .toList();

            // We should find at least 1 query index issue (course_name from findAllByCourseNameStartsWith)
            assertEquals(1, queryUniqueIssues.size(),
                    "Should detect at least 1 missing unique index. Found: " + queryUniqueIssues.size());

            System.out.println("Findings:");
            System.out.println("   - " + fkIndexIssues.size() + " missing FK indexes");
            System.out.println("   - " + queryIndexIssues.size() + " missing query indexes");
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
                    .withScannedEntities("io.github.jespersm.twca.test.entity")
                    .withScannedRepositories("io.github.jespersm.twca.test.repository")
                    .build()
                    .inspect(connection);

            // Should find all 3 entities via scanning
            assertNotNull(report, "Report should not be null");
            assertTrue(report.hasIssues(), "Should detect missing indexes");
            assertEquals(3, report.getEntitiesAnalyzed(), "Should find 3 entities via package scan");

            // Should detect repository query issues via scanning
            List<Issue> queryIndexIssues = report.getIssues().stream()
                    .filter(issue -> issue.getType() == IssueType.MISSING_QUERY_INDEX)
                    .toList();
            assertTrue(queryIndexIssues.size() >= 1,
                    "Should detect query index issues from scanned repositories");
        }
    }

    @Test
    void testBuilderPatternWithExclusions() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            InspectionReport report = Indexinator.builder()
                    .withScannedEntities("io.github.jespersm.twca.test.entity")
                    .exceptEntity(Teacher.class)
                    .build()
                    .inspect(connection);

            // Should only analyze 2 entities (Student and SchoolClass)
            assertNotNull(report, "Report should not be null");
            assertEquals(2, report.getEntitiesAnalyzed(), "Should analyze only 2 entities after exclusion");
        }
    }
}

