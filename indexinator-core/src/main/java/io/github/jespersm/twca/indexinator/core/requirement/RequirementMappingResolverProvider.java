package io.github.jespersm.twca.indexinator.core.requirement;

import jakarta.persistence.EntityManagerFactory;

import java.util.List;

/**
 * Classpath-discoverable provider for mapping resolvers.
 */
public interface RequirementMappingResolverProvider {

    /**
     * Provider priority (higher runs first).
     */
    default int priority() {
        return 0;
    }

    /**
     * Whether this provider can work with the supplied entity manager factory.
     */
    boolean supports(EntityManagerFactory entityManagerFactory);

    /**
     * Create resolver instances for this provider.
     */
    List<RequirementMappingResolver> createResolvers(EntityManagerFactory entityManagerFactory);
}

