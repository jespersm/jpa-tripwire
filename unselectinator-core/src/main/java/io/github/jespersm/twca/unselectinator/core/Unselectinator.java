package io.github.jespersm.twca.unselectinator.core;

import java.util.function.Supplier;

/**
 * Small test-facing facade for running code while capturing lazy select activity.
 */
public class Unselectinator {
    private final EntityLoadTracker tracker;

    public Unselectinator(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    public LazySelectReport observe(Runnable runnable) {
        tracker.startObservation();
        try {
            runnable.run();
            return tracker.finishObservation();
        } catch (RuntimeException ex) {
            tracker.finishObservation();
            throw ex;
        }
    }

    public <T> ObservationResult<T> observe(Supplier<T> supplier) {
        tracker.startObservation();
        try {
            T value = supplier.get();
            return new ObservationResult<>(value, tracker.finishObservation());
        } catch (RuntimeException ex) {
            tracker.finishObservation();
            throw ex;
        }
    }

    public EntityLoadTracker getTracker() {
        return tracker;
    }
}

