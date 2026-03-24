package io.github.jespersm.twca.unselectinator.core;

/**
 * A concrete entity or collection load event together with the number of selects since the previous load event.
 */
public record TrackedLoadEvent(
        TrackedEntityReference entity,
        String relationName,
        int selectsSincePreviousLoad,
        boolean explicitFetchLoad
) {
}

