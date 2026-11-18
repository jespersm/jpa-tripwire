package io.github.jespersm.jpa.tripwire.unselectinator.core;

import java.util.List;

/**
 * Lazy load detected outside an explicit fetch boundary.
 *
 * @param kind lazy load category
 * @param initiatingEndpoint explicit fetch endpoint that previously initiated the traversal
 * @param owner owning entity reference
 * @param relationName relationship field/role name
 * @param loadedEntity lazily loaded entity reference, when available
 * @param selectsSincePreviousLoad number of selects since the previous tracked load event
 * @param attributions candidate owner associations with their original fetch provenance
 * @param triggerLocation application caller that triggered this lazy load, independent of its fetch origins
 */
public record LazySelectEvent(
        LazyLoadKind kind,
        FetchEndpoint initiatingEndpoint,
        TrackedEntityReference owner,
        String relationName,
        TrackedEntityReference loadedEntity,
        int selectsSincePreviousLoad,
        List<AssociationAttribution> attributions,
        SourceLocation triggerLocation
) {
    public LazySelectEvent {
        attributions = List.copyOf(attributions);
    }

    public LazySelectEvent(LazyLoadKind kind, FetchEndpoint initiatingEndpoint, TrackedEntityReference owner,
                           String relationName, TrackedEntityReference loadedEntity, int selectsSincePreviousLoad) {
        this(kind, initiatingEndpoint, owner, relationName, loadedEntity, selectsSincePreviousLoad, List.of(), null);
    }

    public LazySelectEvent(LazyLoadKind kind, FetchEndpoint initiatingEndpoint, TrackedEntityReference owner,
                           String relationName, TrackedEntityReference loadedEntity, int selectsSincePreviousLoad,
                           List<AssociationAttribution> attributions) {
        this(kind, initiatingEndpoint, owner, relationName, loadedEntity, selectsSincePreviousLoad, attributions, null);
    }
}

