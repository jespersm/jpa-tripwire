package io.github.jespersm.twca.test;

import io.github.jespersm.twca.test.repository.SchoolClassRepository;
import io.github.jespersm.twca.unselectinator.core.Unselectinator;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Common parts for testing the JTA T.W.C.A.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
abstract class AbstractTwcaTest {

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
    protected Unselectinator unselectinator;

    @Autowired
    @Qualifier("observedEntityManager")
    protected EntityManager entityManager;
}
