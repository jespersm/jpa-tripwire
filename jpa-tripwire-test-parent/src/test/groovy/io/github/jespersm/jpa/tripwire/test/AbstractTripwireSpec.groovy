package io.github.jespersm.jpa.tripwire.test

import io.github.jespersm.jpa.tripwire.test.repository.SchoolClassRepository
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring.UnselectinatorSpringConfiguration
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Specification

import javax.sql.DataSource

@SpringBootTest(classes = TestApplication)
@Import(UnselectinatorSpringConfiguration)
@ActiveProfiles("test")
abstract class AbstractTripwireSpec extends Specification {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("indexinator_test")
            .withUsername("test")
            .withPassword("test")

    static {
        postgres.start()
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.datasource.username", postgres::getUsername)
        registry.add("spring.datasource.password", postgres::getPassword)
    }

    @Autowired
    protected DataSource dataSource

    @Autowired
    protected SchoolClassRepository schoolClassRepository

    @Autowired
    protected Unselectinator unselectinator

    @Autowired
    @Qualifier("observedEntityManager")
    protected EntityManager entityManager
}

