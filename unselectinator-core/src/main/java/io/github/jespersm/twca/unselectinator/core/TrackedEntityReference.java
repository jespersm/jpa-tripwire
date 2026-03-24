package io.github.jespersm.twca.unselectinator.core;

/**
 * Reference to a loaded entity instance (or synthetic owner reference when no Java object is available).
 */
public record TrackedEntityReference(String entityName, Object entityId, int identityHash) {

    public static TrackedEntityReference forInstance(String entityName, Object entityId, Object entity) {
        return new TrackedEntityReference(entityName, entityId, System.identityHashCode(entity));
    }

    public static TrackedEntityReference synthetic(String entityName, Object entityId) {
        return new TrackedEntityReference(entityName, entityId, -1);
    }

    public String displayName() {
        int lastDot = entityName.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? entityName.substring(lastDot + 1) : entityName;
        if (entityId == null) {
            return simpleName;
        }
        return simpleName + "#" + entityId;
    }

    @Override
    public String toString() {
        return displayName() + (identityHash >= 0 ? "@" + Integer.toHexString(identityHash) : "");
    }
}
