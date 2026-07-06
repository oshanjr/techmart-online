package com.techmart.model;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * Order Entity
 * ============================================================================
 * Represents a customer order in the TechMart system. Orders transition through
 * a well-defined lifecycle:
 *
 * <pre>
 *   PENDING → PROCESSING → SHIPPED → DELIVERED
 *                ↓
 *            CANCELLED
 * </pre>
 *
 * <p><b>Concurrency:</b> Optimistic locking via {@code @Version} prevents
 * conflicting status updates (e.g., shipping an already-cancelled order).</p>
 *
 * <p><b>Cascade:</b> Order items are cascade-persisted with the order to
 * ensure atomic creation (all-or-nothing in a single transaction).</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_customer", columnList = "customer_email"),
        @Index(name = "idx_order_created", columnList = "created_at")
    }
)
@NamedQueries({
    @NamedQuery(
        name = "Order.findByStatus",
        query = "SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC"
    ),
    @NamedQuery(
        name = "Order.findByCustomerEmail",
        query = "SELECT o FROM Order o WHERE o.customerEmail = :email ORDER BY o.createdAt DESC"
    ),
    @NamedQuery(
        name = "Order.findRecentOrders",
        query = "SELECT o FROM Order o ORDER BY o.createdAt DESC"
    )
})
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // Order Status Enum
    // ========================================================================

    /**
     * Represents the lifecycle states of an order.
     * Stored as a string in the database for readability and query flexibility.
     */
    public enum OrderStatus {
        /** Order has been created but not yet processed */
        PENDING,
        /** Order is being fulfilled (inventory reserved, payment captured) */
        PROCESSING,
        /** Order has been dispatched to the customer */
        SHIPPED,
        /** Order has been received by the customer */
        DELIVERED,
        /** Order was cancelled before delivery */
        CANCELLED
    }

    // ========================================================================
    // Primary Key
    // ========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // ========================================================================
    // Business Fields
    // ========================================================================

    /** Customer's email address — used for notifications and order lookup */
    @NotNull(message = "Customer email is required")
    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    /** Customer's display name for the order */
    @Column(name = "customer_name", length = 255)
    private String customerName;

    /** Current status of the order in its lifecycle */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Total order amount (calculated from line items).
     * Stored denormalized for fast reporting and to preserve the exact
     * amount at the time of order placement.
     */
    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Optional shipping address */
    @Column(name = "shipping_address", length = 1000)
    private String shippingAddress;

    // ========================================================================
    // Relationships
    // ========================================================================

    /**
     * Line items in this order.
     * Cascade ALL ensures order items are persisted/removed with the order.
     * OrphanRemoval cleans up items removed from the collection.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    // ========================================================================
    // Audit Fields
    // ========================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Adds a line item to this order and establishes the bidirectional
     * relationship. Also recalculates the order total.
     *
     * @param item the order item to add
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    /**
     * Removes a line item from this order and recalculates the total.
     *
     * @param item the order item to remove
     */
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalculateTotal();
    }

    /**
     * Recalculates the total amount from all line items.
     * Called automatically when items are added or removed.
     */
    public void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========================================================================
    // Constructors
    // ========================================================================

    public Order() {
    }

    public Order(String customerEmail, String customerName) {
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.status = OrderStatus.PENDING;
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

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
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

    @Override
    public String toString() {
        return "Order{" +
               "id=" + id +
               ", customerEmail='" + customerEmail + '\'' +
               ", status=" + status +
               ", totalAmount=" + totalAmount +
               ", itemCount=" + (items != null ? items.size() : 0) +
               '}';
    }
}
