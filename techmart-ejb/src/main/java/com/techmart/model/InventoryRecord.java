package com.techmart.model;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * InventoryRecord Entity
 * ============================================================================
 * Tracks the quantity of a specific product at a specific warehouse.
 * This is the core entity for the real-time inventory synchronization feature.
 *
 * <p><b>Composite Uniqueness:</b> Each (product, warehouse) combination
 * must be unique — a product can only have one inventory record per warehouse.
 * This is enforced via {@code @UniqueConstraint}.</p>
 *
 * <p><b>Concurrency:</b> Inventory updates from multiple warehouses arrive
 * concurrently via JMS. Optimistic locking via {@code @Version} ensures
 * conflicting quantity updates are detected and retried.</p>
 *
 * <p><b>JMS Integration:</b> When inventory changes, the
 * {@link com.techmart.jms.InventoryMessageProducer} publishes an event to
 * the {@code InventoryUpdates} JMS Topic. The
 * {@link com.techmart.mdb.InventoryUpdateMDB} consumes these events and
 * updates this entity accordingly.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "inventory_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_inventory_product_warehouse",
            columnNames = {"product_id", "warehouse_id"}
        )
    },
    indexes = {
        @Index(name = "idx_inventory_product", columnList = "product_id"),
        @Index(name = "idx_inventory_warehouse", columnList = "warehouse_id"),
        @Index(name = "idx_inventory_low_stock", columnList = "quantity")
    }
)
@NamedQueries({
    /**
     * Retrieves inventory for a specific product across all warehouses.
     * Used by AppConfigBean to build the aggregated inventory cache.
     */
    @NamedQuery(
        name = "InventoryRecord.findByProduct",
        query = "SELECT ir FROM InventoryRecord ir " +
                "JOIN FETCH ir.warehouse " +
                "WHERE ir.product.id = :productId"
    ),

    /**
     * Retrieves all inventory records for a specific warehouse.
     * Used for warehouse-level reporting and stock audits.
     */
    @NamedQuery(
        name = "InventoryRecord.findByWarehouse",
        query = "SELECT ir FROM InventoryRecord ir " +
                "JOIN FETCH ir.product " +
                "WHERE ir.warehouse.id = :warehouseId"
    ),

    /**
     * Finds the specific inventory record for a product at a warehouse.
     * Used during order processing to reserve stock.
     */
    @NamedQuery(
        name = "InventoryRecord.findByProductAndWarehouse",
        query = "SELECT ir FROM InventoryRecord ir " +
                "WHERE ir.product.id = :productId " +
                "AND ir.warehouse.id = :warehouseId"
    ),

    /**
     * Aggregates total quantity across all warehouses for a product.
     * Used by the Singleton cache to provide real-time total stock levels.
     */
    @NamedQuery(
        name = "InventoryRecord.getTotalQuantityByProduct",
        query = "SELECT SUM(ir.quantity) FROM InventoryRecord ir " +
                "WHERE ir.product.id = :productId"
    ),

    /**
     * Finds products with low stock (below a threshold) at any warehouse.
     * Used for automated reorder alerts.
     */
    @NamedQuery(
        name = "InventoryRecord.findLowStock",
        query = "SELECT ir FROM InventoryRecord ir " +
                "JOIN FETCH ir.product JOIN FETCH ir.warehouse " +
                "WHERE ir.quantity < :threshold " +
                "ORDER BY ir.quantity ASC"
    )
})
public class InventoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // Primary Key
    // ========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // ========================================================================
    // Relationships
    // ========================================================================

    /** The product this inventory record tracks */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** The warehouse where this stock is located */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // ========================================================================
    // Stock Fields
    // ========================================================================

    /** Current quantity in stock at this warehouse */
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(name = "quantity", nullable = false)
    private int quantity = 0;

    /** Minimum threshold for low-stock alerts */
    @Min(value = 0)
    @Column(name = "reorder_threshold")
    private int reorderThreshold = 10;

    // ========================================================================
    // Audit Fields
    // ========================================================================

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    // ========================================================================
    // Optimistic Locking
    // ========================================================================

    @Version
    @Column(name = "version")
    private int version;

    // ========================================================================
    // Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Checks if this inventory record is below the reorder threshold.
     *
     * @return true if quantity is less than the reorder threshold
     */
    public boolean isLowStock() {
        return quantity < reorderThreshold;
    }

    /**
     * Adjusts the quantity by the given delta (positive for additions,
     * negative for deductions).
     *
     * @param delta the amount to add (positive) or subtract (negative)
     * @throws IllegalStateException if the resulting quantity would be negative
     */
    public void adjustQuantity(int delta) {
        int newQuantity = this.quantity + delta;
        if (newQuantity < 0) {
            throw new IllegalStateException(
                String.format("Insufficient stock: current=%d, requested=%d, product=%s, warehouse=%s",
                    quantity, Math.abs(delta),
                    product != null ? product.getSku() : "N/A",
                    warehouse != null ? warehouse.getCode() : "N/A")
            );
        }
        this.quantity = newQuantity;
    }

    // ========================================================================
    // Constructors
    // ========================================================================

    public InventoryRecord() {
    }

    public InventoryRecord(Product product, Warehouse warehouse, int quantity) {
        this.product = product;
        this.warehouse = warehouse;
        this.quantity = quantity;
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "InventoryRecord{" +
               "id=" + id +
               ", productId=" + (product != null ? product.getId() : null) +
               ", warehouseId=" + (warehouse != null ? warehouse.getId() : null) +
               ", quantity=" + quantity +
               ", lowStock=" + isLowStock() +
               '}';
    }
}
