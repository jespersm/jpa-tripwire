package io.github.jespersm.twca.indexinator.core.requirement;

/**
 * Resolves an index requirement from entity/property paths to relational names.
 */
public interface RequirementMappingResolver {
    ResolvedRequirementMapping resolve(IndexRequirement requirement);
}

