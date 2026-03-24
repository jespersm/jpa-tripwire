package io.github.jespersm.twca.unselectinator.core;

/**
 * Describes the explicit fetch operation visible to user code.
 */
public record FetchEndpoint(
        FetchEndpointKind kind,
        String ownerType,
        String methodName,
        SourceLocation callerLocation
) {

    public String signature() {
        return ownerType + "#" + methodName;
    }

    public String displayName() {
        int lastDot = ownerType.lastIndexOf('.');
        String simpleType = lastDot >= 0 ? ownerType.substring(lastDot + 1) : ownerType;
        return simpleType + "#" + methodName;
    }

    @Override
    public String toString() {
        return displayName() + " via " + callerLocation;
    }
}

