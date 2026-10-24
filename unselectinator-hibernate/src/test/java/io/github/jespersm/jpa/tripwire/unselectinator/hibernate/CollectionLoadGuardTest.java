package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservedEntityManagerFactory;
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator;
import io.github.jespersm.jpa.tripwire.test.callsite.FetchCaller;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.MapKey;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Transient;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.UnresolvableObjectException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CollectionLoadGuardTest {
    private static final AtomicInteger TARGET_GETTER_CALLS = new AtomicInteger();
    private static final AtomicInteger COLLECTION_GETTER_CALLS = new AtomicInteger();
    private static final String ROLE = Parent.class.getName() + ".children";
    private final EntityLoadTracker tracker = new EntityLoadTracker();
    private StandardServiceRegistry registry;
    private EntityManagerFactory factory;
    private CollectionLoadGuard guard;

    @AfterEach
    void close() {
        if (factory != null) {
            factory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void automaticallyFailsWhenDirectLazyLoadsCrossTheRatio() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            List<Child> children = children(em, 1);
            for (int i = 0; i < 3; i++) {
                assertEquals("target-" + (100 + i), children.get(i).target.getLabel());
            }
            // Exactly 3/6 is allowed. An unrelated query must not change attribution.
            em.createQuery("select p from GuardParent p where p.id = 2", Parent.class).getSingleResult();
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> children.get(3).target.getLabel());
            assertEquals(new CollectionLoadGuard.Violation(ROLE, 1L, "target", 6, 4, 0.5,
                    failure.violation().origins(), failure.violation().triggerLocation()), failure.violation());
        }
        assertEquals(0, guard.trackedSessionCount(), "Closing the real session must release guard state");
    }

    @Test
    void honorsTheMinimumNumberOfLazyLoads() {
        startGuard(0.1, 6, 5);
        try (var em = factory.createEntityManager()) {
            var children = children(em, 1);
            for (int i = 0; i < 4; i++) {
                children.get(i).target.getLabel();
            }
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> children.get(4).target.getLabel());
            assertEquals(5, failure.violation().lazyLoadCount());
        }
    }

    @Test
    void ignoresCollectionsBelowTheMinimumSize() {
        startGuard(0.1, 7, 1);
        try (var em = factory.createEntityManager()) {
            children(em, 1).forEach(child -> child.target.getLabel());
            assertEquals(0, guard.trackedSessionCount());
        }
    }

    @Test
    void keepsDifferentCollectionsInTheSameSessionSeparate() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            var first = children(em, 1);
            var second = children(em, 2);
            for (int i = 0; i < 3; i++) {
                first.get(i).target.getLabel();
                second.get(i).target.getLabel();
            }
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> first.get(3).target.getLabel());
            assertEquals(1L, failure.violation().ownerId());
            assertEquals(4, failure.violation().lazyLoadCount());
        }
    }

    @Test
    void keepsSessionsOnTheSameThreadSeparate() {
        startGuard(0.5, 6, 2);
        try (var firstEm = factory.createEntityManager(); var secondEm = factory.createEntityManager()) {
            var first = children(firstEm, 1);
            var second = children(secondEm, 1);
            for (int i = 0; i < 3; i++) {
                first.get(i).target.getLabel();
                second.get(i).target.getLabel();
            }
            assertEquals(2, guard.trackedSessionCount());
            assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> first.get(3).target.getLabel());
            assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> second.get(3).target.getLabel());
        }
        assertEquals(0, guard.trackedSessionCount());
    }

    @Test
    void countsASharedTargetOnlyOnce() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            var children = children(em, 3);
            children.forEach(child -> assertEquals("shared", child.target.getLabel()));
            children.forEach(child -> child.target.getLabel());
        }
    }

    @Test
    void doesNotCountTargetsAlreadyInThePersistenceContext() {
        startGuard(0.1, 6, 1);
        try (var em = factory.createEntityManager()) {
            em.createQuery("select t from GuardTarget t", Target.class).getResultList();
            children(em, 1).forEach(child -> child.target.getLabel());
        }
    }

    @Test
    void countsABatchAsOneLazyOperationRatherThanSixHydratedTargets() {
        startGuard(0.2, 6, 2);
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try (var em = factory.createEntityManager()) {
            em.unwrap(Session.class).setFetchBatchSize(16);
            var children = children(em, 1);
            long statementsBefore = statistics.getPrepareStatementCount();
            children.forEach(child -> child.target.getLabel());
            assertEquals(1, statistics.getPrepareStatementCount() - statementsBefore,
                    "All six targets must actually load in a single batch query");
        }
    }

    @Test
    void clearDiscardsTheOldGroupBeforeReloading() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            var first = children(em, 1);
            for (int i = 0; i < 3; i++) {
                first.get(i).target.getLabel();
            }
            em.clear();
            var reloaded = children(em, 1);
            for (int i = 0; i < 3; i++) {
                reloaded.get(i).target.getLabel();
            }
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> reloaded.get(3).target.getLabel());
            assertEquals(4, failure.violation().lazyLoadCount());
        }
    }

    @Test
    void preservesHibernateLoadFailuresWithoutLeavingAnObservationScope() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            var children = children(em, 1);
            assertThrows(UnresolvableObjectException.class,
                    () -> em.getReference(Target.class, -1L).getLabel());
            for (int i = 0; i < 3; i++) {
                children.get(i).target.getLabel();
            }
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> children.get(3).target.getLabel());
            assertEquals(4, failure.violation().lazyLoadCount());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesExplicitObservationAlongsideTheGuard(boolean guardFirst) {
        startGuard(1.0, 6, 2, guardFirst);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () ->
                    children(em, 1).forEach(child -> child.target.getLabel()));
            assertEquals(7, report.getLazySelectCount(), "One collection plus six lazy targets");
        }
    }

    @Test
    void reportsKeepTheOriginalEndpointAcrossInterleavedFetchesAndCollections() {
        startGuard(1.0, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () -> {
                var first = em.find(Parent.class, 1L);
                var second = em.createQuery("select p from GuardParent p where p.id = 2", Parent.class).getSingleResult();
                first.children.size();
                second.children.size();
                first.children.forEach(child -> child.target.getLabel());
                second.children.forEach(child -> child.target.getLabel());
            });
            var loads = report.lazySelectEvents().stream().filter(event -> "target".equals(event.relationName())).toList();
            assertEquals(12, loads.size());
            for (var load : loads) {
                long id = (Long) load.loadedEntity().entityId();
                String method = id < 200 ? "find" : "createQuery";
                assertEquals(method, load.initiatingEndpoint().methodName());
                assertEquals(1, load.attributions().size());
                var candidate = load.attributions().get(0);
                assertEquals(id, candidate.owner().entityId());
                assertEquals("target", candidate.origin().relationName());
                assertEquals("children", candidate.origin().parent().relationName());
                assertEquals(id / 100, candidate.origin().parent().owner().entityId());
                assertEquals(method, candidate.origin().initiatingEndpoint().methodName());
            }
            assertEquals(7, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#find"));
            assertEquals(7, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
        }
    }

    @Test
    void sharedTargetsPreserveAllCandidateOwnersAndOriginsWithoutInventingOne() {
        startGuard(1.0, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () -> {
                var first = em.find(Parent.class, 3L);
                var second = em.createQuery("select p from GuardParent p where p.id = 4", Parent.class).getSingleResult();
                first.children.size();
                second.children.size();
                first.children.get(0).target.getLabel();
            });
            var loads = report.lazySelectEvents().stream().filter(event -> "target".equals(event.relationName())).toList();
            assertEquals(1, loads.size());
            var load = loads.get(0);
            assertNull(load.owner());
            assertNull(load.initiatingEndpoint());
            assertEquals(12, load.attributions().size());
            assertEquals(2, load.attributions().stream()
                    .map(candidate -> candidate.origin().initiatingEndpoint()).distinct().count());
            assertEquals(2, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#find"));
            assertEquals(2, report.countLazySelectsInitiatedBy("jakarta.persistence.EntityManager#createQuery"));
            assertTrue(report.toString().contains("ambiguous origin"));
        }
    }

    @Test
    void indexesPropertyMappingsWithoutCallingApplicationGettersAndReusesTheMetadata() {
        startGuard(1.0, 6, 2);
        var index = HibernateUnselectinatorEventListener.install(
                factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class), tracker);
        TARGET_GETTER_CALLS.set(0);
        COLLECTION_GETTER_CALLS.set(0);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            new Unselectinator(tracker).observe((Runnable) () -> {
                children(em, 1).forEach(child -> child.target.getLabel());
                int cachedTypes = index.cachedMappingCount();
                children(em, 2).forEach(child -> child.target.getLabel());
                assertEquals(cachedTypes, index.cachedMappingCount(), "Loading more instances must reuse mapping metadata");
            });
            assertEquals(0, TARGET_GETTER_CALLS.get(), "Attribution must use loaded state, not mapped getters");
            assertEquals(0, COLLECTION_GETTER_CALLS.get(), "Report edges must use Hibernate's initialized collection");
            assertSame(index, HibernateUnselectinatorEventListener.install(
                    factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class), tracker));
        }
        assertEquals(0, index.trackedSessionCount());
    }

    @Test
    void repeatedExecutionsOfOneQueryHaveDistinctOrigins() {
        startGuard(1.0, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () -> {
                var query = em.createQuery("select p from GuardParent p where p.id = :id", Parent.class);
                var first = query.setParameter("id", 1L).getSingleResult();
                var second = query.setParameter("id", 2L).getSingleResult();
                first.children.size();
                second.children.size();
                first.children.get(0).target.getLabel();
                second.children.get(0).target.getLabel();
            });
            var origins = report.lazySelectEvents().stream().filter(event -> "target".equals(event.relationName()))
                    .map(event -> event.attributions().get(0).origin()).toList();
            assertEquals(2, origins.size());
            assertEquals(origins.get(0).initiatingEndpoint(), origins.get(1).initiatingEndpoint());
            assertNotEquals(origins.get(0).fetchId(), origins.get(1).fetchId());
        }
    }

    @Test
    void anIndexedTargetHydratedByAnotherQueryIsNotALazyProxyInitialization() {
        startGuard(1.0, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () -> {
                children(em, 1);
                // This query deliberately bypasses the EntityManager fetch-scope wrapper.
                em.unwrap(Session.class).createSelectionQuery("select t from GuardTarget t", Target.class).getResultList();
            });
            assertEquals(0, report.countLazySelectsByRelation("target"));
            assertEquals(1, report.getLazySelectCount());
        }
    }

    @Test
    void oneEntityInTwoCollectionsRetainsBothCollectionOrigins() {
        startGuard(1.0, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var report = new Unselectinator(tracker).observe((Runnable) () -> {
                var first = em.find(Bucket.class, 10L);
                var second = em.createQuery("select b from GuardBucket b where b.id = 11", Bucket.class).getSingleResult();
                first.members.size();
                second.members.size();
                assertSame(first.members.get(0), second.members.get(0));
                first.members.get(0).target.getLabel();
            });
            var load = report.lazySelectEvents().stream()
                    .filter(event -> "target".equals(event.relationName())).findFirst().orElseThrow();
            assertEquals(100L, load.owner().entityId());
            assertNull(load.initiatingEndpoint());
            assertEquals(2, load.attributions().size());
            assertEquals(java.util.Set.of(10L, 11L), load.attributions().stream()
                    .map(candidate -> candidate.origin().parent().owner().entityId())
                    .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void guardViolationsCarryTheOriginalFetchEvenAfterAnotherQuery() {
        startGuard(0.5, 6, 2);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> new Unselectinator(tracker).observe((Runnable) () -> {
                        var first = em.find(Parent.class, 1L);
                        first.children.size();
                        em.createQuery("select p from GuardParent p where p.id = 2", Parent.class).getSingleResult();
                        first.children.forEach(child -> child.target.getLabel());
                    }));
            assertEquals(1, failure.violation().origins().size());
            var origin = failure.violation().origins().get(0);
            assertEquals("find", origin.initiatingEndpoint().methodName());
            assertEquals("children", origin.relationName());
            assertEquals(1L, origin.owner().entityId());
        }
    }

    @Test
    void guardViolationIdentifiesTheAccessThatCrossedTheThreshold() {
        startGuard(0.5, 6, 2);
        try (var em = factory.createEntityManager()) {
            var children = children(em, 1);
            for (int i = 0; i < 3; i++) {
                children.get(i).target.getLabel();
            }
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> FetchCaller.access(() -> children.get(3).target.getLabel()));
            assertEquals(FetchCaller.class.getName(), failure.violation().triggerLocation().className());
            assertEquals("access", failure.violation().triggerLocation().methodName());
            assertTrue(failure.violation().triggerLocation().lineNumber() > 0);
            assertTrue(failure.getMessage().contains("triggered at " + failure.violation().triggerLocation()));
        }
    }

    @Test
    void discoversAssociationsOfQueryResultsWithoutSelectingARole() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var rows = em.createQuery("from GuardChild c where c.parent.id = 1 order by c.id", Child.class).getResultList();
            for (int i = 0; i < 3; i++) rows.get(i).target.getLabel();
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> rows.get(3).target.getLabel());
            assertEquals("target", failure.violation().association());
            assertEquals(6, failure.violation().groupSize());
            assertTrue(failure.violation().collectionRole().startsWith("<query:"));
            assertFalse(failure.violation().origins().isEmpty());
        }
    }

    @Test
    void cachedEntitiesInTupleResultsEstablishANewQueryGroup() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            // No query POST_LOAD events will fire for these already-managed owners.
            for (long id = 100; id <= 105; id++) em.find(Child.class, id);
            var rows = em.createQuery("select c, c.id from GuardChild c where c.parent.id = 1 order by c.id", Object[].class).getResultList();
            for (int i = 0; i < 3; i++) ((Child) rows.get(i)[0]).target.getLabel();
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class,
                    () -> ((Child) rows.get(3)[0]).target.getLabel());
            assertEquals(6, failure.violation().groupSize());
            assertTrue(failure.violation().collectionRole().startsWith("<query:"));
        }
    }

    @Test
    void discoversCollectionValuedAssociations() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var rows = em.createQuery("from GuardTarget t where t.id between 100 and 105 order by t.id", Target.class).getResultList();
            for (int i = 0; i < 3; i++) rows.get(i).tags.size();
            var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> rows.get(3).tags.size());
            assertEquals("tags", failure.violation().association());
        }
    }

    @Test
    void tracksMapValuesAndReadOnlyOwners() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = factory.createEntityManager()) {
            var parent = em.unwrap(Session.class).createQuery("from GuardParent where id = 1", Parent.class).setReadOnly(true).getSingleResult();
            var rows = new ArrayList<>(parent.childMap.values());
            assertEquals(6, rows.size());
            for (int i = 0; i < 3; i++) rows.get(i).target.getLabel();
            assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> rows.get(3).target.getLabel());
        }
    }

    @Test
    void tracksReadOnlyQueryResults() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = factory.createEntityManager()) {
            var rows = em.unwrap(Session.class).createQuery("from GuardChild c where c.parent.id = 1 order by c.id", Child.class).setReadOnly(true).getResultList();
            for (int i = 0; i < 3; i++) rows.get(i).target.getLabel();
            assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> rows.get(3).target.getLabel());
        }
    }

    @Test
    void fetchJoinedCollectionsStillEstablishMemberGroups() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var parent = em.createQuery("from GuardParent p join fetch p.children where p.id = 1", Parent.class).getSingleResult();
            for (int i = 0; i < 3; i++) parent.children.get(i).target.getLabel();
            assertThrows(CollectionLoadGuard.ThresholdExceededException.class, () -> parent.children.get(3).target.getLabel());
        }
    }

    @Test
    void fetchJoiningTheAssociationAvoidsAViolation() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            var rows = em.createQuery("from GuardChild c join fetch c.target where c.parent.id = 1", Child.class).getResultList();
            assertDoesNotThrow(() -> rows.forEach(child -> child.target.getLabel()));
        }
    }

    @Test
    void streamEnforcementWaitsForItsFinalGroupSize() {
        startGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2), false);
        try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
            em.getTransaction().begin();
            try (var rows = em.createQuery("from GuardChild c where c.parent.id = 1 order by c.id", Child.class).getResultStream()) {
                var iterator = rows.iterator();
                for (int i = 0; i < 3; i++) iterator.next().target.getLabel();
                assertDoesNotThrow(() -> FetchCaller.access(() -> iterator.next().target.getLabel()));
                iterator.next();
                iterator.next();
                var failure = assertThrows(CollectionLoadGuard.ThresholdExceededException.class, iterator::hasNext);
                assertEquals(6, failure.violation().groupSize());
                assertEquals(4, failure.violation().lazyLoadCount());
                assertEquals("access", failure.violation().triggerLocation().methodName());
            } finally {
                em.getTransaction().rollback();
            }
        }
    }

    @Test
    void exclusionsOverrideIncludes() {
        startGuard(new CollectionLoadGuard.Configuration(null, null, 0.5, 6, 2, List.of(Child.class), List.of(Child.class)), false);
        try (var em = factory.createEntityManager()) {
            assertDoesNotThrow(() -> children(em, 1).forEach(child -> child.target.getLabel()));
        }
    }

    private static List<Child> children(EntityManager em, long parentId) {
        var children = em.find(Parent.class, parentId).children;
        assertEquals(6, children.size());
        return children;
    }

    private void startGuard(double ratio, int minimumGroupSize, int minimumLazyLoads) {
        startGuard(ratio, minimumGroupSize, minimumLazyLoads, false);
    }

    private void startGuard(double ratio, int minimumGroupSize, int minimumLazyLoads, boolean guardFirst) {
        startGuard(new CollectionLoadGuard.Configuration(ROLE, "target", ratio, minimumGroupSize, minimumLazyLoads), guardFirst);
    }

    private void startGuard(CollectionLoadGuard.Configuration configuration, boolean guardFirst) {
        guard = new CollectionLoadGuard(configuration);
        var bootstrapBuilder = new BootstrapServiceRegistryBuilder();
        var reportIntegrator = new HibernateUnselectinatorIntegrator(tracker);
        if (guardFirst) {
            bootstrapBuilder.applyIntegrator(guard).applyIntegrator(reportIntegrator);
        } else {
            bootstrapBuilder.applyIntegrator(reportIntegrator).applyIntegrator(guard);
        }
        var bootstrap = bootstrapBuilder.build();
        registry = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting(AvailableSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.JAKARTA_JDBC_URL, "jdbc:h2:mem:guard_" + UUID.randomUUID())
                .applySetting(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .applySetting(AvailableSettings.STATEMENT_INSPECTOR, HibernateUnselectinator.statementInspector(tracker))
                .build();
        factory = new MetadataSources(registry)
                .addAnnotatedClass(Parent.class)
                .addAnnotatedClass(Child.class)
                .addAnnotatedClass(Target.class)
                .addAnnotatedClass(Bucket.class)
                .buildMetadata().buildSessionFactory();
        try (var em = factory.createEntityManager()) {
            var transaction = em.getTransaction();
            transaction.begin();
            var shared = new Target(1000, "shared");
            for (long parentId = 1; parentId <= 4; parentId++) {
                var parent = new Parent();
                parent.id = parentId;
                for (int i = 0; i < 6; i++) {
                    var child = new Child();
                    child.id = parentId * 100 + i;
                    child.parent = parent;
                    child.target = parentId >= 3 ? shared : new Target(child.id, "target-" + child.id);
                    parent.children.add(child);
                }
                em.persist(parent);
            }
            for (long id = 10; id <= 11; id++) {
                var bucket = new Bucket();
                bucket.id = id;
                bucket.members.add(em.find(Child.class, 100L));
                em.persist(bucket);
            }
            transaction.commit();
        }
    }

    @Entity(name = "GuardParent")
    static class Parent {
        @Id
        private Long id;
        @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
        @OrderBy("id")
        private List<Child> children = new ArrayList<>();
        @OneToMany(mappedBy = "parent")
        @MapKey(name = "id")
        private Map<Long, Child> childMap = new HashMap<>();

        public List<Child> getChildren() {
            COLLECTION_GETTER_CALLS.incrementAndGet();
            return children;
        }
    }

    @Entity(name = "GuardChild")
    static class Child {
        @Id
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        private Parent parent;
        @Transient
        private Target target;

        @Access(AccessType.PROPERTY)
        @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
        public Target getTarget() {
            TARGET_GETTER_CALLS.incrementAndGet();
            return target;
        }

        public void setTarget(Target target) {
            this.target = target;
        }
    }

    @Entity(name = "GuardTarget")
    static class Target {
        @Id
        private Long id;
        private String label;
        @ElementCollection
        private Set<String> tags = new HashSet<>();

        protected Target() {
        }

        Target(long id, String label) {
            this.id = id;
            this.label = label;
            tags.add("tag");
        }

        public String getLabel() {
            return label;
        }
    }

    @Entity(name = "GuardBucket")
    static class Bucket {
        @Id
        private Long id;
        @ManyToMany
        private List<Child> members = new ArrayList<>();
    }
}
