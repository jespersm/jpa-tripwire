package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

import io.github.jespersm.jpa.tripwire.indexinator.core.analyzer.EntityAnalyzer;
import io.github.jespersm.jpa.tripwire.indexinator.core.detector.IssueDetector;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.*;
import io.github.jespersm.jpa.tripwire.test.model.fixture.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.persistence.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ElementCollectionIndexTest {
    record CollectionCase(Class<?> entity, String property, String ownerTable, String table, String column) {}

    static Stream<CollectionCase> collections() {
        return Stream.of(
                new CollectionCase(OrderComment.class, "tags", "order_comments", "comment_tags", "comment_id"),
                new CollectionCase(Customer.class, "shippingAddresses", "customers", "customer_shipping_addresses", "customer_id"));
    }

    @Entity(name = "Owner")
    static class DefaultCollections {
        @Id @Column(name = "owner_key") long id;
        @ElementCollection List<String> aliases;
        @ElementCollection @CollectionTable(joinColumns = @JoinColumn) List<String> labels;
    }

    static Stream<CollectionCase> defaultCollections() {
        return Stream.of(
                new CollectionCase(DefaultCollections.class, "aliases", "", "Owner_aliases", "Owner_owner_key"),
                new CollectionCase(DefaultCollections.class, "labels", "", "Owner_labels", "Owner_owner_key"));
    }

    @ParameterizedTest
    @MethodSource("defaultCollections")
    void resolvesDefaultCollectionNames(CollectionCase collection) {
        assertEquals(new ResolvedRequirementMapping(collection.table(), List.of(collection.column())),
                new ReflectionFallbackMappingResolver().resolve(requirement(collection)));
    }

    private IndexRequirement requirement(CollectionCase collection) {
        return IndexRequirement.forProperties(collection.entity(), List.of(collection.property()), false,
                RequirementSource.JPA_RELATIONSHIP, IssueType.MISSING_FK_INDEX, IssueSeverity.HIGH,
                "Collection owner FK");
    }

    @ParameterizedTest
    @MethodSource("collections")
    void resolvesOwnerJoinColumnRatherThanElementColumn(CollectionCase collection) {
        assertEquals(new ResolvedRequirementMapping(collection.table(), List.of(collection.column())),
                new ReflectionFallbackMappingResolver().resolve(requirement(collection)));
    }

    @ParameterizedTest
    @MethodSource("collections")
    void detectsMissingCollectionIndexWithAndWithoutMetamodel(CollectionCase collection) {
        TableSchema owner = new TableSchema(collection.ownerTable());
        owner.addPrimaryKeyColumn("id");
        TableSchema table = new TableSchema(collection.table());
        // No database FK is needed: the requirement comes from the JPA mapping.
        Map<String, TableSchema> schemas = Map.of(collection.ownerTable().toUpperCase(), owner,
                collection.table().toUpperCase(), table);
        List<Issue> issues = new RequirementIssueDetector().detectIssues(List.of(requirement(collection)), schemas);
        assertCollectionIssue(collection, issues);
        assertCollectionIssue(collection, new IssueDetector().detectIssues(
                List.of(new EntityAnalyzer().analyzeEntity(collection.entity())), schemas));

        IndexInfo index = new IndexInfo("collection_owner_idx", collection.table(), false);
        index.addColumn(collection.column().toUpperCase());
        index.addColumn("element_value");
        table.addIndex(index);
        assertTrue(new RequirementIssueDetector().detectIssues(List.of(requirement(collection)), schemas).isEmpty());
        assertFalse(new IssueDetector().detectIssues(
                List.of(new EntityAnalyzer().analyzeEntity(collection.entity())), schemas).stream()
                .anyMatch(issue -> issue.tableName().equalsIgnoreCase(collection.table())));
    }

    @ParameterizedTest
    @MethodSource("collections")
    void trailingOwnerColumnDoesNotCoverCollectionLookup(CollectionCase collection) {
        TableSchema table = new TableSchema(collection.table());
        IndexInfo index = new IndexInfo("value_first_idx", collection.table(), false);
        index.addColumn("element_value");
        index.addColumn(collection.column());
        table.addIndex(index);
        assertCollectionIssue(collection, new RequirementIssueDetector().detectIssues(
                List.of(requirement(collection)), Map.of(collection.table().toUpperCase(), table)));
    }

    private void assertCollectionIssue(CollectionCase collection, List<Issue> issues) {
        List<Issue> collectionIssues = issues.stream()
                .filter(issue -> issue.tableName().equalsIgnoreCase(collection.table())).toList();
        assertEquals(1, collectionIssues.size());
        Issue issue = collectionIssues.get(0);
        assertEquals(IssueType.MISSING_FK_INDEX, issue.type());
        assertEquals(collection.column(), issue.columnName());
        assertTrue(issue.recommendation().contains("ON " + collection.table() + "(" + collection.column() + ")"));
    }
}
