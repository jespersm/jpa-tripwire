package io.github.jespersm.jpa.tripwire.indexinator.hibernate;

import io.github.jespersm.jpa.tripwire.indexinator.core.Indexinator;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.Issue;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.*;
import io.github.jespersm.jpa.tripwire.test.model.fixture.*;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HibernateElementCollectionIndexTest {
    private static final String URL = "jdbc:h2:mem:element_collection_indexes;DB_CLOSE_DELAY=-1";
    private static final List<Class<?>> ENTITIES = List.of(Customer.class, Order.class, Product.class,
            ProductCategory.class, ProductReview.class, LineItem.class, ShippingInfo.class, OrderComment.class);
    private static StandardServiceRegistry registry;
    private static SessionFactory factory;

    @BeforeAll
    static void startHibernate() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", URL)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        ENTITIES.forEach(sources::addAnnotatedClass);
        factory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void stopHibernate() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @ParameterizedTest
    @CsvSource({"tags, comment_tags, comment_id", "shippingAddresses, customer_shipping_addresses, customer_id"})
    void detectsMissingOwnerIndexAndAcceptsLeadingIndex(String property, String table, String column) throws Exception {
        Class<?> entity = property.equals("tags") ? OrderComment.class : Customer.class;
        List<IndexRequirement> requirements = new MetamodelIndexRequirementCollector()
                .collect(factory.getMetamodel(), List.of(entity), List.of());
        IndexRequirement collectionRequirement = requirements.stream()
                .filter(requirement -> requirement.getPropertyPaths().equals(List.of(property)))
                .findFirst().orElseThrow();
        assertEquals(new ResolvedRequirementMapping(table, List.of(column)),
                new HibernateSqlMappingResolver(factory.unwrap(SessionFactoryImplementor.class))
                        .resolve(collectionRequirement));

        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE order_comments (id BIGINT PRIMARY KEY)");
            // Deliberately omit a database FK: H2 automatically indexes FK constraints.
            statement.execute("CREATE TABLE " + table + " (" + column + " BIGINT, element_value VARCHAR)");
            Indexinator indexinator = new Indexinator();
            List<Issue> missing = indexinator.inspect(connection, factory, List.of(entity), List.of()).getIssues()
                    .stream().filter(issue -> issue.tableName().equalsIgnoreCase(table)).toList();
            assertEquals(1, missing.size());
            assertEquals(IssueType.MISSING_FK_INDEX, missing.get(0).type());
            assertEquals(column, missing.get(0).columnName());

            statement.execute("CREATE INDEX trailing_owner ON " + table + " (element_value, " + column + ")");
            assertTrue(indexinator.inspect(connection, factory, List.of(entity), List.of()).getIssues().stream()
                    .anyMatch(issue -> issue.tableName().equalsIgnoreCase(table) && issue.type() == IssueType.MISSING_FK_INDEX));

            statement.execute("CREATE INDEX leading_owner ON " + table + " (" + column + ", element_value)");
            assertFalse(indexinator.inspect(connection, factory, List.of(entity), List.of()).getIssues().stream()
                    .anyMatch(issue -> issue.tableName().equalsIgnoreCase(table)));
        }
    }
}
