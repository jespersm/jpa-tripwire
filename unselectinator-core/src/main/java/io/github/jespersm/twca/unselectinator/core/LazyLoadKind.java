package io.github.jespersm.twca.unselectinator.core;

/**
 * Distinguishes lazy entity loads from lazy collection initializations.
 */
public enum LazyLoadKind {
    ENTITY_LOAD,
    COLLECTION_LOAD
}

