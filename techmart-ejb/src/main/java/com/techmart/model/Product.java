package com.techmart.model;

import javax.persistence.*;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * Product Entity
 * ============================================================================
 * Represents a product in the TechMart catalog. Each product has a unique SKU,
 * belongs to a category, and has pricing/availability information.
 *
 * <p><b>Performance Optimizations:</b></p>
 * <ul>
 *   <li>Indexed on {@code sku} for fast unique lookups</li>
 *   <li>Indexed on {@code category} for filtered browsing</li>
 *   <li>Named queries are pre-compiled by the persistence provider</li>
 * </ul>
 *
 * <p><b>JNDI/JPA Notes:</b></p>
 * This entity is managed by the {@code TechMartPU} persistence unit defined
 * in {@code persistence.xml}. It is accessed via {@code EntityManager} injected
 * into session beans using {@code @PersistenceContext}.
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_sku", columnList = "sku", unique = true),
        @Index(name = "idx_product_category", columnList = "category"),
        @Index(name = "idx_product_active", columnList = "active")
    }
)
@NamedQueries({
    /**
     * Retrieves all active products ordered by name.
     * Used by ProductCatalogBean.findAllActive() for catalog display.
     */
    @NamedQuery(
        name = "Product.findAllActive",
        query = "SELECT p FROM Product p WHERE p.active = true ORDER BY p.name"
    ),

    /**
     * Full-text keyword search across product name and description.
     * The LOWER() function ensures case-insensitive matching.
     */
    @NamedQuery(
        name = "Product.searchByKeyword",
        query = "SELECT p FROM Product p WHERE p.active = true " +
                "AND (LOWER(p.name) LIKE LOWER(:keyword) " +
                "OR LOWER(p.description) LIKE LOWER(:keyword)) " +
                "ORDER BY p.name"
    ),

    /**
     * Category-filtered product listing.
     * Used for sidebar navigation in the storefront.
     */
    @NamedQuery(
        name = "Product.findByCategory",
        query = "SELECT p FROM Product p WHERE p.active = true " +
                "AND p.category = :category ORDER BY p.name"
    ),

    /**
     * Single product lookup by SKU.
     * Used for inventory synchronization and order validation.
     */
    @NamedQuery(
        name = "Product.findBySku",
        query = "SELECT p FROM Product p WHERE p.sku = :sku"
    )
})
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // Primary Key
    // ========================================================================

    /**
     * Auto-generated surrogate key using database sequence.
     * Sequence allocation size of 20 reduces round-trips for bulk inserts.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // ========================================================================
    // Business Fields
    // ========================================================================

    /** Stock Keeping Unit — unique business identifier for the product */
    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    /** Human-readable product name displayed in the catalog */
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Detailed product description for the product detail page */
    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    @Column(name = "description", length = 4000)
    private String description;

    /** Product price in the base currency (USD) */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least $0.01")
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Product category for navigation and filtering */
    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Column(name = "category", nullable = false, length = 100)
    private String category;

    /** URL to the product image (stored externally, e.g., CDN) */
    @Column(name = "image_url", length = 512)
    private String imageUrl;

    /** Whether this product is currently available for purchase */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ========================================================================
    // Audit Fields
    // ========================================================================

    /** Timestamp when the product was first created */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the last modification */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========================================================================
    // Optimistic Locking
    // ========================================================================

    /**
     * Version field for optimistic locking.
     * Prevents lost updates when multiple users modify the same product
     * concurrently (e.g., admin price updates during high traffic).
     */
    @Version
    @Column(name = "version")
    private int version;

    // ========================================================================
    // Lifecycle Callbacks
    // ========================================================================

    /**
     * Automatically sets the creation timestamp before the entity is
     * first persisted to the database.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Automatically updates the modification timestamp before each
     * database update operation.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========================================================================
    // Constructors
    // ========================================================================

    /** Default no-arg constructor required by JPA specification */
    public Product() {
    }

    /**
     * Convenience constructor for creating a new product with required fields.
     *
     * @param sku      unique stock keeping unit
     * @param name     product display name
     * @param price    product price
     * @param category product category
     */
    public Product(String sku, String name, BigDecimal price, String category) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    // ========================================================================
    // Object Identity
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return sku != null && sku.equals(product.sku);
    }

    @Override
    public int hashCode() {
        // Use SKU for hashCode as it is the natural business key
        return sku != null ? sku.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Product{" +
               "id=" + id +
               ", sku='" + sku + '\'' +
               ", name='" + name + '\'' +
               ", price=" + price +
               ", category='" + category + '\'' +
               ", active=" + active +
               '}';
    }
}
