package io.github.jespersm.twca.unselectinator.core;

/**
 * Factory methods for explicit fetch endpoints with caller capture.
 */
public final class FetchEndpoints {
    private FetchEndpoints() {
    }

    public static FetchEndpoint repositoryMethod(Class<?> repositoryType, String methodName) {
        return new FetchEndpoint(
                FetchEndpointKind.REPOSITORY_METHOD,
                repositoryType.getName(),
                methodName,
                StackFrameCapture.captureUserLocation()
        );
    }

    public static FetchEndpoint entityManagerMethod(String methodName) {
        return new FetchEndpoint(
                FetchEndpointKind.ENTITY_MANAGER,
                "jakarta.persistence.EntityManager",
                methodName,
                StackFrameCapture.captureUserLocation()
        );
    }
}

