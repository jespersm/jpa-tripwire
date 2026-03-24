package io.github.jespersm.twca.indexinator.core.requirement;

import java.util.List;

/**
 * Resolved relational mapping for an entity/property index requirement.
 */
public record ResolvedRequirementMapping(String tableName, List<String> columnNames) {
}

