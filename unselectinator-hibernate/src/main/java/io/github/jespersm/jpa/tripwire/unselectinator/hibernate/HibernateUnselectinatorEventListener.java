package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.AssociationAttribution;
import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.FetchOrigin;
import io.github.jespersm.jpa.tripwire.unselectinator.core.TrackedEntityReference;
import io.github.jespersm.jpa.tripwire.unselectinator.core.TrackedRelationEdge;
import org.hibernate.SessionEventListener;
import org.hibernate.Hibernate;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.query.spi.QueryImplementor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
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
    private final List<Runnable> subscriptions = new ArrayList<>();
    private SessionFactoryImplementor factory;
    private final Map<EntityPersister, List<AssociationAttribute>> metadata = new ConcurrentHashMap<>();
    private final Map<EventSource, SessionState> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private final List<BiConsumer<EventSource, CollectionGroup>> collectionObservers = new ArrayList<>();
    private final List<Consumer<CompletedLoad>> loadObservers = new ArrayList<>();
    private final List<Consumer<EventSource>> resetObservers = new ArrayList<>();
    private List<LoadEventListener> loadDelegates = List.of();
    private List<InitializeCollectionEventListener> collectionDelegates = List.of();

    public HibernateUnselectinatorEventListener(EntityLoadTracker tracker) {
        if (tracker != null) {
            attach(tracker);
        }
    }

    /** Install once per factory, regardless of which integration is registered first. */
    static HibernateUnselectinatorEventListener install(SessionFactoryImplementor factory, EntityLoadTracker tracker) {
        var registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
        for (var listener : registry.getEventListenerGroup(EventType.POST_LOAD).listeners()) {
            if (listener instanceof HibernateUnselectinatorEventListener existing) {
                if (tracker != null && !existing.trackers.contains(tracker)) {
                    existing.attach(tracker);
                }
                return existing;
            }
        }
        var listener = new HibernateUnselectinatorEventListener(tracker);
        listener.factory = factory;
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

    private void attach(EntityLoadTracker tracker) {
        trackers.add(tracker);
        subscriptions.add(tracker.onFetchResults(this::enabled, this::fetchResult));
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
        if (loadType == IMMEDIATE_LOAD) {
            drainQueries(event.getSession());
        }
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
        boolean enhancedLazy = !attributions.isEmpty() && state.lazyLoadDepth == 0
                && StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                frame.getClassName().startsWith("org.hibernate.bytecode.enhance.spi.interceptor.")
                        && (frame.getMethodName().equals("forceInitialize") || frame.getMethodName().equals("handleRead"))));
        List<FetchOrigin> origins;
        FetchOrigin activeOrigin = currentOrigin();
        if (!state.collectionOrigins.isEmpty()) {
            origins = state.collectionOrigins.peek();
        } else if (activeOrigin != null) {
            origins = List.of(activeOrigin);
        } else if (!attributions.isEmpty()) {
            origins = attributions.stream().map(AssociationAttribution::origin).distinct().toList();
        } else {
            origins = List.of(state.queries.computeIfAbsent(state.executions,
                    ignored -> new QueryBatch(FetchOrigin.root(null))).origin);

        }
        state.origins.put(key, origins);
        var entry = session.getPersistenceContextInternal().getEntry(event.getEntity());
        indexOwner(session, state, entry, origins);
        if (state.collectionOrigins.isEmpty() && state.lazyLoadDepth == 0 && !enhancedLazy) {
            if (state.explicitQueries.isEmpty()) {
                var batch = state.queries.computeIfAbsent(state.executions, ignored -> new QueryBatch(origins.get(0)));
                batch.members.add(key);
            } else {
                state.provisionalStreamMembers.add(key);
            }
        }
        for (var tracker : trackers) {
            tracker.recordEntityLoad(event.getEntity(), event.getId(), event.getPersister().getEntityName(),
                    state.lazyLoadDepth > 0 || enhancedLazy ? attributions : List.of());
        }
        if (enhancedLazy) {
            var load = new CompletedLoad(session, key, !state.cacheHit);
            loadObservers.forEach(observer -> observer.accept(load));
        }
    }

    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) {
        if (!enabled() || event.getCollection().wasInitialized()) {
            collectionDelegates.forEach(delegate -> delegate.onInitializeCollection(event));
            return;
        }
        var session = event.getSession();
        drainQueries(session);
        var state = state(session);
        long executionsBefore = state.executions;
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
            var key = new CollectionKey(collectionEntry.getLoadedPersister(), collectionEntry.getLoadedKey());
            state.byTarget.remove(key);
            publishCollection(session, event.getCollection(), origins, new LinkedHashSet<>());
            var attributions = origins.stream().map(origin -> new AssociationAttribution(owner, relation, origin)).toList();
            var edges = collectionMembers(session, event.getCollection()).keySet().stream()
                    .map(member -> new TrackedRelationEdge(owner, relation, reference(member, null))).toList();
            for (var tracker : trackers) {
                tracker.recordCollectionLoad(event.getAffectedOwnerOrNull(), ownerPersister.getEntityName(),
                        collectionEntry.getLoadedKey(), role, attributions, edges);
            }
            var load = new CompletedLoad(session, key, state.executions > executionsBefore);
            loadObservers.forEach(observer -> observer.accept(load));
        } finally {
            state.collectionOrigins.pop();
        }
    }

    private FetchOrigin currentOrigin() {
        return trackers.stream().map(EntityLoadTracker::currentFetchOrigin)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    List<AssociationAttribute> attributes(EntityPersister persister) {
        return metadata.computeIfAbsent(persister, descriptor -> {
            var attributes = new ArrayList<AssociationAttribute>();
            for (int i = 0; i < descriptor.getNumberOfAttributeMappings(); i++) {
                var mapping = descriptor.getAttributeMapping(i);
                if (mapping instanceof EntityAssociationMapping || mapping instanceof PluralAttributeMapping) {
                    attributes.add(new AssociationAttribute(mapping.getAttributeName(), mapping.getStateArrayPosition(), mapping));
                }
            }
            return List.copyOf(attributes);
        });
    }

    private Object value(EventSource session, EntityEntry entry, AssociationAttribute attribute) {
        if (entry.getLoadedState() != null) {
            return entry.getLoadedState()[attribute.position()];
        }
        // Read-only entities have no dirty-checking snapshot. Access only properties
        // Hibernate already considers initialized; never force an enhanced lazy field.
        Object entity = session.getPersistenceContextInternal().getEntity(entry.getEntityKey());
        return entity != null && Hibernate.isPropertyInitialized(entity, attribute.name())
                ? attribute.mapping().getValue(entity) : null;
    }

    private void indexOwner(EventSource session, SessionState state, EntityEntry entry, List<FetchOrigin> origins) {
        if (entry == null) {
            return;
        }
        var ownerKey = entry.getEntityKey();
        removeOwner(state, ownerKey);
        var owner = TrackedEntityReference.synthetic(entry.getPersister().getEntityName(), entry.getId());
        var outgoing = new ArrayList<PendingAssociation>();
        for (var attribute : attributes(entry.getPersister())) {
            Object value = value(session, entry, attribute);
            Object targetKey = null;
            var proxy = HibernateProxy.extractLazyInitializer(value);
            if (proxy != null && proxy.isUninitialized()) {
                targetKey = session.generateEntityKey(proxy.getInternalIdentifier(),
                        session.getFactory().getMappingMetamodel().getEntityDescriptor(proxy.getEntityName()));
            } else if (value instanceof PersistentAttributeInterceptable enhanced
                    && enhanced.$$_hibernate_getInterceptor() instanceof EnhancementAsProxyLazinessInterceptor interceptor
                    && !interceptor.isInitialized()) {
                targetKey = interceptor.getEntityKey();
            } else if (value instanceof PersistentCollection<?> collection && !collection.wasInitialized()) {
                var ce = session.getPersistenceContextInternal().getCollectionEntry(collection);
                if (ce != null && ce.getLoadedPersister() != null) {
                    targetKey = new CollectionKey(ce.getLoadedPersister(), ce.getLoadedKey());
                }
            }
            if (targetKey != null) {
                for (var origin : origins) {
                    var attribution = new AssociationAttribution(owner, attribute.name(), origin.through(owner, attribute.name()));
                    outgoing.add(new PendingAssociation(targetKey, attribution));
                    state.byTarget.computeIfAbsent(targetKey, ignored -> new LinkedHashSet<>()).add(attribution);
                }
            }
        }
        if (!outgoing.isEmpty()) {
            state.byOwner.put(ownerKey, List.copyOf(outgoing));
        }
    }

    private Map<EntityKey, List<PendingAssociation>> collectionMembers(EventSource session, PersistentCollection<?> collection) {
        var ce = session.getPersistenceContextInternal().getCollectionEntry(collection);
        var members = new LinkedHashMap<EntityKey, List<PendingAssociation>>();
        var elements = collection.entries(ce.getLoadedPersister());
        while (elements.hasNext()) {
            Object element = elements.next();
            if (element instanceof Map.Entry<?, ?> mapEntry) {
                element = mapEntry.getValue();
            }
            EntityKey key = entityKey(session, element);
            if (key != null) {
                members.putIfAbsent(key, List.of());
            }
        }
        return members;
    }

    private void publishCollection(EventSource session, PersistentCollection<?> collection, List<FetchOrigin> origins,
                                   Set<CollectionKey> visited) {
        var ce = session.getPersistenceContextInternal().getCollectionEntry(collection);
        if (ce == null || !collection.wasInitialized()) {
            return;
        }
        var key = new CollectionKey(ce.getLoadedPersister(), ce.getLoadedKey());
        if (!visited.add(key)) {
            return;
        }
        var members = collectionMembers(session, collection);
        publishGroup(session, key, collection.getRole(), ce.getLoadedKey(), members.keySet(), origins, true, visited);
    }

    private void publishGroup(EventSource session, Object identity, String source, Object ownerId,
                              Set<EntityKey> keys, List<FetchOrigin> origins, boolean complete, Set<CollectionKey> visited) {
        var state = state(session);
        var members = new LinkedHashMap<EntityKey, List<PendingAssociation>>();
        for (var key : keys) {
            var entry = session.getPersistenceContextInternal().getEntry(
                    session.getPersistenceContextInternal().getEntity(key));
            if (entry == null) {
                continue;
            }
            var previous = state.origins.getOrDefault(key, List.of());
            var combined = Stream.concat(previous.stream(), origins.stream()).distinct().toList();
            state.origins.put(key, combined);
            indexOwner(session, state, entry, combined);
            members.put(key, state.byOwner.getOrDefault(key, List.of()));
        }
        var group = new CollectionGroup(identity, source, ownerId, Map.copyOf(members), origins, complete);
        collectionObservers.forEach(observer -> observer.accept(session, group));
        // Fetch joins initialize collections without INIT_COLLECTION. Inspect only
        // mapped collections of this group's members, using a visited set for cycles.
        for (var key : keys) {
            var entry = session.getPersistenceContextInternal().getEntry(session.getPersistenceContextInternal().getEntity(key));
            if (entry == null) {
                continue;
            }
            for (var attribute : attributes(entry.getPersister())) {
                if (attribute.mapping() instanceof PluralAttributeMapping
                        && value(session, entry, attribute) instanceof PersistentCollection<?> collection
                        && collection.wasInitialized()) {
                    var owner = reference(key, null);
                    var childOrigins = origins.stream().map(origin -> origin.through(owner, attribute.name())).toList();
                    publishCollection(session, collection, childOrigins, visited);
                }
            }
        }
    }

    private EntityKey entityKey(EventSource session, Object value) {
        var proxy = HibernateProxy.extractLazyInitializer(value);
        if (proxy != null) {
            value = proxy.getImplementation(session);
        }
        if (value == null) {
            return null;
        }
        var entry = session.getPersistenceContextInternal().getEntry(value);
        return entry != null ? entry.getEntityKey() : null;
    }

    private void resultKeys(EventSource session, Object value, Set<EntityKey> keys) {
        EntityKey key = entityKey(session, value);
        if (key != null) {
            keys.add(key);
        } else if (value instanceof Iterable<?> values) {
            values.forEach(element -> resultKeys(session, element, keys));
        } else if (value instanceof Object[] values) {
            for (Object element : values) {
                resultKeys(session, element, keys);
            }
        } else if (value instanceof Tuple tuple) {
            resultKeys(session, tuple.toArray(), keys);
        } else if (value instanceof Map<?, ?> values) {
            values.values().forEach(element -> resultKeys(session, element, keys));
        }
    }

    private void fetchResult(Object source, Object result, FetchOrigin origin, boolean complete) {
        EventSource session;
        if (source instanceof QueryImplementor<?> query && query.getSession() instanceof EventSource eventSource) {
            session = eventSource;
        } else if (source instanceof EntityManager em && em.unwrap(org.hibernate.Session.class) instanceof EventSource eventSource) {
            session = eventSource;
        } else {
            return;
        }
        if (session.getFactory() != factory || !session.isOpen()) {
            return;
        }
        var state = state(session);
        var keys = state.explicitQueries.computeIfAbsent(origin.fetchId(), ignored -> new LinkedHashSet<>());
        state.queries.values().stream().filter(batch -> batch.origin.fetchId() == origin.fetchId())
                .forEach(batch -> keys.addAll(batch.members));
        state.queries.values().removeIf(batch -> batch.origin.fetchId() == origin.fetchId());
        drainQueries(session);
        resultKeys(session, result, keys);
        // Include already-managed, initialized to-one values in result graphs too.
        // This covers fetch joins/entity graphs even when they generate no POST_LOAD.
        var remaining = new ArrayDeque<>(keys);
        while (!remaining.isEmpty()) {
            var key = remaining.pop();
            var entry = session.getPersistenceContextInternal().getEntry(session.getPersistenceContextInternal().getEntity(key));
            if (entry != null) {
                for (var attribute : attributes(entry.getPersister())) {
                    if (attribute.mapping() instanceof EntityAssociationMapping) {
                        EntityKey target = entityKey(session, value(session, entry, attribute));
                        if (target != null && keys.add(target)) {
                            remaining.add(target);
                        }
                    }
                }
            }
        }
        for (var key : keys) {
            if (state.provisionalStreamMembers.remove(key)) {
                state.origins.remove(key);
            }
        }
        publishQuery(session, origin.fetchId(), keys, origin, complete);
        if (complete) {
            state.explicitQueries.remove(origin.fetchId());
        }
    }

    private void drainQueries(EventSource session) {
        var state = sessions.get(session);
        if (state == null || state.queries.isEmpty() || !state.collectionOrigins.isEmpty() || currentOrigin() != null) {
            return;
        }
        var batches = new LinkedHashMap<>(state.queries);
        state.queries.clear();
        batches.forEach((id, batch) -> publishQuery(session, -id - 1, batch.members, batch.origin, true));
    }

    private void publishQuery(EventSource session, long id, Set<EntityKey> keys, FetchOrigin origin, boolean complete) {
        var types = new LinkedHashMap<String, Set<EntityKey>>();
        for (var key : keys) {
            types.computeIfAbsent(key.getEntityName(), ignored -> new LinkedHashSet<>()).add(key);
        }
        types.forEach((type, members) -> publishGroup(session, new QueryGroupId(id, type),
                "<query:" + type + ">", null, members, List.of(origin), complete, new LinkedHashSet<>()));
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
            state.queries.clear();
            state.explicitQueries.clear();
            state.provisionalStreamMembers.clear();
        }
        resetObservers.forEach(observer -> observer.accept(session));
        if (closed) {
            sessions.remove(session);
        }
    }

    void clear() {
        sessions.clear();
        metadata.clear();
        subscriptions.forEach(Runnable::run);
        subscriptions.clear();
    }

    int cachedMappingCount() {
        return metadata.size();
    }

    int trackedSessionCount() {
        return sessions.size();
    }

    record PendingAssociation(Object target, AssociationAttribution attribution) {
    }

    record CollectionGroup(Object identity, String role, Object ownerId, Map<EntityKey, List<PendingAssociation>> members,
                           List<FetchOrigin> origins, boolean complete) {
    }

    record CompletedLoad(EventSource session, Object target, boolean executedSql) {
    }

    record AssociationAttribute(String name, int position, AttributeMapping mapping) {
    }

    private record QueryGroupId(long invocation, String entityName) {
    }

    private static final class QueryBatch {
        private final FetchOrigin origin;
        private final Set<EntityKey> members = new LinkedHashSet<>();

        private QueryBatch(FetchOrigin origin) {
            this.origin = origin;
        }
    }

    private final class SessionState implements SessionEventListener {
        private final WeakReference<EventSource> session;
        private final Map<Object, Set<AssociationAttribution>> byTarget = new HashMap<>();
        private final Map<EntityKey, List<PendingAssociation>> byOwner = new HashMap<>();
        private final Map<EntityKey, List<FetchOrigin>> origins = new HashMap<>();
        private final Deque<List<FetchOrigin>> collectionOrigins = new ArrayDeque<>();
        private final Map<Long, QueryBatch> queries = new LinkedHashMap<>();
        private final Map<Long, Set<EntityKey>> explicitQueries = new HashMap<>();
        private final Set<EntityKey> provisionalStreamMembers = new LinkedHashSet<>();
        private boolean cacheHit;
        private long executions;
        private int lazyLoadDepth;

        private SessionState(EventSource session) {
            this.session = new WeakReference<>(session);
        }

        @Override
        public void jdbcExecuteStatementEnd() {
            executions++;
            cacheHit = false;
        }

        @Override
        public void cacheGetEnd(boolean hit) {
            cacheHit = hit;
        }

        @Override
        public void end() {
            reset(session.get(), true);
        }
    }
}
