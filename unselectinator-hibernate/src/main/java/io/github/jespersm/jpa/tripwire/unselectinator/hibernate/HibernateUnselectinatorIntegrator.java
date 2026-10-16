package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
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

    /**
     * @param tracker tracker receiving Hibernate load callbacks
     */
    public HibernateUnselectinatorIntegrator(EntityLoadTracker tracker) {
        this.listener = new HibernateUnselectinatorEventListener(tracker);
    }

    /**
     * Register listeners for entity and collection load events.
     */
    @Override
    public void integrate(Metadata metadata,
                          BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        // Observe lazy association fetches before Hibernate's default load listener performs
        // the database access. POST_LOAD then records the loaded entity instance.
        registry.prependListeners(EventType.LOAD, listener);
        registry.appendListeners(EventType.POST_LOAD, listener);
        registry.appendListeners(EventType.INIT_COLLECTION, listener);
    }

    /**
     * No-op lifecycle hook; listeners are managed by Hibernate for the session factory lifetime.
     */
    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
                             SessionFactoryServiceRegistry serviceRegistry) {
        // Nothing to clean up.
    }
}
