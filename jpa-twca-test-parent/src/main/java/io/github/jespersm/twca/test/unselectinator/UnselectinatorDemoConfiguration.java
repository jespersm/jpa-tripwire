package io.github.jespersm.twca.test.unselectinator;

import io.github.jespersm.twca.unselectinator.core.EntityLoadTracker;
import io.github.jespersm.twca.unselectinator.core.ObservedEntityManagerFactory;
import io.github.jespersm.twca.unselectinator.core.Unselectinator;
import io.github.jespersm.twca.unselectinator.hibernate.HibernateUnselectinator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * One-stop demo configuration that wires Unselectinator into Spring Data repositories,
 * direct EntityManager usage, and Hibernate event/SQL observation.
 */
@Configuration
public class UnselectinatorDemoConfiguration {

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
     * Declared static so Spring does not need to instantiate the @Configuration class
     * before all BeanPostProcessors are registered. Uses {@link ObjectProvider} for the
     * {@link EntityLoadTracker} dependency so that Spring resolves it lazily (only when
     * {@code postProcessBeforeInitialization} is actually called), which prevents the
     * "not eligible for auto-proxying" BeanPostProcessorChecker warning.
     */
    @Bean
    public static BeanPostProcessor unselectinatorEntityManagerFactoryPostProcessor(
            ObjectProvider<EntityLoadTracker> trackerProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
                    EntityLoadTracker tracker = trackerProvider.getObject();
                    entityManagerFactoryBean.getJpaPropertyMap().put(
                            AvailableSettings.STATEMENT_INSPECTOR,
                            HibernateUnselectinator.statementInspector(tracker)
                    );
                    entityManagerFactoryBean.getJpaPropertyMap().put(
                            "hibernate.integrator_provider",
                            HibernateUnselectinator.integratorProvider(tracker)
                    );
                }
                return bean;
            }
        };
    }

    @Bean
    public EntityManager observedEntityManager(EntityManagerFactory entityManagerFactory, EntityLoadTracker tracker) {
        EntityManager sharedEntityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return ObservedEntityManagerFactory.wrap(sharedEntityManager, tracker);
    }
}
