package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.FetchOrigin;
import io.github.jespersm.jpa.tripwire.unselectinator.core.SourceLocation;
import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventSource;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Experimental always-on guard for {@code A.bs -> B.c}, with proxy-based lazy to-one
 * associations. Each initialization of the configured collection creates an independent
 * group. No explicit observation or EntityManager decoration is required.
 *
 * <p>Reports and enforcement share a session-local association index, cached Hibernate
 * mapping metadata, and fetch origins. Either integration may be registered first.
 */
public final class CollectionLoadGuard implements Integrator {
    private final Configuration configuration;
    private final Map<EventSource, Map<EntityKey, List<Group>>> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private HibernateUnselectinatorEventListener index;

    public CollectionLoadGuard(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * @param collectionRole Hibernate collection role, normally {@code fully.qualified.A.bs}
     * @param association direct to-one property on the collection's entity elements
     * @param ratio exclusive upper bound for SQL-producing lazy operations / distinct members
     * @param minimumGroupSize smallest collection group eligible for enforcement
     * @param minimumLazyLoads smallest number of SQL-producing lazy operations eligible for enforcement
     */
    public record Configuration(String collectionRole, String association, double ratio,
                                int minimumGroupSize, int minimumLazyLoads) {
        public Configuration {
            if (collectionRole == null || collectionRole.isBlank()
                    || association == null || association.isBlank()) {
                throw new IllegalArgumentException("Collection role and association must be specified");
            }
            if (!Double.isFinite(ratio) || ratio < 0 || ratio > 1) {
                throw new IllegalArgumentException("Ratio must be between 0 and 1");
            }
            if (minimumGroupSize < 1 || minimumLazyLoads < 1) {
                throw new IllegalArgumentException("Minimum group size and lazy loads must be positive");
            }
        }
    }

    public record Violation(String collectionRole, Object ownerId, String association,
                            int groupSize, int lazyLoadCount, double ratioLimit, List<FetchOrigin> origins,
                            SourceLocation triggerLocation) {
        public Violation {
            origins = List.copyOf(origins);
        }
    }

    public static final class ThresholdExceededException extends HibernateException {
        private final Violation violation;

        private ThresholdExceededException(Violation violation) {
            super(String.format(Locale.ROOT,
                    "Lazy-load threshold exceeded for %s#%s -> %s: %d SQL-producing lazy loads / %d members (%.1f%% > %.1f%%)",
                    violation.collectionRole(), violation.ownerId(), violation.association(),
                    violation.lazyLoadCount(), violation.groupSize(),
                    100.0 * violation.lazyLoadCount() / violation.groupSize(), 100.0 * violation.ratioLimit())
                    + " triggered at " + violation.triggerLocation() + " origins=" + violation.origins());
            this.violation = violation;
        }

        public Violation violation() {
            return violation;
        }
    }

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        index = HibernateUnselectinatorEventListener.install(sessionFactory, null);
        index.observeCollections(this::collectionLoaded);
        index.observeLoads(this::loadCompleted);
        index.observeResets(sessions::remove);
    }

    private void loadCompleted(HibernateUnselectinatorEventListener.CompletedLoad load) {
        var pending = sessions.get(load.session());
        if (pending == null) {
            return;
        }
        var groups = pending.remove(load.target());
        if (groups == null || !load.executedSql()) {
            return;
        }
        for (Group group : groups) {
            group.lazyLoads++;
            if (group.lazyLoads >= configuration.minimumLazyLoads()
                    && (double) group.lazyLoads / group.size > configuration.ratio()) {
                throw new ThresholdExceededException(new Violation(configuration.collectionRole(), group.ownerId,
                        configuration.association(), group.size, group.lazyLoads, configuration.ratio(), group.origins,
                        SourceLocation.capture()));
            }
        }
    }

    private void collectionLoaded(EventSource session, HibernateUnselectinatorEventListener.CollectionGroup collection) {
        if (!configuration.collectionRole().equals(collection.role())
                || collection.members().size() < configuration.minimumGroupSize()) {
            return;
        }
        for (var member : collection.members().keySet()) {
            if (!(member.getPersister().findAttributeMapping(configuration.association()) instanceof EntityAssociationMapping)) {
                throw new IllegalArgumentException("Guard association must be a direct to-one entity property: "
                        + configuration.association());
            }
        }
        var targets = collection.members().values().stream().flatMap(List::stream)
                .filter(candidate -> configuration.association().equals(candidate.attribution().relationName()))
                .map(HibernateUnselectinatorEventListener.PendingAssociation::target).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        var pending = sessions.computeIfAbsent(session, key -> new HashMap<>());
        var group = new Group(collection.ownerId(), collection.members().size(), collection.origins());
        for (var target : targets) {
            pending.computeIfAbsent(target, key -> new ArrayList<>()).add(group);
        }
    }

    // Package-private lifecycle diagnostic for the integration tests.
    int trackedSessionCount() {
        return sessions.size();
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        sessions.clear();
        index.clear();
    }

    private static final class Group {
        private final Object ownerId;
        private final int size;
        private final List<FetchOrigin> origins;
        private int lazyLoads;

        private Group(Object ownerId, int size, List<FetchOrigin> origins) {
            this.ownerId = ownerId;
            this.size = size;
            this.origins = origins;
        }
    }

}
