package io.github.jespersm.twca.unselectinator.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-local tracker for explicit fetch scopes, select statements, and lazy loads.
 */
public class EntityLoadTracker {
    private static final Logger logger = LoggerFactory.getLogger(EntityLoadTracker.class);

    private final ThreadLocal<TrackerState> state = ThreadLocal.withInitial(TrackerState::new);

    public void startObservation() {
        state.set(new TrackerState(true));
    }

    public LazySelectReport finishObservation() {
        TrackerState trackerState = state.get();
        try {
            return trackerState.snapshot();
        } finally {
            state.remove();
        }
    }

    public void beginExplicitFetch(FetchEndpoint endpoint) {
        TrackerState trackerState = state.get();
        if (!trackerState.observing) {
            return;
        }
        trackerState.explicitFetchScopes.push(endpoint);
        trackerState.lastInitiatingEndpoint = trackerState.currentInitiatingEndpoint();
    }

    public void endExplicitFetch() {
        TrackerState trackerState = state.get();
        if (!trackerState.observing || trackerState.explicitFetchScopes.isEmpty()) {
            return;
        }
        trackerState.explicitFetchScopes.pop();
    }

    public void recordSelect(String sql) {
        TrackerState trackerState = state.get();
        if (!trackerState.observing || !isSelect(sql)) {
            return;
        }
        trackerState.totalSelectCount++;
    }

    public void recordEntityLoad(Object entity, Object entityId, String entityName) {
        TrackerState trackerState = state.get();
        if (!trackerState.observing || entity == null) {
            return;
        }

        TrackedEntityReference reference = TrackedEntityReference.forInstance(entityName, entityId, entity);
        trackerState.referencesByInstance.put(entity, reference);

        int selectsSincePreviousLoad = trackerState.totalSelectCount - trackerState.selectCountAtPreviousLoad;
        boolean explicitFetchLoad = !trackerState.explicitFetchScopes.isEmpty();
        trackerState.loadEvents.add(new TrackedLoadEvent(reference, null, selectsSincePreviousLoad, explicitFetchLoad));
        trackerState.selectCountAtPreviousLoad = trackerState.totalSelectCount;
    }

    public void recordCollectionLoad(Object owner, String ownerEntityName, Object ownerId, String role) {
        TrackerState trackerState = state.get();
        if (!trackerState.observing || role == null) {
            return;
        }

        TrackedEntityReference ownerReference = owner != null
                ? trackerState.referencesByInstance.computeIfAbsent(owner,
                key -> TrackedEntityReference.forInstance(owner.getClass().getName(), ownerId, owner))
                : TrackedEntityReference.synthetic(ownerEntityName != null ? ownerEntityName : "<unknown-owner>", ownerId);

        String relationName = relationName(role);
        int selectsSincePreviousLoad = trackerState.totalSelectCount - trackerState.selectCountAtPreviousLoad;
        boolean explicitFetchLoad = !trackerState.explicitFetchScopes.isEmpty();
        trackerState.loadEvents.add(new TrackedLoadEvent(ownerReference, relationName, selectsSincePreviousLoad, explicitFetchLoad));
        trackerState.selectCountAtPreviousLoad = trackerState.totalSelectCount;

        captureRelationEdges(trackerState, owner, ownerReference, relationName);

        if (!explicitFetchLoad && trackerState.lastInitiatingEndpoint != null) {
            LazySelectEvent event = new LazySelectEvent(
                    LazyLoadKind.COLLECTION_LOAD,
                    trackerState.lastInitiatingEndpoint,
                    ownerReference,
                    relationName,
                    null,
                    selectsSincePreviousLoad
            );
            trackerState.lazySelectEvents.add(event);
            logger.warn("Lazy collection load detected: {}.{} triggered after explicit fetch {} ({} select(s) since previous load)",
                    ownerReference.displayName(),
                    relationName,
                    trackerState.lastInitiatingEndpoint.displayName(),
                    selectsSincePreviousLoad);
        }
    }

    private void captureRelationEdges(TrackerState trackerState,
                                      Object owner,
                                      TrackedEntityReference ownerReference,
                                      String relationName) {
        if (owner == null) {
            return;
        }

        Object relationValue = readRelationValue(owner, relationName);
        if (relationValue == null) {
            return;
        }

        if (relationValue instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                addRelationEdge(trackerState, ownerReference, relationName, element);
            }
            return;
        }

        if (relationValue.getClass().isArray()) {
            int length = Array.getLength(relationValue);
            for (int index = 0; index < length; index++) {
                addRelationEdge(trackerState, ownerReference, relationName, Array.get(relationValue, index));
            }
            return;
        }

        addRelationEdge(trackerState, ownerReference, relationName, relationValue);
    }

    private void addRelationEdge(TrackerState trackerState,
                                 TrackedEntityReference ownerReference,
                                 String relationName,
                                 Object element) {
        if (element == null) {
            return;
        }
        TrackedEntityReference target = trackerState.referencesByInstance.get(element);
        if (target == null) {
            target = TrackedEntityReference.forInstance(element.getClass().getName(), null, element);
        }
        trackerState.relationEdges.add(new TrackedRelationEdge(ownerReference, relationName, target));
    }

    private Object readRelationValue(Object owner, String relationName) {
        String suffix = Character.toUpperCase(relationName.charAt(0)) + relationName.substring(1);
        for (String candidate : List.of("get" + suffix, "is" + suffix)) {
            try {
                Method method = owner.getClass().getMethod(candidate);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (NoSuchMethodException ignored) {
                // Try fields next.
            } catch (Exception ex) {
                return null;
            }
        }

        Class<?> current = owner.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(relationName);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private String relationName(String role) {
        int dotIndex = role.lastIndexOf('.');
        return dotIndex >= 0 ? role.substring(dotIndex + 1) : role;
    }

    private boolean isSelect(String sql) {
        if (sql == null) {
            return false;
        }

        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/*")) {
            int commentEnd = normalized.indexOf("*/");
            if (commentEnd < 0) {
                return false;
            }
            normalized = normalized.substring(commentEnd + 2).stripLeading();
        }
        return normalized.startsWith("select") || normalized.startsWith("with");
    }

    private static final class TrackerState {
        private final boolean observing;
        private final Deque<FetchEndpoint> explicitFetchScopes = new ArrayDeque<>();
        private final Map<Object, TrackedEntityReference> referencesByInstance = new IdentityHashMap<>();
        private final List<TrackedLoadEvent> loadEvents = new ArrayList<>();
        private final List<LazySelectEvent> lazySelectEvents = new ArrayList<>();
        private final List<TrackedRelationEdge> relationEdges = new ArrayList<>();

        private int totalSelectCount;
        private int selectCountAtPreviousLoad;
        private FetchEndpoint lastInitiatingEndpoint;

        private TrackerState() {
            this(false);
        }

        private TrackerState(boolean observing) {
            this.observing = observing;
        }

        private FetchEndpoint currentInitiatingEndpoint() {
            FetchEndpoint repositoryEndpoint = null;
            for (FetchEndpoint endpoint : explicitFetchScopes) {
                if (endpoint.kind() == FetchEndpointKind.REPOSITORY_METHOD) {
                    repositoryEndpoint = endpoint;
                }
            }
            return repositoryEndpoint != null ? repositoryEndpoint : explicitFetchScopes.peekLast();
        }

        private LazySelectReport snapshot() {
            return new LazySelectReport(totalSelectCount, loadEvents, lazySelectEvents, relationEdges);
        }
    }
}


