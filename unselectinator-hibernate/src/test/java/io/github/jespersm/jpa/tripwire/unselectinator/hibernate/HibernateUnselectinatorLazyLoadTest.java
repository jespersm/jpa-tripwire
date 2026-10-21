package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.test.model.fixture.Address;
import io.github.jespersm.jpa.tripwire.test.model.fixture.Customer;
import io.github.jespersm.jpa.tripwire.test.model.fixture.LineItem;
import io.github.jespersm.jpa.tripwire.test.model.fixture.Order;
import io.github.jespersm.jpa.tripwire.test.model.fixture.OrderComment;
import io.github.jespersm.jpa.tripwire.test.model.fixture.Product;
import io.github.jespersm.jpa.tripwire.test.model.fixture.ProductCategory;
import io.github.jespersm.jpa.tripwire.test.model.fixture.ProductReview;
import io.github.jespersm.jpa.tripwire.test.model.fixture.ShippingInfo;
import io.github.jespersm.jpa.tripwire.test.callsite.FetchCaller;
import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazyLoadKind;
import io.github.jespersm.jpa.tripwire.unselectinator.core.LazySelectReport;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservationResult;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservedEntityManagerFactory;
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HibernateUnselectinatorLazyLoadTest {
    private final EntityLoadTracker tracker = new EntityLoadTracker();
    private final Unselectinator unselectinator = new Unselectinator(tracker);

    private StandardServiceRegistry registry;
    private EntityManagerFactory entityManagerFactory;
    private List<Long> customerIds;

    @BeforeEach
    void setUp() {
        BootstrapServiceRegistry bootstrapRegistry = new BootstrapServiceRegistryBuilder()
                .applyIntegrator(new HibernateUnselectinatorIntegrator(tracker))
                .build();
        registry = new StandardServiceRegistryBuilder(bootstrapRegistry)
                .applySetting(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL,
                        "jdbc:h2:mem:unselectinator_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .applySetting(AvailableSettings.JAKARTA_JDBC_USER, "sa")
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .applySetting(AvailableSettings.STATEMENT_INSPECTOR,
                        HibernateUnselectinator.statementInspector(tracker))
                .build();

        MetadataSources sources = new MetadataSources(registry);
        entityClasses().forEach(sources::addAnnotatedClass);
        entityManagerFactory = sources.buildMetadata().buildSessionFactory();
        customerIds = seedOrders();
    }

    @AfterEach
    void tearDown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void detectsLazyCollectionLoadsInitiatedByEntityManagerFind() {
        try (EntityManager entityManager = observedEntityManager()) {
            ObservationResult<List<Integer>> observation = unselectinator.observe(() ->
                    customerIds.stream()
                            .map(id -> entityManager.find(Customer.class, id).getOrders().size())
                            .toList()
            );

            assertEquals(List.of(2, 2), observation.value());

            LazySelectReport report = observation.report();
            assertEquals(2, report.getLazySelectCount());
            assertEquals(2L, report.countLazySelectsByRelation("orders"));
            assertEquals(2L, report.lazySelectEvents().stream()
                    .filter(event -> event.kind() == LazyLoadKind.COLLECTION_LOAD)
                    .count());
            assertEquals(2L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#find"));
        }
    }

    @Test
    void detectsLazyToOneLoadsInitiatedByEntityManagerQuery() {
        try (EntityManager entityManager = observedEntityManager()) {
            ObservationResult<List<String>> observation = unselectinator.observe(() ->
                    entityManager.createQuery("select order from Order order order by order.id.orderNumber", Order.class)
                            .getResultList()
                            .stream()
                            .map(order -> order.getShippingInfo().getCarrier())
                            .toList()
            );

            assertEquals(List.of("Carrier 1", "Carrier 2", "Carrier 3", "Carrier 4"), observation.value());

            LazySelectReport report = observation.report();
            assertEquals(4, report.getLazySelectCount(),
                    "Each Order.shippingInfo access must be reported as an individual lazy entity load");
            assertEquals(4L, report.lazySelectEvents().stream()
                    .filter(event -> event.kind() == LazyLoadKind.ENTITY_LOAD)
                    .filter(event -> event.loadedEntity() != null)
                    .filter(event -> ShippingInfo.class.getName().equals(event.loadedEntity().entityName()))
                    .count());
            assertEquals(4L, report.countLazySelectsByRelation("shippingInfo"));
            assertEquals(4L, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
        }
    }

    @Test
    void reportOnlyIndexRemembersEachOriginAfterAnUnrelatedFetch() {
        try (EntityManager entityManager = observedEntityManager()) {
            var report = unselectinator.observe((Runnable) () -> {
                var first = entityManager.find(Customer.class, customerIds.get(0));
                var second = entityManager.createQuery("select c from Customer c where c.id = :id", Customer.class)
                        .setParameter("id", customerIds.get(1)).getSingleResult();
                first.getOrders().forEach(order -> order.getShippingInfo().getCarrier());
                second.getOrders().forEach(order -> order.getShippingInfo().getCarrier());
            });
            assertEquals(3, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#find"));
            assertEquals(3, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
            assertTrue(report.lazySelectEvents().stream().allMatch(event -> !event.attributions().isEmpty()));
        }
    }

    @Test
    void reportOnlyIndexDoesNoAttributionWorkOutsideObservation() {
        var index = HibernateUnselectinatorEventListener.install(
                entityManagerFactory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class), tracker);
        try (var entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.find(Customer.class, customerIds.get(0)).getOrders()
                    .forEach(order -> order.getShippingInfo().getCarrier());
            assertEquals(0, index.cachedMappingCount());
            assertEquals(0, index.trackedSessionCount());
        }
    }

    @Test
    void capturesTheActualLazyCallerSeparatelyFromTheOriginalFetch() {
        try (var entityManager = observedEntityManager()) {
            var report = unselectinator.observe((Runnable) () -> {
                var customer = FetchCaller.loadCustomer(entityManager, customerIds.get(0));
                FetchCaller.initializeOrders(customer);
                customer.getOrders().forEach(FetchCaller::shippingCarrier);
            });
            var collection = report.lazySelectEvents().stream()
                    .filter(event -> event.kind() == LazyLoadKind.COLLECTION_LOAD).findFirst().orElseThrow();
            assertEquals(FetchCaller.class.getName(), collection.triggerLocation().className());
            assertEquals("initializeOrders", collection.triggerLocation().methodName());
            assertEquals("loadCustomer", collection.initiatingEndpoint().callerLocation().methodName());

            var targets = report.lazySelectEvents().stream()
                    .filter(event -> event.kind() == LazyLoadKind.ENTITY_LOAD).toList();
            assertEquals(2, targets.size());
            for (var target : targets) {
                assertEquals(FetchCaller.class.getName(), target.triggerLocation().className());
                assertEquals("shippingCarrier", target.triggerLocation().methodName());
                assertEquals("FetchCaller.java", target.triggerLocation().fileName());
                assertTrue(target.triggerLocation().lineNumber() > 0);
                assertEquals("loadCustomer", target.initiatingEndpoint().callerLocation().methodName());
                assertNotEquals(target.triggerLocation(), target.attributions().get(0).origin().location());
                assertTrue(report.toString().contains("triggered at " + target.triggerLocation()));
            }
            assertSame(targets.get(0).triggerLocation(), targets.get(1).triggerLocation(),
                    "Repeated lazy loads from one call site should retain one shared location value");
        }
    }

    private EntityManager observedEntityManager() {
        return ObservedEntityManagerFactory.wrap(entityManagerFactory.createEntityManager(), tracker);
    }

    private List<Long> seedOrders() {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                Customer firstCustomer = customer("first@example.test", "First");
                Customer secondCustomer = customer("second@example.test", "Second");
                addOrder(firstCustomer, 1L, 1);
                addOrder(firstCustomer, 2L, 2);
                addOrder(secondCustomer, 3L, 3);
                addOrder(secondCustomer, 4L, 4);
                entityManager.persist(firstCustomer);
                entityManager.persist(secondCustomer);
                entityManager.flush();
                transaction.commit();
                return List.of(firstCustomer.getId(), secondCustomer.getId());
            } catch (RuntimeException ex) {
                transaction.rollback();
                throw ex;
            }
        }
    }

    private static Customer customer(String email, String firstName) {
        return new Customer(email, firstName, "Customer",
                new Address("Test Street 1", "Test City", "1000", "DK"));
    }

    private static void addOrder(Customer customer, long orderNumber, int carrierNumber) {
        Order order = new Order(2026, orderNumber, customer, LocalDateTime.of(2026, 1, 1, 12, 0));
        order.setShippingInfo(new ShippingInfo("Carrier " + carrierNumber, "TRACK-" + carrierNumber,
                new Address("Shipping Street " + carrierNumber, "Test City", "1000", "DK")));
        customer.addOrder(order);
    }

    private static List<Class<?>> entityClasses() {
        return List.of(
                Customer.class,
                Order.class,
                ShippingInfo.class,
                LineItem.class,
                OrderComment.class,
                Product.class,
                ProductCategory.class,
                ProductReview.class
        );
    }
}
