package io.github.jespersm.twca.indexinator.hibernate;

import io.github.jespersm.twca.indexinator.core.requirement.RequirementMappingResolver;
import io.github.jespersm.twca.indexinator.core.requirement.RequirementMappingResolverProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.util.List;

/**
 * ServiceLoader provider that contributes Hibernate mapping resolvers.
 */
public class HibernateRequirementMappingResolverProvider implements RequirementMappingResolverProvider {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(EntityManagerFactory entityManagerFactory) {
        try {
            return entityManagerFactory.unwrap(SessionFactoryImplementor.class) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public List<RequirementMappingResolver> createResolvers(EntityManagerFactory entityManagerFactory) {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        return List.of(new HibernateSqlMappingResolver(sessionFactory));
    }
}

