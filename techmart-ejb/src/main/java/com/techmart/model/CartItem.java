package com.techmart.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ============================================================================
 * CartItem — Shopping Cart Line Item (Value Object)
 * ============================================================================
 * Represents a single line item in the user's shopping cart. This is a
 * lightweight POJO (NOT a JPA entity) because cart state is maintained
 * in-memory by the {@link com.techmart.ejb.ShoppingCartBean} (Stateful EJB).
 *
 * <p><b>Design Decision:</b> Cart items are not persisted to the database
 * until checkout. This avoids unnecessary database writes for browse-heavy
 * workloads (typical e-commerce pattern: ~2% checkout rate). The Stateful
 * bean handles passivation/activation serialization automatically.</p>
 *
 * <p><b>Serializable:</b> Required because Stateful EJB state may be
 * passivated to disk when the container needs to reclaim memory under
 * high load (10,000+ concurrent sessions).</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // Fields
    // ========================================================================

    /** Reference to the product (by ID, not entity reference, to avoid detached entity issues) */
    private Long productId;

    /** Product SKU for display and order creation */
    private String productSku;

    /** Product name snapshot at the time of adding to cart */
    private String productName;

    /** Unit price snapshot at the time of adding to cart (price may change later) */
    private BigDecimal unitPrice;

    /** Quantity of this product in the cart */
    private int quantity;

    // ========================================================================
    // Constructors
    // ========================================================================

    /** Default constructor for serialization */
    public CartItem() {
    }

    /**
     * Creates a cart item from a product with the specified quantity.
     *
     * @param productId   the product's database ID
     * @param productSku  the product's SKU code
     * @param productName the product's display name
     * @param unitPrice   the product's current price (snapshotted)
     * @param quantity    the desired quantity
     */
    public CartItem(Long productId, String productSku, String productName,
                    BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Calculates the subtotal for this cart line item.
     *
     * @return unitPrice * quantity
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

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public String toString() {
        return "CartItem{" +
               "productId=" + productId +
               ", sku='" + productSku + '\'' +
               ", name='" + productName + '\'' +
               ", unitPrice=" + unitPrice +
               ", quantity=" + quantity +
               ", subtotal=" + getSubtotal() +
               '}';
    }
}
