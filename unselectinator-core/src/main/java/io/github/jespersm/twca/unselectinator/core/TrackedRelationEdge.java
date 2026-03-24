package io.github.jespersm.twca.unselectinator.core;

/**
 * Best-effort entity relationship edge observed during lazy loading.
 */
public record TrackedRelationEdge(
        TrackedEntityReference owner,
        String relationName,
        TrackedEntityReference target
) {
}

