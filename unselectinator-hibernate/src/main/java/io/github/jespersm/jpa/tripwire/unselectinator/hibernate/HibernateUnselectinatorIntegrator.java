package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

/**
 * Registers Hibernate event listeners needed for Unselectinator tracking.
 */
public class HibernateUnselectinatorIntegrator implements Integrator {
    private final EntityLoadTracker tracker;
    private HibernateUnselectinatorEventListener listener;

    /**
     * @param tracker tracker receiving Hibernate load callbacks
     */
    public HibernateUnselectinatorIntegrator(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Register listeners for entity and collection load events.
     */
    @Override
    public void integrate(Metadata metadata,
                          BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        listener = HibernateUnselectinatorEventListener.install(sessionFactory, tracker);
    }

    /**
     * Release factory-scoped metadata and session index data.
     */
    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                             SessionFactoryServiceRegistry serviceRegistry) {
        listener.clear();
    }
}
