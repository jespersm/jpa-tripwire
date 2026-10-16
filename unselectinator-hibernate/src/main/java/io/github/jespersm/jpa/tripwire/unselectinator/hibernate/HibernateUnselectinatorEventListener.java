package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.hibernate.HibernateException;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;
import org.hibernate.event.spi.LoadEvent;
import org.hibernate.event.spi.LoadEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Objects;

/**
 * Bridges Hibernate load events into the thread-local tracker.
 */
public class HibernateUnselectinatorEventListener implements PostLoadEventListener,
        InitializeCollectionEventListener, LoadEventListener {
    private final EntityLoadTracker tracker;

    /**
     * @param tracker tracker receiving load callbacks
     */
    public HibernateUnselectinatorEventListener(EntityLoadTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Record an entity post-load callback.
     *
     * @param event Hibernate post-load event
     */
    @Override
    public void onPostLoad(PostLoadEvent event) {
        tracker.recordEntityLoad(event.getEntity(), event.getId(), event.getPersister().getEntityName());
    }

    /**
     * Mark lazy association fetches before Hibernate performs the load. The matching post-load
     * callback records the loaded entity in the report.
     *
     * @param event Hibernate load event
     * @param loadType type of entity load being performed
     */
    @Override
    public void onLoad(LoadEvent event, LoadEventListener.LoadType loadType) {
        if (event.isAssociationFetch() && loadType == LoadEventListener.INTERNAL_LOAD_LAZY) {
            AssociationFetch associationFetch = findAssociationFetch(event);
            tracker.recordAssociationFetch(event.getEntityClassName(), event.getEntityId(),
                    associationFetch != null ? associationFetch.owner() : null,
                    associationFetch != null ? associationFetch.relationName() : null);
        }
    }

    private AssociationFetch findAssociationFetch(LoadEvent event) {
        String inferredRelationName = null;
        boolean relationIsAmbiguous = false;
        Iterator<Object> entities = event.getSession().getPersistenceContextInternal().managedEntitiesIterator();
        while (entities.hasNext()) {
            Object owner = entities.next();
            for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!field.isAnnotationPresent(ManyToOne.class) && !field.isAnnotationPresent(OneToOne.class)) {
                        continue;
                    }
                    LazyInitializer initializer = lazyInitializer(event, owner, field);
                    if (initializer != null
                            && Objects.equals(initializer.getIdentifier(), event.getEntityId())
                            && entityMatches(initializer, event.getEntityClassName())) {
                        return new AssociationFetch(owner, field.getName());
                    }
                    if (field.getType().getName().equals(event.getEntityClassName())) {
                        if (inferredRelationName == null) {
                            inferredRelationName = field.getName();
                        } else if (!inferredRelationName.equals(field.getName())) {
                            relationIsAmbiguous = true;
                        }
                    }
                }
            }
        }
        // Hibernate can use bytecode interception instead of a visible proxy for a lazy to-one.
        // In that case the target type still identifies the relation when all managed owners use
        // the same association name; leave the owner unspecified rather than misattributing it.
        return !relationIsAmbiguous && inferredRelationName != null
                ? new AssociationFetch(null, inferredRelationName)
                : null;
    }

    private LazyInitializer lazyInitializer(LoadEvent event, Object owner, Field field) {
        try {
            field.setAccessible(true);
            LazyInitializer initializer = HibernateProxy.extractLazyInitializer(field.get(owner));
            if (initializer != null) {
                return initializer;
            }
            EntityEntry entry = event.getSession().getPersistenceContextInternal().getEntry(owner);
            return entry != null ? HibernateProxy.extractLazyInitializer(entry.getLoadedValue(field.getName())) : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private boolean entityMatches(LazyInitializer initializer, String entityName) {
        return Objects.equals(initializer.getEntityName(), entityName)
                || initializer.getPersistentClass().getName().equals(entityName);
    }

    private record AssociationFetch(Object owner, String relationName) {
    }

    /**
     * Record a collection initialization callback.
     *
     * @param event Hibernate collection initialization event
     * @throws HibernateException propagated from Hibernate listener contract
     */
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
