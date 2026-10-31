package io.github.jespersm.jpa.tripwire.unselectinator.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable fetch provenance. Association steps share their parent rather than copying
 * an entire traversal path. A missing endpoint means that no explicit API boundary was observed.
 *
 * @param fetchId identity of this fetch invocation, shared by its traversal steps
 * @param endpoint explicit root fetch, when instrumented
 * @param location known source location, inherited by indexed to-one steps rather than guessing an access site
 * @param parent preceding fetch step, or null for a root
 * @param owner association owner for this step, or null for a root
 * @param relationName association traversed in this step, or null for a root
 */
public record FetchOrigin(long fetchId, FetchEndpoint endpoint, SourceLocation location, FetchOrigin parent,
                          TrackedEntityReference owner, String relationName) {
    private static final AtomicLong NEXT_FETCH_ID = new AtomicLong();

    public static FetchOrigin root(FetchEndpoint endpoint) {
        return new FetchOrigin(NEXT_FETCH_ID.incrementAndGet(), endpoint, endpoint != null ? endpoint.callerLocation()
                : StackFrameCapture.captureUserLocation(), null, null, null);
    }

    public FetchOrigin through(TrackedEntityReference owner, String relationName) {
        return new FetchOrigin(fetchId, null, location, this, owner, relationName);
    }

    /** Capture an actual collection access once; indexing its members reuses this location. */
    public FetchOrigin throughHere(TrackedEntityReference owner, String relationName) {
        return new FetchOrigin(fetchId, null, StackFrameCapture.captureUserLocation(), this, owner, relationName);
    }

    public FetchEndpoint initiatingEndpoint() {
        return endpoint != null ? endpoint : parent != null ? parent.initiatingEndpoint() : null;
    }
}
