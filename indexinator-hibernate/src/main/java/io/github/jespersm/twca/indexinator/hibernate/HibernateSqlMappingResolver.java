package io.github.jespersm.twca.indexinator.hibernate;

import io.github.jespersm.twca.indexinator.core.requirement.IndexRequirement;
import io.github.jespersm.twca.indexinator.core.requirement.RequirementMappingResolver;
import io.github.jespersm.twca.indexinator.core.requirement.ResolvedRequirementMapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves table and column names from Hibernate SQL mapping metadata.
 */
public class HibernateSqlMappingResolver implements RequirementMappingResolver {

    private final SessionFactoryImplementor sessionFactory;

    public HibernateSqlMappingResolver(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public ResolvedRequirementMapping resolve(IndexRequirement requirement) {
        MappingMetamodel mappingMetamodel = sessionFactory.getMappingMetamodel();
        EntityPersister entityPersister = mappingMetamodel.findEntityDescriptor(requirement.getEntityClass());
        if (!(entityPersister instanceof AbstractEntityPersister persister)) {
            return null;
        }

        String tableName = persister.getRootTableName();
        List<String> columns = new ArrayList<>();

        for (String propertyPath : requirement.getPropertyPaths()) {
            String propertyName = rootProperty(propertyPath);
            String[] propertyColumns;

            try {
                if (isIdentifierProperty(propertyName)) {
                    propertyColumns = persister.getIdentifierColumnNames();
                } else {
                    propertyColumns = persister.getPropertyColumnNames(propertyName);
                }
            } catch (Exception ex) {
                return null;
            }

            if (propertyColumns == null || propertyColumns.length == 0) {
                return null;
            }

            Arrays.stream(propertyColumns)
                    .map(this::stripQuotes)
                    .forEach(columns::add);
        }

        return columns.isEmpty() ? null : new ResolvedRequirementMapping(stripQuotes(tableName), columns);
    }

    private String rootProperty(String propertyPath) {
        int dotIndex = propertyPath.indexOf('.');
        return dotIndex >= 0 ? propertyPath.substring(0, dotIndex) : propertyPath;
    }

    private boolean isIdentifierProperty(String propertyName) {
        return "id".equals(propertyName);
    }

    private String stripQuotes(String identifier) {
        if (identifier == null) {
            return null;
        }
        return identifier.replace("\"", "");
    }
}

