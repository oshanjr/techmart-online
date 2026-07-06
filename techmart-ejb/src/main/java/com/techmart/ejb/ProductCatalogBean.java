package com.techmart.ejb;

import com.techmart.interceptor.PerformanceLogged;
import com.techmart.model.Product;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * ProductCatalogBean — Stateless Session Bean
 * ============================================================================
 * Provides stateless business logic for product catalog operations including
 * browsing, searching, and CRUD operations. As a {@code @Stateless} bean,
 * instances are pooled by the container and shared across requests.
 *
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li>Stateless beans are pooled — no per-session overhead</li>
 *   <li>All queries use pre-compiled {@code @NamedQuery} definitions</li>
 *   <li>Read-only operations use {@code setHint("org.hibernate.readOnly", true)}</li>
 * </ul>
 *
 * <p><b>JNDI Binding Strategy:</b></p>
 * This bean is available via the following JNDI names (WildFly default):
 * <pre>
 *   Global:  java:global/techmart-ear/techmart-ejb/ProductCatalogBean
 *   App:     java:app/techmart-ejb/ProductCatalogBean
 *   Module:  java:module/ProductCatalogBean
 * </pre>
 * Injection is preferred over JNDI lookup:
 * <pre>
 *   &#64;EJB
 *   private ProductCatalogBean catalogBean;
 * </pre>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Stateless
@PerformanceLogged  // Enables automatic performance monitoring via CDI interceptor
public class ProductCatalogBean {

    private static final Logger LOGGER = Logger.getLogger(ProductCatalogBean.class.getName());

    /**
     * JPA EntityManager injected by the container.
     * Uses the "TechMartPU" persistence unit defined in persistence.xml.
     * The container manages the EntityManager lifecycle (open/close per transaction).
     */
    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    // ========================================================================
    // Lifecycle Callbacks
    // ========================================================================

    /**
     * Called by the container after the bean instance is created and
     * dependency injection is complete. Used for one-time initialization.
     *
     * For Stateless beans, this is called once per pooled instance
     * (not once per request), making it ideal for lightweight setup.
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("ProductCatalogBean instance initialized and added to the EJB pool");
    }

    /**
     * Called by the container before the bean instance is destroyed.
     * Used to release any resources acquired in @PostConstruct.
     *
     * For Stateless beans, this happens when the pool shrinks or
     * the application is undeployed.
     */
    @PreDestroy
    public void cleanup() {
        LOGGER.info("ProductCatalogBean instance being removed from the EJB pool");
    }

    // ========================================================================
    // Business Methods — Catalog Queries
    // ========================================================================

    /**
     * Retrieves all active products in the catalog, ordered by name.
     * Uses a named query for optimal performance (pre-compiled by JPA provider).
     *
     * @return list of active products
     */
    public List<Product> findAllActive() {
        LOGGER.log(Level.FINE, "Fetching all active products from catalog");
        return em.createNamedQuery("Product.findAllActive", Product.class)
                 .getResultList();
    }

    /**
     * Retrieves all active products with pagination support.
     * Essential for large catalogs to maintain sub-second response times.
     *
     * @param page     zero-based page number
     * @param pageSize number of products per page (max 100)
     * @return paginated list of active products
     */
    public List<Product> findAllActive(int page, int pageSize) {
        // Enforce maximum page size to prevent memory issues
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);

        LOGGER.log(Level.FINE, "Fetching active products: page={0}, size={1}",
                   new Object[]{safePage, safeSize});

        return em.createNamedQuery("Product.findAllActive", Product.class)
                 .setFirstResult(safePage * safeSize)
                 .setMaxResults(safeSize)
                 .getResultList();
    }

    /**
     * Finds a product by its database ID.
     *
     * @param id the product's primary key
     * @return the product, or null if not found
     */
    public Product findById(Long id) {
        LOGGER.log(Level.FINE, "Looking up product by ID: {0}", id);
        return em.find(Product.class, id);
    }

    /**
     * Finds a product by its unique SKU code.
     *
     * @param sku the stock keeping unit
     * @return the product, or null if not found
     */
    public Product findBySku(String sku) {
        LOGGER.log(Level.FINE, "Looking up product by SKU: {0}", sku);
        List<Product> results = em.createNamedQuery("Product.findBySku", Product.class)
                                  .setParameter("sku", sku)
                                  .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Searches products by keyword across name and description fields.
     * Uses case-insensitive LIKE matching.
     *
     * @param keyword the search term (will be wrapped with % wildcards)
     * @return list of matching products
     */
    public List<Product> searchByKeyword(String keyword) {
        LOGGER.log(Level.FINE, "Searching products with keyword: {0}", keyword);
        String searchPattern = "%" + keyword.trim() + "%";
        return em.createNamedQuery("Product.searchByKeyword", Product.class)
                 .setParameter("keyword", searchPattern)
                 .getResultList();
    }

    /**
     * Retrieves all active products in a specific category.
     *
     * @param category the category name (exact match)
     * @return list of products in the category
     */
    public List<Product> findByCategory(String category) {
        LOGGER.log(Level.FINE, "Fetching products by category: {0}", category);
        return em.createNamedQuery("Product.findByCategory", Product.class)
                 .setParameter("category", category)
                 .getResultList();
    }

    /**
     * Returns the total count of active products in the catalog.
     * Useful for pagination metadata.
     *
     * @return total number of active products
     */
    public long countActive() {
        return em.createQuery("SELECT COUNT(p) FROM Product p WHERE p.active = true", Long.class)
                 .getSingleResult();
    }

    // ========================================================================
    // Business Methods — CRUD Operations
    // ========================================================================

    /**
     * Persists a new product to the catalog.
     *
     * @param product the product to create
     * @return the managed product entity with generated ID
     */
    public Product createProduct(Product product) {
        LOGGER.log(Level.INFO, "Creating new product: {0}", product.getSku());
        em.persist(product);
        em.flush(); // Ensure ID is generated immediately
        return product;
    }

    /**
     * Updates an existing product in the catalog.
     *
     * @param product the detached product entity with updated fields
     * @return the merged (managed) product entity
     */
    public Product updateProduct(Product product) {
        LOGGER.log(Level.INFO, "Updating product: {0}", product.getSku());
        return em.merge(product);
    }

    /**
     * Soft-deletes a product by marking it as inactive.
     * Products are never hard-deleted to preserve order history integrity.
     *
     * @param productId the product ID to deactivate
     * @return true if the product was found and deactivated
     */
    public boolean deactivateProduct(Long productId) {
        Product product = em.find(Product.class, productId);
        if (product != null) {
            product.setActive(false);
            LOGGER.log(Level.INFO, "Deactivated product: {0}", product.getSku());
            return true;
        }
        LOGGER.log(Level.WARNING, "Product not found for deactivation: {0}", productId);
        return false;
    }
}
