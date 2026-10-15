package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

import io.github.jespersm.jpa.tripwire.indexinator.core.model.*;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.fixture.*;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for repository-driven index requirements collected by MetamodelIndexRequirementCollector.
 *
 * Demonstrates how Spring Data repository query methods translate into index requirements.
 * Stories told here:
 *   - Queries filtering on a single field generate one MISSING_QUERY_INDEX requirement per field.
 *   - Queries filtering on multiple fields generate additional POTENTIAL_COMPOSITE_INDEX requirements.
 *   - Queries on the FK side of a ManyToOne navigate through composite foreign keys (order_year,
 *     order_number) and land requirements on *both* join-column fields even though the application
 *     model names a single association attribute ("order").
 *   - Queries that traverse a ManyToMany join-table (product → categories.name) must index the
 *     join-table column referenced on the entity side.
 */
@DisplayName("Repository Query Methods: Index Requirement Detection")
class RepositoryIndexRequirementTest {

    private MetamodelIndexRequirementCollector collector;
    private Metamodel metamodel;

    // -----------------------------------------------------------------------
    // Simulated repository interfaces (no Spring Data on classpath needed)
    // -----------------------------------------------------------------------

    /** Simulated repository for LineItem queries that span composite FK to Order. */
    interface LineItemRepository {}

    /** Simulated repository for Product queries that traverse the ManyToMany join-table. */
    interface ProductRepository {}

    /** Simulated repository for Order queries that use its composite PK columns. */
    interface OrderRepository {}

    /** Simulated repository for ProductReview queries. */
    interface ProductReviewRepository {}

    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        collector = new MetamodelIndexRequirementCollector();
        metamodel = MetamodelBuilder.buildMetamodel(
                Customer.class,
                Order.class,
                Product.class,
                ProductCategory.class,
                ProductReview.class,
                LineItem.class,
                ShippingInfo.class,
                OrderComment.class
        );
    }

    // ===================================================================
    // Single-field query requirements
    // ===================================================================

    @Nested
    @DisplayName("Single-Field Queries: one MISSING_QUERY_INDEX per queried field")
    class SingleFieldQueryRequirements {

        @Test
        @DisplayName("findByProduct generates a single MISSING_QUERY_INDEX on ProductReview.product")
        void singleFieldQueryGeneratesOneRequirement() {
            // Story: a developer writes findByProduct(Product p) on the ProductReview repository.
            // The analyser should flag the product column as needing an index.

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductReviewRepository.class, ProductReview.class, "product_reviews");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByProduct", List.of("product"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(ProductReview.class), List.of(repo));

            IndexRequirement queryReq = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getEntityClass() == ProductReview.class
                            && r.getPropertyPaths().equals(List.of("product")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected MISSING_QUERY_INDEX for ProductReview.product"));

            assertEquals(IssueType.MISSING_QUERY_INDEX, queryReq.getIssueType());
            assertEquals(IssueSeverity.MEDIUM, queryReq.getSeverity());
            assertEquals("ProductReviewRepository.findByProduct", queryReq.getContext());
        }

        @Test
        @DisplayName("findByRating generates a single MISSING_QUERY_INDEX on ProductReview.rating")
        void singleScalarFieldQueryGeneratesOneRequirement() {
            // Story: a developer writes findByRating(int r) to list all reviews with a given score.

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductReviewRepository.class, ProductReview.class, "product_reviews");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByRating", List.of("rating"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(ProductReview.class), List.of(repo));

            long queryReqCount = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getPropertyPaths().equals(List.of("rating")))
                    .count();

            assertEquals(1, queryReqCount, "A single-field query should produce exactly 1 requirement");
        }

        @Test
        @DisplayName("findByOrderYear+findByOrderNumber each generate individual MISSING_QUERY_INDEX requirements")
        void compositePKFieldsEachGenerateOwnRequirement() {
            // Story: the Order search screen allows filtering by year or number independently.

            RepositoryMetadata repo = new RepositoryMetadata(
                    OrderRepository.class, Order.class, "orders");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrderYear", List.of("orderYear"), QueryMethodMetadata.QueryType.FIND));
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrderNumber", List.of("orderNumber"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(Order.class), List.of(repo));

            boolean hasOrderYearReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getPropertyPaths().equals(List.of("orderYear")));
            boolean hasOrderNumberReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getPropertyPaths().equals(List.of("orderNumber")));

            assertTrue(hasOrderYearReq, "Expected MISSING_QUERY_INDEX for Order.orderYear");
            assertTrue(hasOrderNumberReq, "Expected MISSING_QUERY_INDEX for Order.orderNumber");
        }
    }

    // ===================================================================
    // Multi-field queries and composite index hints
    // ===================================================================

    @Nested
    @DisplayName("Multi-Field Queries: individual requirements plus a composite index hint")
    class MultiFieldQueryRequirements {

        @Test
        @DisplayName("findByOrderYearAndOrderNumber generates individual requirements AND a composite hint")
        void twoFieldQueryGeneratesIndividualAndCompositeRequirements() {
            // Story: a developer writes findByOrderYearAndOrderNumber to look up one order precisely.
            // This emits one MISSING_QUERY_INDEX per field *plus* a POTENTIAL_COMPOSITE_INDEX
            // covering both columns together (which is the real index that would satisfy the query).

            RepositoryMetadata repo = new RepositoryMetadata(
                    OrderRepository.class, Order.class, "orders");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrderYearAndOrderNumber",
                    List.of("orderYear", "orderNumber"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(Order.class), List.of(repo));

            // Individual requirements
            boolean hasYearReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("orderYear")));
            boolean hasNumberReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("orderNumber")));

            assertTrue(hasYearReq, "Expected individual MISSING_QUERY_INDEX for orderYear");
            assertTrue(hasNumberReq, "Expected individual MISSING_QUERY_INDEX for orderNumber");

            // Composite hint covers both fields together
            IndexRequirement compositeReq = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.POTENTIAL_COMPOSITE_INDEX)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected POTENTIAL_COMPOSITE_INDEX for (orderYear, orderNumber)"));

            assertEquals(List.of("orderYear", "orderNumber"), compositeReq.getPropertyPaths());
            assertEquals(IssueSeverity.LOW, compositeReq.getSeverity(),
                    "Composite index hints are LOW severity — they are suggestions, not hard requirements");
        }

        @Test
        @DisplayName("findByProductAndRating generates two individual requirements and one composite hint")
        void productAndRatingQueryGeneratesThreeRequirements() {
            // Story: a "find reviews for this product with at least N stars" filter.
            // The natural index is a composite (product_id, rating).

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductReviewRepository.class, ProductReview.class, "product_reviews");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByProductAndRating",
                    List.of("product", "rating"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(ProductReview.class), List.of(repo));

            long individualCount = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX)
                    .count();
            long compositeCount = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.POTENTIAL_COMPOSITE_INDEX)
                    .count();

            assertEquals(2, individualCount, "Two queried fields → two individual requirements");
            assertEquals(1, compositeCount, "Two queried fields → one composite-index hint");
        }
    }

    // ===================================================================
    // Composite FK column queries (LineItem → Order)
    // ===================================================================

    @Nested
    @DisplayName("Composite FK Queries: LineItem → Order (year + orderNumber)")
    class CompositeFKQueryRequirements {

        @Test
        @DisplayName("findByOrder generates MISSING_QUERY_INDEX on the 'order' association attribute")
        void findByOrderAssociationGeneratesRequirement() {
            // Story: a developer writes findByOrder(Order o) to list all line items for a given order.
            // The FK in the database is composite (order_year, order_number), but the application model
            // references the association as a single attribute "order".  The requirement is recorded
            // against that attribute name; the mapping resolver is responsible for expanding it to the
            // actual FK columns when inspecting the schema.

            RepositoryMetadata repo = new RepositoryMetadata(
                    LineItemRepository.class, LineItem.class, "line_items");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrder", List.of("order"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(LineItem.class), List.of(repo));

            IndexRequirement req = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getEntityClass() == LineItem.class
                            && r.getPropertyPaths().equals(List.of("order")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected MISSING_QUERY_INDEX for LineItem.order association"));

            assertEquals(IssueType.MISSING_QUERY_INDEX, req.getIssueType());
            assertEquals("LineItemRepository.findByOrder", req.getContext());
        }

        @Test
        @DisplayName("findByOrderAndProduct generates individual requirements plus a composite hint")
        void findByOrderAndProductGeneratesCompositeHint() {
            // Story: a developer writes findByOrderAndProduct to check whether a product is already in
            // an order (e.g. to prevent duplicate line items).  The natural covering index spans both
            // FK columns.

            RepositoryMetadata repo = new RepositoryMetadata(
                    LineItemRepository.class, LineItem.class, "line_items");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrderAndProduct",
                    List.of("order", "product"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(LineItem.class), List.of(repo));

            boolean hasOrderReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("order")));
            boolean hasProductReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("product")));
            boolean hasCompositeReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.POTENTIAL_COMPOSITE_INDEX
                            && r.getPropertyPaths().equals(List.of("order", "product")));

            assertTrue(hasOrderReq, "Expected MISSING_QUERY_INDEX for LineItem.order");
            assertTrue(hasProductReq, "Expected MISSING_QUERY_INDEX for LineItem.product");
            assertTrue(hasCompositeReq,
                    "Expected POTENTIAL_COMPOSITE_INDEX hint for (order, product) to support "
                    + "duplicate-line-item checks");
        }

        @Test
        @DisplayName("Multiple query methods in one repository each contribute their own requirements")
        void multipleQueryMethodsEachContributeRequirements() {
            // Story: a LineItem repository has three common access patterns and each should produce
            // its own index requirements independently.

            RepositoryMetadata repo = new RepositoryMetadata(
                    LineItemRepository.class, LineItem.class, "line_items");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrder", List.of("order"), QueryMethodMetadata.QueryType.FIND));
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByProduct", List.of("product"), QueryMethodMetadata.QueryType.FIND));
            repo.addQueryMethod(new QueryMethodMetadata(
                    "countByOrder", List.of("order"), QueryMethodMetadata.QueryType.COUNT));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(LineItem.class), List.of(repo));

            long querySourceCount = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY)
                    .count();

            // findByOrder → 1, findByProduct → 1, countByOrder → 1  =  3 total
            assertEquals(3, querySourceCount,
                    "Three single-field query methods should produce three independent requirements");
        }
    }

    // ===================================================================
    // ManyToMany join-table traversal queries (Product ↔ ProductCategory)
    // ===================================================================

    @Nested
    @DisplayName("ManyToMany Traversal Queries: Product ↔ ProductCategory join-table")
    class ManyToManyTraversalQueryRequirements {

        @Test
        @DisplayName("findByCategories generates MISSING_QUERY_INDEX on Product.categories association")
        void findByCategoriesGeneratesRequirement() {
            // Story: a developer writes findByCategories(ProductCategory c) to list all products in a
            // given category.  The join-table lookup needs an index on the category_id side of
            // product_category_mapping; the requirement is recorded against the "categories" attribute.

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductRepository.class, Product.class, "products");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByCategories",
                    List.of("categories"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(Product.class), List.of(repo));

            IndexRequirement req = requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getEntityClass() == Product.class
                            && r.getPropertyPaths().equals(List.of("categories")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected MISSING_QUERY_INDEX for Product.categories (join-table traversal)"));

            assertEquals(IssueType.MISSING_QUERY_INDEX, req.getIssueType());
            assertEquals("ProductRepository.findByCategories", req.getContext());
        }

        @Test
        @DisplayName("findBySkuAndCategories generates per-field requirements and a composite hint")
        void findBySkuAndCategoriesGeneratesCompositeHint() {
            // Story: a catalogue search narrows by a known SKU prefix *within* a category.
            // This needs both a per-SKU index and a composite (sku, category) hint.

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductRepository.class, Product.class, "products");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findBySkuAndCategories",
                    List.of("sku", "categories"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(Product.class), List.of(repo));

            boolean hasSkuReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("sku")));
            boolean hasCategoryReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.MISSING_QUERY_INDEX
                            && r.getPropertyPaths().equals(List.of("categories")));
            boolean hasCompositeReq = requirements.stream()
                    .anyMatch(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY
                            && r.getIssueType() == IssueType.POTENTIAL_COMPOSITE_INDEX
                            && r.getPropertyPaths().equals(List.of("sku", "categories")));

            assertTrue(hasSkuReq, "Expected MISSING_QUERY_INDEX for Product.sku");
            assertTrue(hasCategoryReq, "Expected MISSING_QUERY_INDEX for Product.categories");
            assertTrue(hasCompositeReq,
                    "Expected POTENTIAL_COMPOSITE_INDEX hint for (sku, categories) catalogue search");
        }

        @Test
        @DisplayName("findByCategories does NOT produce a MISSING_FK_INDEX "
                + "— that type is reserved for entity-table associations")
        void findByCategoriesDoesNotProduceFKRequirement() {
            // Story: ManyToMany relationships don't carry an FK column in the entity table itself,
            // so no MISSING_FK_INDEX should appear on Product for the categories attribute.

            RepositoryMetadata repo = new RepositoryMetadata(
                    ProductRepository.class, Product.class, "products");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByCategories", List.of("categories"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(Product.class), List.of(repo));

            boolean hasFKReq = requirements.stream()
                    .anyMatch(r -> r.getEntityClass() == Product.class
                            && r.getPropertyPaths().equals(List.of("categories"))
                            && r.getIssueType() == IssueType.MISSING_FK_INDEX);

            assertFalse(hasFKReq,
                    "ManyToMany join-table queries should not produce MISSING_FK_INDEX on the entity table");
        }
    }

    // ===================================================================
    // Context string correctness
    // ===================================================================

    @Nested
    @DisplayName("Context Strings: requirements reference the repository and method correctly")
    class ContextStringCorrectness {

        @Test
        @DisplayName("Context is '<RepositorySimpleName>.<methodName>' for all query-driven requirements")
        void contextStringFollowsConvention() {
            RepositoryMetadata repo = new RepositoryMetadata(
                    LineItemRepository.class, LineItem.class, "line_items");
            repo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrderAndProduct",
                    List.of("order", "product"),
                    QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel, Set.of(LineItem.class), List.of(repo));

            requirements.stream()
                    .filter(r -> r.getSource() == RequirementSource.SPRING_DATA_QUERY)
                    .forEach(r -> assertEquals(
                            "LineItemRepository.findByOrderAndProduct", r.getContext(),
                            "Context should be '<Repository>.<method>'"));
        }

        @Test
        @DisplayName("Requirements from two different repositories are attributed to their own repository")
        void requirementsFromTwoReposAreAttributedCorrectly() {
            RepositoryMetadata lineItemRepo = new RepositoryMetadata(
                    LineItemRepository.class, LineItem.class, "line_items");
            lineItemRepo.addQueryMethod(new QueryMethodMetadata(
                    "findByOrder", List.of("order"), QueryMethodMetadata.QueryType.FIND));

            RepositoryMetadata productRepo = new RepositoryMetadata(
                    ProductRepository.class, Product.class, "products");
            productRepo.addQueryMethod(new QueryMethodMetadata(
                    "findByCategories", List.of("categories"), QueryMethodMetadata.QueryType.FIND));

            List<IndexRequirement> requirements = collector.collect(
                    metamodel,
                    Set.of(LineItem.class, Product.class),
                    List.of(lineItemRepo, productRepo));

            boolean lineItemCtx = requirements.stream()
                    .anyMatch(r -> "LineItemRepository.findByOrder".equals(r.getContext()));
            boolean productCtx = requirements.stream()
                    .anyMatch(r -> "ProductRepository.findByCategories".equals(r.getContext()));

            assertTrue(lineItemCtx, "LineItem requirement should reference LineItemRepository");
            assertTrue(productCtx, "Product requirement should reference ProductRepository");
        }
    }
}
