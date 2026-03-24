package io.github.jespersm.twca.unselectinator.hibernate;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;

/**
 * Small helper factory for registering Unselectinator with Hibernate.
 */
public final class HibernateUnselectinator {
    private HibernateUnselectinator() {
    }

    public static StatementInspector statementInspector(EntityLoadTracker tracker) {
        return new HibernateUnselectinatorStatementInspector(tracker);
    }

    public static IntegratorProvider integratorProvider(EntityLoadTracker tracker) {
        return () -> List.<Integrator>of(new HibernateUnselectinatorIntegrator(tracker));
    }
}

