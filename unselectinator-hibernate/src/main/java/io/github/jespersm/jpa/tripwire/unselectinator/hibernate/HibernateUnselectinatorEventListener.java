package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.AssociationAttribution;
import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.FetchOrigin;
import io.github.jespersm.jpa.tripwire.unselectinator.core.TrackedEntityReference;
import io.github.jespersm.jpa.tripwire.unselectinator.core.TrackedRelationEdge;
import org.hibernate.SessionEventListener;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.event.spi.LoadEvent;
import org.hibernate.event.spi.LoadEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.metamodel.mapping.EntityAssociationMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Shared, session-local association index for reports and always-on enforcement.
 * Metadata is cached per Hibernate mapping; entity instances and reflected fields
 * are never retained. Each reverse-index entry preserves its owner's fetch origin.
 */
public class HibernateUnselectinatorEventListener implements PostLoadEventListener,
        InitializeCollectionEventListener, LoadEventListener {
    private final List<EntityLoadTracker> trackers = new ArrayList<>();
    private final Map<EntityPersister, List<AssociationAttribute>> metadata = new ConcurrentHashMap<>();
    private final Map<EventSource, SessionState> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private final List<BiConsumer<EventSource, CollectionGroup>> collectionObservers = new ArrayList<>();
    private final List<Consumer<CompletedLoad>> loadObservers = new ArrayList<>();
    private final List<Consumer<EventSource>> resetObservers = new ArrayList<>();
    private List<LoadEventListener> loadDelegates = List.of();
    private List<InitializeCollectionEventListener> collectionDelegates = List.of();

    public HibernateUnselectinatorEventListener(EntityLoadTracker tracker) {
        if (tracker != null) {
            trackers.add(tracker);
        }
    }

    /** Install once per factory, regardless of which integration is registered first. */
    static HibernateUnselectinatorEventListener install(SessionFactoryImplementor factory, EntityLoadTracker tracker) {
        var registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
        for (var listener : registry.getEventListenerGroup(EventType.POST_LOAD).listeners()) {
            if (listener instanceof HibernateUnselectinatorEventListener existing) {
                if (tracker != null && !existing.trackers.contains(tracker)) {
                    existing.trackers.add(tracker);
                }
                return existing;
            }
        }
        var listener = new HibernateUnselectinatorEventListener(tracker);
        var loads = new ArrayList<LoadEventListener>();
        registry.getEventListenerGroup(EventType.LOAD).listeners().forEach(loads::add);
        listener.loadDelegates = List.copyOf(loads);
        var collections = new ArrayList<InitializeCollectionEventListener>();
        registry.getEventListenerGroup(EventType.INIT_COLLECTION).listeners().forEach(collections::add);
        listener.collectionDelegates = List.copyOf(collections);
        registry.setListeners(EventType.LOAD, listener);
        registry.setListeners(EventType.INIT_COLLECTION, listener);
        registry.appendListeners(EventType.POST_LOAD, listener);
        registry.appendListeners(EventType.CLEAR, event -> listener.reset(event.getSession(), false));
        registry.prependListeners(EventType.EVICT, event -> {
            var state = listener.sessions.get(event.getSession());
            if (state == null) {
                return;
            }
            Object entity = event.getObject();
            var proxy = HibernateProxy.extractLazyInitializer(entity);
            if (proxy != null) {
                entity = proxy.getImplementation(event.getSession());
            }
            EntityEntry entry = event.getSession().getPersistenceContextInternal().getEntry(entity);
            if (entry != null) {
                listener.removeOwner(state, entry.getEntityKey());
                state.origins.remove(entry.getEntityKey());
            }
        });
        return listener;
    }

    void observeCollections(BiConsumer<EventSource, CollectionGroup> observer) {
        collectionObservers.add(observer);
    }

    void observeLoads(Consumer<CompletedLoad> observer) {
        loadObservers.add(observer);
    }

    void observeResets(Consumer<EventSource> observer) {
        resetObservers.add(observer);
    }

    private boolean enabled() {
        return !collectionObservers.isEmpty() || trackers.stream().anyMatch(EntityLoadTracker::isObserving);
    }

    @Override
    public void onLoad(LoadEvent event, LoadType loadType) {
        var state = sessions.get(event.getSession());
        long executionsBefore = state != null ? state.executions : 0;
        boolean lazy = state != null && loadType == IMMEDIATE_LOAD;
        if (lazy) {
            state.lazyLoadDepth++;
        }
        try {
            for (var delegate : loadDelegates) {
                delegate.onLoad(event, loadType);
            }
        } finally {
            if (lazy) {
                state.lazyLoadDepth--;
            }
        }
        if (state != null && loadType == IMMEDIATE_LOAD && event.getResult() != null) {
            var key = event.getSession().generateEntityKey(event.getEntityId(),
                    event.getSession().getFactory().getMappingMetamodel().getEntityDescriptor(event.getEntityClassName()));
            var load = new CompletedLoad(event.getSession(), key, state.executions > executionsBefore);
            loadObservers.forEach(observer -> observer.accept(load));
        }
    }

    @Override
    public void onPostLoad(PostLoadEvent event) {
        if (!enabled()) {
            return;
        }
        var session = event.getSession();
        var state = state(session);
        var key = session.generateEntityKey(event.getId(), event.getPersister());
        var candidates = state.byTarget.remove(key);
        List<AssociationAttribution> attributions = candidates != null ? List.copyOf(candidates) : List.of();
        // Drop resolved reverse edges from their owners too, so the cache retains
        // pending relationships rather than a history of completed target loads.
        for (var owner : attributions.stream().map(AssociationAttribution::owner).distinct().toList()) {
            var ownerKey = session.generateEntityKey(owner.entityId(),
                    session.getFactory().getMappingMetamodel().getEntityDescriptor(owner.entityName()));
            state.byOwner.computeIfPresent(ownerKey, (ignored, outgoing) -> {
                var pending = outgoing.stream().filter(association -> !association.target().equals(key)).toList();
                return pending.isEmpty() ? null : pending;
            });
        }
        List<FetchOrigin> origins;
        FetchOrigin activeOrigin = currentOrigin();
        if (!state.collectionOrigins.isEmpty()) {
            origins = state.collectionOrigins.peek();
        } else if (activeOrigin != null) {
            origins = List.of(activeOrigin);
        } else if (!attributions.isEmpty()) {
            origins = attributions.stream().map(AssociationAttribution::origin).distinct().toList();
        } else {
            origins = List.of(FetchOrigin.root(null));
        }
        state.origins.put(key, origins);
        var entry = session.getPersistenceContextInternal().getEntry(event.getEntity());
        indexOwner(session, state, entry, origins);
        for (var tracker : trackers) {
            tracker.recordEntityLoad(event.getEntity(), event.getId(), event.getPersister().getEntityName(),
                    state.lazyLoadDepth > 0 ? attributions : List.of());
        }
    }

    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) {
        if (!enabled() || event.getCollection().wasInitialized()) {
            collectionDelegates.forEach(delegate -> delegate.onInitializeCollection(event));
            return;
        }
        var session = event.getSession();
        var state = state(session);
        var collectionEntry = session.getPersistenceContextInternal().getCollectionEntry(event.getCollection());
        var ownerPersister = collectionEntry.getLoadedPersister().getOwnerEntityPersister();
        var ownerKey = session.generateEntityKey(collectionEntry.getLoadedKey(), ownerPersister);
        var owner = reference(ownerKey, event.getAffectedOwnerOrNull());
        String role = event.getCollection().getRole();
        String relation = role.substring(role.lastIndexOf('.') + 1);
        var roots = state.origins.computeIfAbsent(ownerKey, key -> {
            var origin = currentOrigin();
            return List.of(origin != null ? origin : FetchOrigin.root(null));
        });
        var origins = roots.stream().map(root -> root.throughHere(owner, relation)).toList();
        state.collectionOrigins.push(origins);
        try {
            collectionDelegates.forEach(delegate -> delegate.onInitializeCollection(event));
            if (!event.getCollection().wasInitialized()) {
                return;
            }
            // Scan only this completed collection, never the persistence context. Reuse
            // the same cached metadata and outgoing associations as POST_LOAD indexing.
            var members = new LinkedHashMap<EntityKey, List<PendingAssociation>>();
            var edges = new ArrayList<TrackedRelationEdge>();
            var elements = event.getCollection().entries(collectionEntry.getLoadedPersister());
            while (elements.hasNext()) {
                Object element = elements.next();
                var proxy = HibernateProxy.extractLazyInitializer(element);
                if (proxy != null) {
                    element = proxy.getImplementation(session);
                }
                var entry = session.getPersistenceContextInternal().getEntry(element);
                if (entry != null && !members.containsKey(entry.getEntityKey())) {
                    // A managed entity can occur in several collections. Preserve
                    // every source instead of replacing it with the latest group.
                    var previousOrigins = state.origins.getOrDefault(entry.getEntityKey(), List.of());
                    var memberOrigins = Stream.concat(previousOrigins.stream(), origins.stream())
                            .distinct().toList();
                    if (!memberOrigins.equals(previousOrigins)) {
                        state.origins.put(entry.getEntityKey(), memberOrigins);
                        indexOwner(session, state, entry, memberOrigins);
                    }
                    members.put(entry.getEntityKey(), state.byOwner.getOrDefault(entry.getEntityKey(), List.of()));
                    edges.add(new TrackedRelationEdge(owner, relation, reference(entry.getEntityKey(), element)));
                }
            }
            var attributions = origins.stream().map(origin -> new AssociationAttribution(owner, relation, origin)).toList();
            for (var tracker : trackers) {
                tracker.recordCollectionLoad(event.getAffectedOwnerOrNull(), ownerPersister.getEntityName(),
                        collectionEntry.getLoadedKey(), role, attributions, edges);
            }
            var group = new CollectionGroup(role, collectionEntry.getLoadedKey(), Map.copyOf(members), origins);
            collectionObservers.forEach(observer -> observer.accept(session, group));
        } finally {
            state.collectionOrigins.pop();
        }
    }

    private FetchOrigin currentOrigin() {
        return trackers.stream().map(EntityLoadTracker::currentFetchOrigin)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private void indexOwner(EventSource session, SessionState state, EntityEntry entry, List<FetchOrigin> origins) {
        if (entry == null || entry.getLoadedState() == null) {
            return;
        }
        var ownerKey = entry.getEntityKey();
        removeOwner(state, ownerKey);
        var owner = TrackedEntityReference.synthetic(entry.getPersister().getEntityName(), entry.getId());
        var outgoing = new ArrayList<PendingAssociation>();
        for (var attribute : metadata.computeIfAbsent(entry.getPersister(), persister -> {
            var attributes = new ArrayList<AssociationAttribute>();
            for (int i = 0; i < persister.getNumberOfAttributeMappings(); i++) {
                var mapping = persister.getAttributeMapping(i);
                if (mapping instanceof EntityAssociationMapping) {
                    attributes.add(new AssociationAttribute(mapping.getAttributeName(), mapping.getStateArrayPosition()));
                }
            }
            return List.copyOf(attributes);
        })) {
            var target = HibernateProxy.extractLazyInitializer(entry.getLoadedState()[attribute.position()]);
            if (target == null || !target.isUninitialized()) {
                continue;
            }
            var targetKey = session.generateEntityKey(target.getInternalIdentifier(),
                    session.getFactory().getMappingMetamodel().getEntityDescriptor(target.getEntityName()));
            for (var origin : origins) {
                var attribution = new AssociationAttribution(owner, attribute.name(), origin.through(owner, attribute.name()));
                outgoing.add(new PendingAssociation(targetKey, attribution));
                state.byTarget.computeIfAbsent(targetKey, key -> new LinkedHashSet<>()).add(attribution);
            }
        }
        if (!outgoing.isEmpty()) {
            state.byOwner.put(ownerKey, List.copyOf(outgoing));
        }
    }

    private void removeOwner(SessionState state, EntityKey owner) {
        var previous = state.byOwner.remove(owner);
        if (previous != null) {
            for (var association : previous) {
                var candidates = state.byTarget.get(association.target());
                if (candidates != null) {
                    candidates.remove(association.attribution());
                    if (candidates.isEmpty()) {
                        state.byTarget.remove(association.target());
                    }
                }
            }
        }
    }

    private static TrackedEntityReference reference(EntityKey key, Object entity) {
        return entity == null ? TrackedEntityReference.synthetic(key.getEntityName(), key.getIdentifier())
                : TrackedEntityReference.forInstance(key.getEntityName(), key.getIdentifier(), entity);
    }

    private SessionState state(EventSource session) {
        return sessions.computeIfAbsent(session, key -> {
            var state = new SessionState(key);
            key.addEventListeners(state);
            return state;
        });
    }

    private void reset(EventSource session, boolean closed) {
        var state = sessions.get(session);
        if (state != null) {
            state.byTarget.clear();
            state.byOwner.clear();
            state.origins.clear();
            state.collectionOrigins.clear();
        }
        resetObservers.forEach(observer -> observer.accept(session));
        if (closed) {
            sessions.remove(session);
        }
    }

    void clear() {
        sessions.clear();
        metadata.clear();
    }

    int cachedMappingCount() {
        return metadata.size();
    }

    int trackedSessionCount() {
        return sessions.size();
    }

    record PendingAssociation(EntityKey target, AssociationAttribution attribution) {
    }

    record CollectionGroup(String role, Object ownerId, Map<EntityKey, List<PendingAssociation>> members,
                           List<FetchOrigin> origins) {
    }

    record CompletedLoad(EventSource session, EntityKey target, boolean executedSql) {
    }

    private record AssociationAttribute(String name, int position) {
    }

    private final class SessionState implements SessionEventListener {
        private final WeakReference<EventSource> session;
        private final Map<EntityKey, Set<AssociationAttribution>> byTarget = new HashMap<>();
        private final Map<EntityKey, List<PendingAssociation>> byOwner = new HashMap<>();
        private final Map<EntityKey, List<FetchOrigin>> origins = new HashMap<>();
        private final Deque<List<FetchOrigin>> collectionOrigins = new ArrayDeque<>();
        private long executions;
        private int lazyLoadDepth;

        private SessionState(EventSource session) {
            this.session = new WeakReference<>(session);
        }

        @Override
        public void jdbcExecuteStatementEnd() {
            executions++;
        }

        @Override
        public void end() {
            reset(session.get(), true);
        }
    }
}
