package io.github.jespersm.twca.unselectinator.hibernate;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * Registers Hibernate event listeners needed for Unselectinator tracking.
 */
public class HibernateUnselectinatorIntegrator implements Integrator {
    private final HibernateUnselectinatorEventListener listener;

    public HibernateUnselectinatorIntegrator(EntityLoadTracker tracker) {
        this.listener = new HibernateUnselectinatorEventListener(tracker);
    }

    @Override
    public void integrate(Metadata metadata,
                          BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_LOAD, listener);
        registry.appendListeners(EventType.INIT_COLLECTION, listener);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                             SessionFactoryServiceRegistry serviceRegistry) {
        // Nothing to clean up.
    }
}

