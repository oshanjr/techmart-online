package com.techmart.model;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ============================================================================
 * OrderItem Entity
 * ============================================================================
 * Represents a single line item within an {@link Order}. Each OrderItem
 * captures a snapshot of the product at the time of purchase (price, name)
 * to ensure historical accuracy even if the product is later modified or
 * discontinued.
 *
 * <p><b>Relationship:</b> Many-to-one with {@link Order} (owning side).
 * The order manages the lifecycle via cascade.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {

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

    /** Parent order — this is the owning side of the bidirectional relationship */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @javax.json.bind.annotation.JsonbTransient
    private Order order;

    /** Reference to the product (nullable if product is deleted) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // ========================================================================
    // Snapshot Fields (captured at order time for historical accuracy)
    // ========================================================================

    /** Product SKU at time of purchase */
    @NotNull
    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    /** Product name at time of purchase */
    @NotNull
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** Unit price at time of purchase */
    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Quantity ordered */
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private int quantity;

    // ========================================================================
    // Constructors
    // ========================================================================

    public OrderItem() {
    }

    /**
     * Creates an order item from a product with a specified quantity.
     * Snapshots the product's current price and name.
     *
     * @param product  the product being ordered
     * @param quantity the desired quantity
     */
    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.productSku = product.getSku();
        this.productName = product.getName();
        this.unitPrice = product.getPrice();
        this.quantity = quantity;
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Calculates the subtotal for this line item.
     *
     * @return unitPrice × quantity
     */
    public BigDecimal getSubtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
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

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "OrderItem{" +
               "id=" + id +
               ", productSku='" + productSku + '\'' +
               ", productName='" + productName + '\'' +
               ", unitPrice=" + unitPrice +
               ", quantity=" + quantity +
               ", subtotal=" + getSubtotal() +
               '}';
    }
}
