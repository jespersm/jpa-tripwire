package io.github.jespersm.jpa.tripwire.indexinator.hibernate;

import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.IndexRequirement;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.RequirementMappingResolver;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.ResolvedRequirementMapping;
import io.github.jespersm.jpa.tripwire.indexinator.core.requirement.RequirementSource;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.TableDetails;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.type.CollectionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves table and column names from Hibernate SQL mapping metadata.
 */
public class HibernateSqlMappingResolver implements RequirementMappingResolver {

    private final SessionFactoryImplementor sessionFactory;

    /**
     * @param sessionFactory Hibernate session factory used to read mapping metadata
     */
    public HibernateSqlMappingResolver(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Resolve a requirement to physical table/column names via Hibernate persister metadata.
     *
     * @param requirement requirement to resolve
     * @return resolved mapping, or {@code null} when unresolved
     */
    @Override
    public ResolvedRequirementMapping resolve(IndexRequirement requirement) {
        MappingMetamodel mappingMetamodel = sessionFactory.getMappingMetamodel();
        EntityPersister entityPersister = mappingMetamodel.findEntityDescriptor(requirement.getEntityClass());
        if (!(entityPersister instanceof AbstractEntityPersister persister)) {
            return null;
        }

        // With JOINED inheritance the columns of one entity live in several tables, so each column is
        // resolved together with the table that contains it, instead of assuming the root table.
        String tableName = null;
        List<String> columns = new ArrayList<>();

        for (String propertyPath : requirement.getPropertyPaths()) {
            String propertyName = rootProperty(propertyPath);
            List<TableColumn> propertyColumns = new ArrayList<>();

            try {
                if (isIdentifierProperty(persister, propertyName)) {
                    TableDetails tableDetails = persister.getMappedTableDetails();
                    for (TableDetails.KeyColumn keyColumn : tableDetails.getKeyDetails().getKeyColumns()) {
                        propertyColumns.add(new TableColumn(tableDetails.getTableName(), keyColumn.getColumnName()));
                    }
                } else {
                    if (persister.getPropertyType(propertyName) instanceof CollectionType collectionType) {
                        if (requirement.getSource() != RequirementSource.JPA_RELATIONSHIP ||
                                requirement.getPropertyPaths().size() != 1) {
                            // A query through a collection joins via its key columns in another table, which the
                            // relationship and join-table checks cover. Check the remaining properties only.
                            continue;
                        }
                        if (!(mappingMetamodel.findCollectionDescriptor(collectionType.getRole())
                                instanceof AbstractCollectionPersister collectionPersister)) {
                            return null;
                        }
                        return new ResolvedRequirementMapping(stripQuotes(collectionPersister.getTableName()),
                                Arrays.stream(collectionPersister.getKeyColumnNames()).map(this::stripQuotes).toList());
                    }
                    AttributeMapping attributeMapping = persister.findAttributeMapping(propertyName);
                    if (attributeMapping == null) {
                        return null;
                    }
                    attributeMapping.forEachSelectable((index, selectable) -> propertyColumns.add(
                            new TableColumn(selectable.getContainingTableExpression(), selectable.getSelectionExpression())));
                }
            } catch (Exception ex) {
                return null;
            }

            if (propertyColumns.isEmpty()) {
                return null;
            }

            // An index cannot span tables: keep the columns that live in the table of the first property.
            for (TableColumn column : propertyColumns) {
                if (tableName == null) {
                    tableName = column.table();
                }
                if (tableName.equals(column.table())) {
                    columns.add(stripQuotes(column.column()));
                }
            }
        }

        return columns.isEmpty() ? null : new ResolvedRequirementMapping(stripQuotes(tableName), columns);
    }

    private String rootProperty(String propertyPath) {
        int dotIndex = propertyPath.indexOf('.');
        return dotIndex >= 0 ? propertyPath.substring(0, dotIndex) : propertyPath;
    }

    private boolean isIdentifierProperty(AbstractEntityPersister persister, String propertyName) {
        return propertyName.equals(persister.getIdentifierPropertyName()) || "id".equals(propertyName);
    }

    private record TableColumn(String table, String column) {
    }

    private String stripQuotes(String identifier) {
        if (identifier == null) {
            return null;
        }
        return identifier.replace("\"", "");
    }
}

