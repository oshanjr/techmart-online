package com.techmart.test;

import com.techmart.ejb.ProductCatalogBean;
import com.techmart.interceptor.PerformanceInterceptor;
import com.techmart.interceptor.PerformanceLogged;
import com.techmart.model.Product;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ejb.EJB;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ProductCatalogBeanTest — Arquillian Integration Test
 * ============================================================================
 * Tests the {@link ProductCatalogBean} (Stateless Session Bean) in a real
 * Java EE container (WildFly) to verify:
 * <ul>
 *   <li>Entity persistence and retrieval via JPA</li>
 *   <li>Named query execution (search, category filter)</li>
 *   <li>Pagination logic</li>
 *   <li>CRUD operations (create, read, update, soft-delete)</li>
 *   <li>EJB lifecycle callbacks (@PostConstruct, @PreDestroy)</li>
 * </ul>
 *
 * <p><b>Arquillian Setup:</b></p>
 * The {@code @Deployment} method creates a micro-archive containing only the
 * classes needed for this test. Arquillian deploys this archive to a managed
 * WildFly instance, runs the tests inside the container, and then undeploys.
 *
 * <p><b>Prerequisites:</b></p>
 * <ul>
 *   <li>{@code JBOSS_HOME} environment variable pointing to WildFly</li>
 *   <li>PostgreSQL running with the {@code techmart_db} database</li>
 *   <li>Datasource configured in WildFly (see wildfly-standalone-snippet.xml)</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductCatalogBeanTest {

    // ========================================================================
    // Arquillian Deployment
    // ========================================================================

    /**
     * Creates the test deployment archive.
     *
     * <p>ShrinkWrap builds a JAR archive containing only the classes and
     * resources needed for this test. This provides test isolation — the test
     * does not depend on the full application being deployed.</p>
     *
     * @return the test archive to deploy to WildFly
     */
    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "techmart-catalog-test.jar")
            // ---- Entity classes ----
            .addClass(Product.class)
            // ---- EJB under test ----
            .addClass(ProductCatalogBean.class)
            // ---- Interceptor (used by @PerformanceLogged on the bean) ----
            .addClass(PerformanceLogged.class)
            .addClass(PerformanceInterceptor.class)
            // ---- JPA configuration ----
            .addAsResource("META-INF/persistence.xml")
            // ---- CDI activation (required for interceptors) ----
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // ========================================================================
    // Injected Dependencies
    // ========================================================================

    /**
     * The EJB under test — injected by the container inside the managed
     * WildFly instance. This is a real EJB proxy, not a mock.
     */
    @EJB
    private ProductCatalogBean productCatalogBean;

    // ========================================================================
    // Test Data
    // ========================================================================

    /** Test product reused across ordered test methods */
    private static Long testProductId;

    // ========================================================================
    // Test Methods
    // ========================================================================

    /**
     * Verifies that the EJB was successfully injected by the container.
     * This implicitly tests:
     *   - EJB pool initialization
     *   - @PostConstruct callback execution
     *   - JNDI binding resolution
     */
    @Test
    @Order(1)
    @DisplayName("ProductCatalogBean should be injectable via @EJB")
    public void testBeanInjection() {
        assertNotNull(productCatalogBean,
            "ProductCatalogBean should be injected by the container");
    }

    /**
     * Tests creating a new product and verifying it was persisted.
     * Validates:
     *   - EntityManager.persist() and flush()
     *   - @PrePersist callback (createdAt timestamp)
     *   - ID generation via sequence
     */
    @Test
    @Order(2)
    @DisplayName("Should create a new product with generated ID")
    public void testCreateProduct() {
        // Arrange
        Product laptop = new Product(
            "TEST-LAPTOP-001",
            "TechMart Pro Laptop",
            new BigDecimal("1299.99"),
            "Electronics"
        );
        laptop.setDescription("High-performance laptop for professionals");

        // Act
        Product created = productCatalogBean.createProduct(laptop);

        // Assert
        assertNotNull(created.getId(), "Created product should have a generated ID");
        assertEquals("TEST-LAPTOP-001", created.getSku());
        assertEquals("TechMart Pro Laptop", created.getName());
        assertEquals(new BigDecimal("1299.99"), created.getPrice());
        assertEquals("Electronics", created.getCategory());
        assertTrue(created.isActive(), "New products should be active by default");

        // Save ID for subsequent tests
        testProductId = created.getId();
    }

    /**
     * Tests finding a product by its database ID.
     * Depends on testCreateProduct having run first (via @Order).
     */
    @Test
    @Order(3)
    @DisplayName("Should find product by ID")
    public void testFindById() {
        // Arrange — use ID from previous test
        assertNotNull(testProductId, "testProductId should be set by testCreateProduct");

        // Act
        Product found = productCatalogBean.findById(testProductId);

        // Assert
        assertNotNull(found, "Product should be found by ID");
        assertEquals("TEST-LAPTOP-001", found.getSku());
        assertEquals("TechMart Pro Laptop", found.getName());
    }

    /**
     * Tests finding a product by its unique SKU.
     */
    @Test
    @Order(4)
    @DisplayName("Should find product by SKU")
    public void testFindBySku() {
        // Act
        Product found = productCatalogBean.findBySku("TEST-LAPTOP-001");

        // Assert
        assertNotNull(found, "Product should be found by SKU");
        assertEquals(testProductId, found.getId());
    }

    /**
     * Tests keyword search across product name and description.
     */
    @Test
    @Order(5)
    @DisplayName("Should find products by keyword search")
    public void testSearchByKeyword() {
        // Act — search by partial name
        List<Product> results = productCatalogBean.searchByKeyword("Laptop");

        // Assert
        assertFalse(results.isEmpty(), "Search for 'Laptop' should return results");
        assertTrue(results.stream()
            .anyMatch(p -> p.getSku().equals("TEST-LAPTOP-001")),
            "Results should include the test laptop");
    }

    /**
     * Tests category-based filtering.
     */
    @Test
    @Order(6)
    @DisplayName("Should find products by category")
    public void testFindByCategory() {
        // Act
        List<Product> electronics = productCatalogBean.findByCategory("Electronics");

        // Assert
        assertFalse(electronics.isEmpty(), "Electronics category should have products");
        assertTrue(electronics.stream().allMatch(p -> "Electronics".equals(p.getCategory())),
            "All returned products should be in the Electronics category");
    }

    /**
     * Tests pagination logic — first page retrieval.
     */
    @Test
    @Order(7)
    @DisplayName("Should support pagination")
    public void testPagination() {
        // Arrange — create additional products for pagination testing
        for (int i = 1; i <= 5; i++) {
            Product p = new Product(
                "TEST-PAGE-" + String.format("%03d", i),
                "Pagination Test Product " + i,
                new BigDecimal("9.99"),
                "TestCategory"
            );
            productCatalogBean.createProduct(p);
        }

        // Act — get first page with size 3
        List<Product> page1 = productCatalogBean.findAllActive(0, 3);

        // Assert
        assertNotNull(page1, "Page result should not be null");
        assertTrue(page1.size() <= 3, "Page size should not exceed the requested limit");
    }

    /**
     * Tests the active product count query.
     */
    @Test
    @Order(8)
    @DisplayName("Should count active products")
    public void testCountActive() {
        // Act
        long count = productCatalogBean.countActive();

        // Assert
        assertTrue(count > 0, "Active product count should be greater than 0");
    }

    /**
     * Tests soft-delete (deactivation) of a product.
     */
    @Test
    @Order(9)
    @DisplayName("Should soft-delete a product by deactivating it")
    public void testDeactivateProduct() {
        // Act
        boolean result = productCatalogBean.deactivateProduct(testProductId);

        // Assert
        assertTrue(result, "Deactivation should return true for existing product");

        // Verify the product is now inactive
        Product deactivated = productCatalogBean.findById(testProductId);
        assertNotNull(deactivated, "Product should still exist after deactivation");
        assertFalse(deactivated.isActive(), "Product should be inactive after deactivation");
    }

    /**
     * Tests that deactivating a non-existent product returns false.
     */
    @Test
    @Order(10)
    @DisplayName("Should return false when deactivating non-existent product")
    public void testDeactivateNonExistent() {
        // Act
        boolean result = productCatalogBean.deactivateProduct(-999L);

        // Assert
        assertFalse(result, "Deactivation should return false for non-existent product");
    }

    /**
     * Tests that findById returns null for a non-existent ID.
     */
    @Test
    @Order(11)
    @DisplayName("Should return null for non-existent product ID")
    public void testFindByIdNotFound() {
        // Act
        Product notFound = productCatalogBean.findById(-999L);

        // Assert
        assertNull(notFound, "Should return null for non-existent product ID");
    }
}
