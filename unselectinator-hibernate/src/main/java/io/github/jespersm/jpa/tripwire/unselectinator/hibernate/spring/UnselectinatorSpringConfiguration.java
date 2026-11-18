package io.github.jespersm.jpa.tripwire.unselectinator.hibernate.spring;

import io.github.jespersm.jpa.tripwire.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.jpa.tripwire.unselectinator.core.ObservedEntityManagerFactory;
import io.github.jespersm.jpa.tripwire.unselectinator.core.Unselectinator;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.HibernateUnselectinator;
import io.github.jespersm.jpa.tripwire.unselectinator.hibernate.CollectionLoadGuard;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import java.util.ArrayList;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.InfrastructureProxy;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.util.ClassUtils;

/**
 * One-stop Spring {@link Configuration} that wires Unselectinator into a Spring Boot /
 * Spring Data JPA application for integration testing.
 *
 * <p>This class is <em>not</em> activated automatically. Import it explicitly from each
 * test that needs Unselectinator observation, for example:
 *
 * <pre>{@code
 * @SpringBootTest(classes = MyApp.class)
 * @Import(UnselectinatorSpringConfiguration.class)
 * class MyIntegrationTest { … }
 * }</pre>
 *
 * <p>or as a shared base-class annotation:
 *
 * <pre>{@code
 * @SpringBootTest(classes = { MyApp.class, UnselectinatorSpringConfiguration.class })
 * abstract class AbstractMyTest { … }
 * }</pre>
 *
 * <p>Requires Spring ORM, Spring AOP, AspectJ weaver, and Hibernate on the classpath
 * (all provided as optional dependencies of {@code unselectinator-hibernate}).
 */
@Configuration
public class UnselectinatorSpringConfiguration {

    /**
     * Static factory for EntityLoadTracker so it is available during the BeanPostProcessor
     * phase without triggering the "not eligible for auto-proxying" warning.
     */
    @Bean
    public static EntityLoadTracker entityLoadTracker() {
        return new EntityLoadTracker();
    }

    @Bean
    public Unselectinator unselectinator(EntityLoadTracker tracker) {
        return new Unselectinator(tracker);
    }

    /**
     * Registers the repository-fetch aspect as an explicit bean rather than relying
     * on component scanning, keeping activation fully under caller control.
     */
    @Bean
    public RepositoryFetchObservationAspect repositoryFetchObservationAspect(EntityLoadTracker tracker) {
        return new RepositoryFetchObservationAspect(tracker);
    }

    /**
     * Declared static so Spring does not need to instantiate the {@code @Configuration}
     * class before all {@link BeanPostProcessor}s are registered. Uses
     * {@link ObjectProvider} for the {@link EntityLoadTracker} dependency so that Spring
     * resolves it lazily, preventing the "not eligible for auto-proxying"
     * {@code BeanPostProcessorChecker} warning.
     */
    @Bean
    public static BeanPostProcessor unselectinatorEntityManagerFactoryPostProcessor(
            ObjectProvider<EntityLoadTracker> trackerProvider, ObjectProvider<CollectionLoadGuard> guards) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof LocalContainerEntityManagerFactoryBean emfb) {
                    EntityLoadTracker tracker = trackerProvider.getObject();
                    var properties = emfb.getJpaPropertyMap();
                    var inspector = HibernateUnselectinator.statementInspector(tracker);
                    Object existingInspector = properties.get(AvailableSettings.STATEMENT_INSPECTOR);
                    if (existingInspector instanceof StatementInspector existing) {
                        properties.put(AvailableSettings.STATEMENT_INSPECTOR, (StatementInspector) sql -> {
                            String inspected = existing.inspect(sql);
                            return inspector.inspect(inspected != null ? inspected : sql);
                        });
                    } else if (existingInspector == null) {
                        properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
                    } else {
                        throw new IllegalArgumentException("Provide an existing StatementInspector as an instance when enabling Unselectinator");
                    }
                    Object existingProvider = properties.get("hibernate.integrator_provider");
                    var integrators = new ArrayList<Integrator>();
                    if (existingProvider instanceof IntegratorProvider provider) {
                        integrators.addAll(provider.getIntegrators());
                    } else if (existingProvider != null) {
                        throw new IllegalArgumentException("Provide an existing IntegratorProvider as an instance when enabling Unselectinator");
                    }
                    integrators.addAll(HibernateUnselectinator.integratorProvider(tracker).getIntegrators());
                    guards.orderedStream().forEach(integrators::add);
                    properties.put("hibernate.integrator_provider", (IntegratorProvider) () -> integrators);
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                // Spring 7 also exposes a shared EntityManager directly as a typed FactoryBean product.
                if (bean instanceof EntityManager entityManager) {
                    return ObservedEntityManagerFactory.wrap(entityManager, trackerProvider.getObject(),
                            ClassUtils.getAllInterfaces(entityManager));
                }
                if (bean instanceof EntityManagerFactory) {
                    ProxyFactory proxyFactory = new ProxyFactory(bean);
                    // Keep Spring's transaction resource key identical to the original factory.
                    proxyFactory.addInterface(InfrastructureProxy.class);
                    proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                        if (invocation.getMethod().getDeclaringClass() == InfrastructureProxy.class) {
                            return bean;
                        }
                        Object result = invocation.proceed();
                        // JpaTransactionManager uses EntityManagerFactoryInfo.createNativeEntityManager,
                        // while shared and application-managed EntityManagers use createEntityManager.
                        String methodName = invocation.getMethod().getName();
                        if (result instanceof EntityManager entityManager
                                && (methodName.equals("createEntityManager")
                                || methodName.equals("createNativeEntityManager"))) {
                            return ObservedEntityManagerFactory.wrap(entityManager, trackerProvider.getObject(),
                                    ClassUtils.getAllInterfaces(entityManager));
                        }
                        return result;
                    });
                    return proxyFactory.getProxy();
                }
                return bean;
            }
        };
    }

    @Bean
    public EntityManager observedEntityManager(EntityManagerFactory entityManagerFactory,
                                               EntityLoadTracker tracker) {
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return ObservedEntityManagerFactory.wrap(shared, tracker);
    }
}
