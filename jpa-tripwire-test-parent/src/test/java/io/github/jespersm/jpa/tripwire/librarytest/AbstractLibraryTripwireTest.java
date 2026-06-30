package io.github.jespersm.jpa.tripwire.librarytest;

import io.github.jespersm.jpa.tripwire.librarytest.repository.BookCopyRepository;
import io.github.jespersm.jpa.tripwire.librarytest.repository.LoanRepository;
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.UnselectinatorSpringConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

@SpringBootTest(classes = LibraryTestApplication.class)
@Import(UnselectinatorSpringConfiguration.class)
@ActiveProfiles("library-test")
abstract class AbstractLibraryTripwireTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("library_tripwire_test")
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
    protected BookCopyRepository bookCopyRepository;

    @Autowired
    protected LoanRepository loanRepository;

    @Autowired
    protected Unselectinator unselectinator;

    @Autowired
    @Qualifier("observedEntityManager")
    protected EntityManager entityManager;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;
}
