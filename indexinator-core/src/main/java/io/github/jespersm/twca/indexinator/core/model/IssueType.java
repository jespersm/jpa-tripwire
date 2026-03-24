package io.github.jespersm.twca.indexinator.core.model;

/**
 * Types of database issues that can be detected
 */
public enum IssueType {
    /**
     * Foreign key column without an index
     */
    MISSING_FK_INDEX,

    /**
     * Unique constraint without an index
     */
    MISSING_UNIQUE_INDEX,

    /**
     * Join table in many-to-many relationship without proper indexes
     */
    MISSING_JOIN_TABLE_INDEX,

    /**
     * Table without a primary key
     */
    MISSING_PRIMARY_KEY,

    /**
     * Composite index that could be optimized
     */
    SUBOPTIMAL_COMPOSITE_INDEX,

    /**
     * Index that appears to be unused or redundant
     */
    REDUNDANT_INDEX,

    /**
     * General performance warning
     */
    PERFORMANCE_WARNING,

    /**
     * Repository query method uses column without an index
     */
    MISSING_QUERY_INDEX,

    /**
     * Index declared in JPA metadata is missing from the physical schema
     */
    MISSING_DECLARED_INDEX,

    /**
     * Repository query uses multiple columns that could benefit from a composite index
     */
    POTENTIAL_COMPOSITE_INDEX
}
