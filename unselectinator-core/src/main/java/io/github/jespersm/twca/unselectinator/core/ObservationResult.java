package io.github.jespersm.twca.unselectinator.core;

/**
 * Observed return value together with the lazy-select report captured while computing it.
 */
public record ObservationResult<T>(T value, LazySelectReport report) {
}

