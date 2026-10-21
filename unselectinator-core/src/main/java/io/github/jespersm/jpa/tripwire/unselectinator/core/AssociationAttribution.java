package io.github.jespersm.jpa.tripwire.unselectinator.core;

/** A candidate association responsible for a load; multiple candidates preserve ambiguity. */
public record AssociationAttribution(TrackedEntityReference owner, String relationName, FetchOrigin origin) {
}
