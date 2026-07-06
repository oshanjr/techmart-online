package com.techmart.ejb;

import com.techmart.interceptor.PerformanceLogged;
import com.techmart.model.CartItem;
import com.techmart.model.Product;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * ShoppingCartBean — Stateful Session Bean
 * ============================================================================
 * Manages a user's shopping cart state across multiple requests within a
 * session. As a {@code @Stateful} bean, each client gets a dedicated instance
 * that maintains conversational state.
 *
 * <p><b>Session Management Strategy:</b></p>
 * <ul>
 *   <li>Cart state is maintained in-memory as a {@code Map<Long, CartItem>}</li>
 *   <li>State is NOT persisted to the database until checkout</li>
 *   <li>{@code @PrePassivate} / {@code @PostActivate} handle session
 *       serialization when the container needs to reclaim memory</li>
 *   <li>{@code @Remove} methods signal the container to destroy the instance
 *       after checkout or explicit cart clearing</li>
 * </ul>
 *
 * <p><b>Scalability for 10,000+ Concurrent Users:</b></p>
 * The container passivates idle Stateful beans to secondary storage (disk)
 * when memory pressure increases. The {@code @StatefulTimeout} is set to
 * 30 minutes to automatically clean up abandoned carts.
 *
 * <p><b>JNDI Binding:</b></p>
 * <pre>
 *   &#64;EJB
 *   private ShoppingCartBean cart;  // Injected per-session
 * </pre>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Stateful
@StatefulTimeout(value = 30, unit = java.util.concurrent.TimeUnit.MINUTES)
@PerformanceLogged
public class ShoppingCartBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ShoppingCartBean.class.getName());

    /**
     * In-memory cart storage.
     * Key: Product ID
     * Value: CartItem (contains snapshot of product info + quantity)
     *
     * Using LinkedHashMap to maintain insertion order for predictable
     * iteration in cart display.
     */
    private Map<Long, CartItem> cartItems;

    /** Unique session identifier for logging and debugging */
    private String sessionId;

    // ========================================================================
    // Lifecycle Callbacks
    // ========================================================================

    /**
     * Initializes the cart when the Stateful bean instance is first created.
     * This is called exactly once per client session.
     */
    @PostConstruct
    public void initialize() {
        cartItems = new LinkedHashMap<>();
        sessionId = UUID.randomUUID().toString().substring(0, 8);
        LOGGER.log(Level.INFO, "Shopping cart created: session={0}", sessionId);
    }

    /**
     * Called by the container before the bean state is serialized to secondary
     * storage (passivation). This happens when the container needs to free
     * memory under high load.
     *
     * Ensures all transient state is in a serializable form.
     */
    @PrePassivate
    public void beforePassivation() {
        LOGGER.log(Level.INFO,
            "Passivating shopping cart: session={0}, items={1}",
            new Object[]{sessionId, cartItems.size()});
        // CartItem is Serializable — no special handling needed.
        // If we had any transient resources (DB connections, etc.),
        // they would be released here.
    }

    /**
     * Called by the container after the bean state is deserialized from
     * secondary storage (activation). Restores any transient state.
     */
    @PostActivate
    public void afterActivation() {
        LOGGER.log(Level.INFO,
            "Activating shopping cart: session={0}, items={1}",
            new Object[]{sessionId, cartItems.size()});
        // Re-initialize any transient resources if needed.
        // The cartItems map is automatically restored via deserialization.
    }

    /**
     * Called by the container when the bean is about to be destroyed.
     * Logs the final state for analytics/debugging.
     */
    @PreDestroy
    public void cleanup() {
        LOGGER.log(Level.INFO,
            "Destroying shopping cart: session={0}, items={1}, total={2}",
            new Object[]{sessionId, cartItems.size(), getCartTotal()});
    }

    // ========================================================================
    // Business Methods — Cart Operations
    // ========================================================================

    /**
     * Adds a product to the shopping cart. If the product is already in the
     * cart, the quantity is incremented.
     *
     * @param product  the product to add (must be a managed or detached entity)
     * @param quantity the quantity to add (must be positive)
     * @throws IllegalArgumentException if product is null or quantity is invalid
     */
    public void addItem(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + quantity);
        }

        CartItem existingItem = cartItems.get(product.getId());

        if (existingItem != null) {
            // Product already in cart — increment quantity
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            LOGGER.log(Level.FINE,
                "Updated cart item quantity: session={0}, product={1}, newQty={2}",
                new Object[]{sessionId, product.getSku(), existingItem.getQuantity()});
        } else {
            // New product — create a CartItem with snapshotted price
            CartItem newItem = new CartItem(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                quantity
            );
            cartItems.put(product.getId(), newItem);
            LOGGER.log(Level.FINE,
                "Added new item to cart: session={0}, product={1}, qty={2}",
                new Object[]{sessionId, product.getSku(), quantity});
        }
    }

    /**
     * Removes a product entirely from the shopping cart.
     *
     * @param productId the ID of the product to remove
     * @return true if the product was in the cart and removed
     */
    public boolean removeItem(Long productId) {
        CartItem removed = cartItems.remove(productId);
        if (removed != null) {
            LOGGER.log(Level.FINE,
                "Removed item from cart: session={0}, product={1}",
                new Object[]{sessionId, removed.getProductSku()});
            return true;
        }
        return false;
    }

    /**
     * Updates the quantity of a specific product in the cart.
     * If the new quantity is 0 or negative, the item is removed.
     *
     * @param productId   the product ID
     * @param newQuantity the new quantity
     * @return true if the item was found and updated
     */
    public boolean updateQuantity(Long productId, int newQuantity) {
        CartItem item = cartItems.get(productId);
        if (item == null) {
            return false;
        }

        if (newQuantity <= 0) {
            cartItems.remove(productId);
            LOGGER.log(Level.FINE,
                "Removed item (quantity <= 0): session={0}, product={1}",
                new Object[]{sessionId, item.getProductSku()});
        } else {
            item.setQuantity(newQuantity);
            LOGGER.log(Level.FINE,
                "Updated quantity: session={0}, product={1}, qty={2}",
                new Object[]{sessionId, item.getProductSku(), newQuantity});
        }
        return true;
    }

    /**
     * Returns all items currently in the shopping cart.
     *
     * @return unmodifiable collection of cart items
     */
    public Collection<CartItem> getCartItems() {
        return Collections.unmodifiableCollection(cartItems.values());
    }

    /**
     * Returns the total number of individual items in the cart
     * (sum of all quantities).
     *
     * @return total item count
     */
    public int getItemCount() {
        return cartItems.values().stream()
                        .mapToInt(CartItem::getQuantity)
                        .sum();
    }

    /**
     * Calculates the total price of all items in the cart.
     *
     * @return the sum of all item subtotals
     */
    public BigDecimal getCartTotal() {
        return cartItems.values().stream()
                        .map(CartItem::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Checks if the cart is empty.
     *
     * @return true if the cart contains no items
     */
    public boolean isEmpty() {
        return cartItems.isEmpty();
    }

    /**
     * Retrieves a specific cart item by product ID.
     *
     * @param productId the product ID to look up
     * @return the cart item, or null if not in the cart
     */
    public CartItem getItem(Long productId) {
        return cartItems.get(productId);
    }

    // ========================================================================
    // @Remove Methods — Bean Lifecycle Termination
    // ========================================================================

    /**
     * Processes checkout and destroys the Stateful bean instance.
     * The {@code @Remove} annotation tells the container to destroy this
     * bean after the method completes successfully.
     *
     * <p>This method returns the cart contents so the caller can proceed
     * with order creation. After this method returns, the bean is invalid
     * and any further method calls will throw {@code NoSuchEJBException}.</p>
     *
     * @return the list of cart items at checkout time
     */
    @Remove
    public List<CartItem> checkout() {
        LOGGER.log(Level.INFO,
            "Checkout initiated: session={0}, items={1}, total={2}",
            new Object[]{sessionId, cartItems.size(), getCartTotal()});

        // Return a copy of the items (the bean is about to be destroyed)
        return new ArrayList<>(cartItems.values());
    }

    /**
     * Clears the cart and destroys the Stateful bean instance.
     * Used when the user explicitly abandons their cart.
     * {@code retainIfException = true} keeps the bean alive if clearing fails.
     */
    @Remove(retainIfException = true)
    public void clearAndDestroy() {
        LOGGER.log(Level.INFO,
            "Cart cleared and destroyed: session={0}", sessionId);
        cartItems.clear();
    }

    /**
     * Returns the session ID for debugging purposes.
     *
     * @return the unique session identifier
     */
    public String getSessionId() {
        return sessionId;
    }
}
