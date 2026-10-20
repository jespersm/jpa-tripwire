package io.github.jespersm.jpa.tripwire.indexinator.core.requirement;

import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueSeverity;
import io.github.jespersm.jpa.tripwire.indexinator.core.model.IssueType;
import io.github.jespersm.jpa.tripwire.test.model.fixture.*;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for MetamodelIndexRequirementCollector.
 * Tests basic index requirement detection across all major JPA relationship types.
 *
 * Demonstrates the expected index requirements for:
 * - OneToMany relationships (FK-requirement at owning side)
 * - OneToOne relationships (owning side FK-requirement only)
 * - ManyToMany relationships (no FK-requirement, handled by join table)
 * - Unique columns (unique index requirement)
 * - Embedded types (no requirements)
 * - ElementCollection (owner FK-index on collection table)
 */
@DisplayName("MetamodelIndexRequirementCollector: Core Index Requirement Detection")
class MetamodelIndexRequirementCollectorTest {

	private MetamodelIndexRequirementCollector collector;
	private Metamodel metamodel;

	@BeforeEach
	void setUp() {
		collector = new MetamodelIndexRequirementCollector();
		// Build metamodel from all test entities
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

	@Nested
	@DisplayName("OneToMany Relationships: ManyToOne FK-Index Requirements")
	class OneToManyFKRequirements {

		@Test
		@DisplayName("ProductReview → Product (ManyToOne) generates HIGH severity FK-index requirement")
		void manyToOneRelationshipRequiresIndex() {
			// Given: Product has many ProductReview entities (ProductReview.product)
			// When: Collecting requirements from ProductReview

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(ProductReview.class),
					List.of()
			);

			// Then: ManyToOne relationship requires HIGH severity FK-index
			IndexRequirement productFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == ProductReview.class
							&& r.getPropertyPaths().contains("product")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for ProductReview.product"));

			assertEquals(RequirementSource.JPA_RELATIONSHIP, productFkReq.getSource());
			assertEquals(IssueSeverity.HIGH, productFkReq.getSeverity(),
					"ManyToOne relationships should require HIGH severity indexes");
			assertEquals("Relationship attribute 'product'", productFkReq.getContext());
		}

		@Test
		@DisplayName("Order → Customer (ManyToOne) generates HIGH severity FK-index requirement")
		void orderCustomerManyToOneRequiresIndex() {
			// Given: Order has ManyToOne relationship to Customer
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Order.class),
					List.of()
			);

			// Then: Should include FK-index requirement for Order.customer
			IndexRequirement customerFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == Order.class
							&& r.getPropertyPaths().contains("customer")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for Order.customer"));

			assertEquals(IssueSeverity.HIGH, customerFkReq.getSeverity());
		}

		@Test
		@DisplayName("LineItem → Order (ManyToOne) generates HIGH severity FK-index requirement")
		void lineItemOrderManyToOneRequiresIndex() {
			// Given: LineItem has ManyToOne relationship to Order
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(LineItem.class),
					List.of()
			);

			// Then: Should include FK-index requirement for LineItem.order
			IndexRequirement orderFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == LineItem.class
							&& r.getPropertyPaths().contains("order")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for LineItem.order"));

			assertEquals(IssueSeverity.HIGH, orderFkReq.getSeverity());
		}

		@Test
		@DisplayName("LineItem → Product (ManyToOne) generates HIGH severity FK-index requirement")
		void lineItemProductManyToOneRequiresIndex() {
			// Given: LineItem has ManyToOne relationship to Product
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(LineItem.class),
					List.of()
			);

			// Then: Should include FK-index requirement for LineItem.product
			IndexRequirement productFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == LineItem.class
							&& r.getPropertyPaths().contains("product")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for LineItem.product"));

			assertEquals(IssueSeverity.HIGH, productFkReq.getSeverity());
		}

		@Test
		@DisplayName("OrderComment → Order (ManyToOne) generates HIGH severity FK-index requirement")
		void orderCommentOrderManyToOneRequiresIndex() {
			// Given: OrderComment has ManyToOne relationship to Order
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(OrderComment.class),
					List.of()
			);

			// Then: Should include FK-index requirement for OrderComment.order
			IndexRequirement orderFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == OrderComment.class
							&& r.getPropertyPaths().contains("order")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for OrderComment.order"));

			assertEquals(IssueSeverity.HIGH, orderFkReq.getSeverity());
		}
	}

	@Nested
	@DisplayName("OneToOne Relationships: Owning Side FK-Index Requirements")
	class OneToOneFKRequirements {

		@Test
		@DisplayName("Order → ShippingInfo (OneToOne, owning side) generates MEDIUM severity FK-index requirement")
		void oneToOneOwningRequiresIndex() {
			// Given: Order has OneToOne relationship to ShippingInfo (owning side with FK)
			// When: Collecting requirements from Order

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Order.class),
					List.of()
			);

			// Then: OneToOne owning side should require MEDIUM severity FK-index
			IndexRequirement shippingFkReq = requirements.stream()
					.filter(r -> r.getEntityClass() == Order.class
							&& r.getPropertyPaths().contains("shippingInfo")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected FK-index requirement for Order.shippingInfo"));

			assertEquals(RequirementSource.JPA_RELATIONSHIP, shippingFkReq.getSource());
			assertEquals(IssueSeverity.MEDIUM, shippingFkReq.getSeverity(),
					"OneToOne owning side relationships should require MEDIUM severity indexes");
			assertEquals("Relationship attribute 'shippingInfo'", shippingFkReq.getContext());
		}

		@Test
		@DisplayName("ShippingInfo → Order (OneToOne, non-owning side) does NOT generate FK-index requirement")
		void oneToOneNonOwningDoesNotRequireIndex() {
			// Given: ShippingInfo has OneToOne relationship to Order (non-owning side, inverse, mappedBy)
			// When: Collecting requirements from ShippingInfo

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(ShippingInfo.class),
					List.of()
			);

			// Then: Non-owning OneToOne should NOT require any FK-index
			boolean hasFkRequirement = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == ShippingInfo.class
							&& r.getPropertyPaths().contains("order")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertFalse(hasFkRequirement,
					"Non-owning OneToOne relationships (mappedBy) should not require FK-index");
		}
	}

	@Nested
	@DisplayName("ManyToMany Relationships: No FK-Index Requirements")
	class ManyToManyNoFKRequirements {

		@Test
		@DisplayName("Product ↔ ProductCategory (ManyToMany) does NOT generate FK-index requirements")
		void manyToManyDoesNotRequireFK() {
			// Given: Product has ManyToMany relationship with ProductCategory
			// (ManyToMany uses join table, FK indexes are on join table, not entity)
			// When: Collecting requirements from Product

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Product.class),
					List.of()
			);

			// Then: ManyToMany relationships should NOT generate FK-index requirements
			boolean hasManyToManyFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == Product.class
							&& r.getPropertyPaths().contains("categories")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertFalse(hasManyToManyFkReq,
					"ManyToMany relationships should not generate FK-index requirements (handled by join table)");
		}

		@Test
		@DisplayName("ProductCategory ↔ Product (ManyToMany, inverse) does NOT generate FK-index requirements")
		void manyToManyInverseDoesNotRequireFK() {
			// Given: ProductCategory has ManyToMany relationship with Product (inverse side)
			// When: Collecting requirements from ProductCategory

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(ProductCategory.class),
					List.of()
			);

			// Then: ManyToMany (inverse) should NOT generate FK-index requirements
			boolean hasManyToManyFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == ProductCategory.class
							&& r.getPropertyPaths().contains("products")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertFalse(hasManyToManyFkReq,
					"ManyToMany (inverse) relationships should not generate FK-index requirements");
		}
	}

	@Nested
	@DisplayName("Unique Column Requirements")
	class UniqueColumnRequirements {

		@Test
		@DisplayName("Customer.email (unique=true) generates unique index requirement")
		void uniqueColumnGeneratesRequirement() {
			// Given: Customer has unique email column
			// When: Collecting requirements from Customer

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Customer.class),
					List.of()
			);

			// Then: Should include unique index requirement for Customer.email
			IndexRequirement emailUniqueReq = requirements.stream()
					.filter(r -> r.getEntityClass() == Customer.class
							&& r.getPropertyPaths().contains("email")
							&& r.getIssueType() == IssueType.MISSING_UNIQUE_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected unique index requirement for Customer.email"));

			assertEquals(RequirementSource.JPA_UNIQUE_COLUMN, emailUniqueReq.getSource());
			assertEquals(IssueSeverity.MEDIUM, emailUniqueReq.getSeverity());
			assertTrue(emailUniqueReq.isUnique());
		}

		@Test
		@DisplayName("Product.sku (unique=true) generates unique index requirement")
		void productSkuUniqueGeneratesRequirement() {
			// Given: Product has unique SKU column
			// When: Collecting requirements from Product

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Product.class),
					List.of()
			);

			// Then: Should include unique index requirement for Product.sku
			IndexRequirement skuUniqueReq = requirements.stream()
					.filter(r -> r.getEntityClass() == Product.class
							&& r.getPropertyPaths().contains("sku")
							&& r.getIssueType() == IssueType.MISSING_UNIQUE_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected unique index requirement for Product.sku"));

			assertEquals(RequirementSource.JPA_UNIQUE_COLUMN, skuUniqueReq.getSource());
			assertTrue(skuUniqueReq.isUnique());
		}

		@Test
		@DisplayName("ProductCategory.name (unique=true) generates unique index requirement")
		void categoryNameUniqueGeneratesRequirement() {
			// Given: ProductCategory has unique name column
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(ProductCategory.class),
					List.of()
			);

			// Then: Should include unique index requirement for ProductCategory.name
			IndexRequirement nameUniqueReq = requirements.stream()
					.filter(r -> r.getEntityClass() == ProductCategory.class
							&& r.getPropertyPaths().contains("name")
							&& r.getIssueType() == IssueType.MISSING_UNIQUE_INDEX)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected unique index requirement for ProductCategory.name"));

			assertEquals(RequirementSource.JPA_UNIQUE_COLUMN, nameUniqueReq.getSource());
			assertTrue(nameUniqueReq.isUnique());
		}
	}

	@Nested
	@DisplayName("Embedded Types and ElementCollections")
	class EmbeddedAndElementCollectionRequirements {

		@Test
		@DisplayName("Customer.billingAddress (Embedded) does NOT generate FK-index requirement")
		void embeddedAddressDoesNotRequireIndex() {
			// Given: Customer has embedded Address
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Customer.class),
					List.of()
			);

			// Then: Embedded types should NOT generate FK-index requirements
			boolean hasEmbeddedFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == Customer.class
							&& r.getPropertyPaths().contains("billingAddress")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertFalse(hasEmbeddedFkReq,
					"Embedded types should not generate FK-index requirements");
		}

		@Test
		@DisplayName("Customer.shippingAddresses (ElementCollection) requires FK-index on join table")
		void elementCollectionCustomerShippingAddresses() {
			// Given: Customer has ElementCollection of ShippingAddress
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(Customer.class),
					List.of()
			);

			// Then: The owner join column on the collection table needs an index
			boolean hasCollectionFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == Customer.class
							&& r.getPropertyPaths().contains("shippingAddresses")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertTrue(hasCollectionFkReq,
					"ElementCollection requires an index on its collection table owner FK");
		}

		@Test
		@DisplayName("OrderComment.note (Embedded) does NOT generate FK-index requirement")
		void embeddedNoteDoesNotRequireIndex() {
			// Given: OrderComment has embedded Note
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(OrderComment.class),
					List.of()
			);

			// Then: Embedded Note should NOT generate FK-index requirements
			boolean hasEmbeddedFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == OrderComment.class
							&& r.getPropertyPaths().contains("note")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertFalse(hasEmbeddedFkReq,
					"Embedded Note should not generate FK-index requirements");
		}

		@Test
		@DisplayName("OrderComment.tags (ElementCollection) requires FK-index on collection table")
		void elementCollectionTagsRequiresOwnerFKIndex() {
			// Given: OrderComment has ElementCollection of String tags
			// When: Collecting requirements

			List<IndexRequirement> requirements = collector.collect(
					metamodel,
					Set.of(OrderComment.class),
					List.of()
			);

			// Then: The owner join column on the collection table needs an index
			boolean hasCollectionFkReq = requirements.stream()
					.anyMatch(r -> r.getEntityClass() == OrderComment.class
							&& r.getPropertyPaths().contains("tags")
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX);

			assertTrue(hasCollectionFkReq,
					"ElementCollection requires an index on its collection table owner FK");
		}
	}

	@Nested
	@DisplayName("Multi-Entity Collection: Comprehensive Requirement Summary")
	class MultiEntityScenario {

		@Test
		@DisplayName("Complete e-commerce model generates correct total requirements count")
		void completeModelGeneratesExpectedRequirements() {
			// Given: All test entities in the e-commerce model
			// When: Collecting requirements from all entities

			Set<Class<?>> allEntities = Set.of(
					Customer.class,
					Order.class,
					LineItem.class,
					Product.class,
					ProductReview.class,
					ProductCategory.class,
					ShippingInfo.class,
					OrderComment.class
			);

			List<IndexRequirement> allRequirements = collector.collect(
					metamodel,
					allEntities,
					List.of()
			);

			// Then: Should have all relationship and unique column requirements
			// Verify counts by category
			long highSeverityFkCount = allRequirements.stream()
					.filter(r -> r.getSeverity() == IssueSeverity.HIGH)
					.count();
			long oneToOneCount = allRequirements.stream()
					.filter(r -> r.getSeverity() == IssueSeverity.MEDIUM
							&& r.getIssueType() == IssueType.MISSING_FK_INDEX)
					.count();
			long uniqueCount = allRequirements.stream()
					.filter(r -> r.getIssueType() == IssueType.MISSING_UNIQUE_INDEX)
					.count();

			// ManyToOne relationships (HIGH severity):
			// - ProductReview.product
			// - Order.customer
			// - LineItem.order
			// - LineItem.product
			// - OrderComment.order
			// Collection owner FKs: Customer.shippingAddresses and OrderComment.tags
			assertEquals(7, highSeverityFkCount, "Should have 5 ManyToOne and 2 collection owner FK requirements");

			// OneToOne owning side (MEDIUM severity):
			// - Order.shippingInfo
			assertEquals(1, oneToOneCount, "Should have 1 OneToOne owning-side requirement");

			// Unique columns (MEDIUM severity):
			// - Customer.email
			// - Product.sku
			// - ProductCategory.name
			assertEquals(3, uniqueCount, "Should have 3 unique column requirements");

			// Include both collection-table owner FK requirements.
			assertEquals(11, allRequirements.size(),
					"Should have 11 total requirements including 2 ElementCollections");
		}

		@Test
		@DisplayName("All requirements have proper metadata and context information")
		void requirementsHaveCompleteMetadata() {
			// Given: All test entities
			// When: Collecting requirements

			Set<Class<?>> allEntities = Set.of(
					Customer.class,
					Order.class,
					LineItem.class,
					Product.class,
					ProductReview.class,
					ProductCategory.class,
					ShippingInfo.class,
					OrderComment.class
			);

			List<IndexRequirement> allRequirements = collector.collect(
					metamodel,
					allEntities,
					List.of()
			);

			// Then: All requirements should have complete metadata
			for (IndexRequirement req : allRequirements) {
				assertNotNull(req.getEntityClass(), "Requirement should have entity class");
				assertFalse(req.getPropertyPaths().isEmpty(), "Requirement should have property paths");
				assertNotNull(req.getSource(), "Requirement should have source");
				assertNotNull(req.getIssueType(), "Requirement should have issue type");
				assertNotNull(req.getSeverity(), "Requirement should have severity");
				assertNotNull(req.getContext(), "Requirement should have context description");
			}
		}
	}
}
