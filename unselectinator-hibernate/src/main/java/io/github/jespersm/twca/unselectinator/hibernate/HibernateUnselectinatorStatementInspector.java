package io.github.jespersm.twca.unselectinator.hibernate;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Records every Hibernate SELECT statement in the current observation.
 */
public class HibernateUnselectinatorStatementInspector implements StatementInspector {
    private final EntityLoadTracker tracker;

    public HibernateUnselectinatorStatementInspector(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String inspect(String sql) {
        tracker.recordSelect(sql);
        return sql;
    }
}

