package io.github.jespersm.jpa.tripwire.test.students;

import io.github.jespersm.jpa.tripwire.test.students.repository.SchoolClassRepository;
import io.github.jespersm.jpa.tripwire.test.students.service.SchoolClassService;
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.UnselectinatorSpringConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Common parts for testing the JPA Tripwire tools.
 */
@SpringBootTest(classes = StudentsTestApplication.class)
@Import(UnselectinatorSpringConfiguration.class)
@ActiveProfiles({"test", "students"})
abstract class AbstractStudentsTripwireTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("indexinator_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected SchoolClassRepository schoolClassRepository;

    @Autowired
    protected SchoolClassService schoolClassService;

    @Autowired
    protected Unselectinator unselectinator;

    @Autowired
    @Qualifier("observedEntityManager")
    protected EntityManager entityManager;
}
