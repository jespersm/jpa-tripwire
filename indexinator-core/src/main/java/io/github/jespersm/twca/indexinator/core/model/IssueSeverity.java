package io.github.jespersm.twca.indexinator.core.model;

/**
 * Severity levels for database issues
 */
public enum IssueSeverity {
    /**
     * Critical issues that can cause severe performance problems or data integrity issues
     */
    CRITICAL,

    /**
     * High priority issues that should be addressed soon
     */
    HIGH,

    /**
     * Medium priority issues that may impact performance under load
     */
    MEDIUM,

    /**
     * Low priority issues or suggestions for optimization
     */
    LOW,

    /**
     * Informational findings
     */
    INFO
}
