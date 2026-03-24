package io.github.jespersm.twca.unselectinator.hibernate;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import org.hibernate.HibernateException;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;

/**
 * Bridges Hibernate load events into the thread-local tracker.
 */
public class HibernateUnselectinatorEventListener implements PostLoadEventListener, InitializeCollectionEventListener {
    private final EntityLoadTracker tracker;

    public HibernateUnselectinatorEventListener(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void onPostLoad(PostLoadEvent event) {
        tracker.recordEntityLoad(event.getEntity(), event.getId(), event.getPersister().getEntityName());
    }

    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) throws HibernateException {
        tracker.recordCollectionLoad(
                event.getAffectedOwnerOrNull(),
                event.getAffectedOwnerEntityName(),
                event.getAffectedOwnerIdOrNull(),
                event.getCollection().getRole()
        );
    }
}

