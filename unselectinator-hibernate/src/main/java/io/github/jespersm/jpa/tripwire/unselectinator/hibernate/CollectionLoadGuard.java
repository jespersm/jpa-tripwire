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
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Always-on N+1 guard for entity query results and initialized collections.
 * Automatic mode discovers each member's mapped to-one and collection associations.
 * The five-argument configuration retains the original explicitly selected role mode.
 *
 * <p>Reports and enforcement share a session-local association index, cached Hibernate
 * mapping metadata, and fetch origins. Either integration may be registered first.
 */
public final class CollectionLoadGuard implements Integrator {
    private final Configuration configuration;
    private final Map<EventSource, SessionGroups> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private HibernateUnselectinatorEventListener index;

    public CollectionLoadGuard(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * @param collectionRole Hibernate collection role, or null for automatic discovery
     * @param association member association, or null for automatic discovery
     * @param ratio exclusive upper bound for SQL-producing lazy operations / distinct members
     * @param minimumGroupSize smallest source group eligible for enforcement
     * @param minimumLazyLoads smallest number of SQL-producing lazy operations eligible for enforcement
     * @param includes eligible association owner types; empty includes all
     * @param excludes excluded owner types; exclusions take precedence
     */
    public record Configuration(String collectionRole, String association, double ratio,
                                int minimumGroupSize, int minimumLazyLoads,
                                List<Class<?>> includes, List<Class<?>> excludes) {
        public Configuration(double ratio, int minimumGroupSize, int minimumLazyLoads) {
            this(null, null, ratio, minimumGroupSize, minimumLazyLoads, List.of(), List.of());
        }

        public Configuration(String collectionRole, String association, double ratio,
                             int minimumGroupSize, int minimumLazyLoads) {
            this(collectionRole, association, ratio, minimumGroupSize, minimumLazyLoads, List.of(), List.of());
        }

        public Configuration {
            if ((collectionRole == null) != (association == null)
                    || collectionRole != null && (collectionRole.isBlank() || association.isBlank())) {
                throw new IllegalArgumentException("Specify both collection role and association, or neither for automatic discovery");
            }
            includes = List.copyOf(includes);
            excludes = List.copyOf(excludes);
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
        var session = sessions.get(load.session());
        if (session == null) {
            return;
        }
        var groups = session.pending.remove(load.target());
        if (groups == null || !load.executedSql()) {
            return;
        }
        var triggerLocation = SourceLocation.capture();
        for (Group group : groups) {
            group.triggerLocation = triggerLocation;
            group.lazyLoads++;
            evaluate(group);
        }
    }

    private void evaluate(Group group) {
        if (group.complete && group.size >= configuration.minimumGroupSize()
                && group.lazyLoads >= configuration.minimumLazyLoads()
                && (double) group.lazyLoads / group.size > configuration.ratio()) {
            throw new ThresholdExceededException(new Violation(group.source, group.ownerId,
                    group.association, group.size, group.lazyLoads, configuration.ratio(), group.origins,
                    group.triggerLocation));
        }
    }

    private boolean included(Class<?> type) {
        return configuration.excludes().stream().noneMatch(excluded -> excluded.isAssignableFrom(type))
                && (configuration.includes().isEmpty()
                || configuration.includes().stream().anyMatch(included -> included.isAssignableFrom(type)));
    }

    private void collectionLoaded(EventSource session, HibernateUnselectinatorEventListener.CollectionGroup collection) {
        if (configuration.collectionRole() != null && !configuration.collectionRole().equals(collection.role())) {
            return;
        }
        if (collection.complete() && collection.members().size() < configuration.minimumGroupSize()) {
            return;
        }
        var state = sessions.computeIfAbsent(session, ignored -> new SessionGroups());
        var groups = state.groups.computeIfAbsent(collection.identity(), ignored -> new LinkedHashMap<>());
        var eligible = new LinkedHashMap<String, Set<EntityKey>>();
        for (var member : collection.members().keySet()) {
            if (!included(member.getPersister().getMappedClass())) {
                continue;
            }
            for (var attribute : index.attributes(member.getPersister())) {
                if (configuration.association() == null || configuration.association().equals(attribute.name())) {
                    eligible.computeIfAbsent(attribute.name(), ignored -> new LinkedHashSet<>()).add(member);
                }
            }
        }
        for (var entry : eligible.entrySet()) {
            var group = groups.computeIfAbsent(entry.getKey(), association ->
                    new Group(collection.role(), collection.ownerId(), association, collection.origins()));
            group.size = entry.getValue().size();
            group.complete = collection.complete();
            for (var member : entry.getValue()) {
                for (var candidate : collection.members().get(member)) {
                    if (entry.getKey().equals(candidate.attribution().relationName())) {
                        state.pending.computeIfAbsent(candidate.target(), ignored -> new LinkedHashSet<>()).add(group);
                    }
                }
            }
            evaluate(group);
        }
        if (state.pending.isEmpty() && groups.values().stream().allMatch(group -> group.lazyLoads == 0)) {
            state.groups.remove(collection.identity());
            if (state.groups.isEmpty()) {
                sessions.remove(session);
            }
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

    private static final class SessionGroups {
        private final Map<Object, Set<Group>> pending = new HashMap<>();
        private final Map<Object, Map<String, Group>> groups = new HashMap<>();
    }

    private static final class Group {
        private final String source;
        private final Object ownerId;
        private final String association;
        private final List<FetchOrigin> origins;
        private int size;
        private int lazyLoads;
        private SourceLocation triggerLocation;
        private boolean complete;

        private Group(String source, Object ownerId, String association, List<FetchOrigin> origins) {
            this.source = source;
            this.ownerId = ownerId;
            this.association = association;
            this.origins = origins;
        }
    }
}
