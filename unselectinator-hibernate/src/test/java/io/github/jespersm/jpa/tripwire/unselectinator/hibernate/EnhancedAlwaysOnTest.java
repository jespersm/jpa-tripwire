package io.github.jespersm.jpa.tripwire.unselectinator.hibernate;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservedEntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.bytecode.enhance.spi.DefaultEnhancementContext;
import org.hibernate.bytecode.enhance.spi.UnloadedField;
import org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedAlwaysOnTest {
    private static final String PREFIX = EnhancedAlwaysOnTest.class.getPackageName() + ".enhanced.";

    @Test
    void detectsRealBytecodeEnhancedProxyLoads() throws Exception {
        // Use Hibernate's own enhancer, without requiring build-time enhancement of other tests.
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(PREFIX + "EnhancedOwner") && !name.equals(PREFIX + "EnhancedTarget")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                        var enhancer = new BytecodeProviderImpl().getEnhancer(new DefaultEnhancementContext() {
                            @Override
                            public boolean doBiDirectionalAssociationManagement(UnloadedField field) { return false; }
                        });
                        byte[] bytes = enhancer.enhance(name, input.readAllBytes());
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        var originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        var ownerType = loader.loadClass(PREFIX + "EnhancedOwner");
        var targetType = loader.loadClass(PREFIX + "EnhancedTarget");
        var tracker = new EntityLoadTracker();
        var guard = new CollectionLoadGuard(new CollectionLoadGuard.Configuration(0.5, 6, 2));
        var bootstrap = new BootstrapServiceRegistryBuilder().applyClassLoader(loader).applyIntegrator(guard)
                .applyIntegrator(new HibernateUnselectinatorIntegrator(tracker)).build();
        var registry = new StandardServiceRegistryBuilder(bootstrap)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:enhanced_" + UUID.randomUUID())
                .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(ownerType).addAnnotatedClass(targetType)
                .buildMetadata().buildSessionFactory()) {
            try (var em = factory.createEntityManager()) {
                em.getTransaction().begin();
                for (long id = 1; id <= 6; id++) {
                    var owner = ownerType.getConstructor().newInstance();
                    var target = targetType.getConstructor().newInstance();
                    ownerType.getMethod("setId", Long.class).invoke(owner, id);
                    targetType.getMethod("setId", Long.class).invoke(target, id);
                    targetType.getMethod("setLabel", String.class).invoke(target, "target-" + id);
                    ownerType.getMethod("setTarget", targetType).invoke(owner, target);
                    em.persist(owner);
                }
                em.getTransaction().commit();
            }
            try (var em = ObservedEntityManagerFactory.wrap(factory.createEntityManager(), tracker)) {
                var rows = em.createQuery("from EnhancedOwner order by id").getResultList();
                var firstTarget = ownerType.getMethod("getTarget").invoke(rows.get(0));
                assertInstanceOf(PersistentAttributeInterceptable.class, firstTarget);
                assertFalse(Hibernate.isInitialized(firstTarget));
                for (int i = 0; i < 3; i++) {
                    targetType.getMethod("getLabel").invoke(ownerType.getMethod("getTarget").invoke(rows.get(i)));
                }
                var failure = assertThrows(InvocationTargetException.class, () ->
                        targetType.getMethod("getLabel").invoke(ownerType.getMethod("getTarget").invoke(rows.get(3))));
                var threshold = assertInstanceOf(CollectionLoadGuard.ThresholdExceededException.class, failure.getCause());
                assertEquals("target", threshold.violation().association());
                assertEquals(4, threshold.violation().lazyLoadCount());
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
}
