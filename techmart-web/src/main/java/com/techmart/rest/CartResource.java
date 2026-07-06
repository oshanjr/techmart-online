package com.techmart.rest;

import com.techmart.ejb.OrderProcessingBean;
import com.techmart.ejb.ProductCatalogBean;
import com.techmart.ejb.ShoppingCartBean;
import com.techmart.model.CartItem;
import com.techmart.model.Product;

import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * CartResource — RESTful Endpoint for Shopping Cart Management
 * ============================================================================
 * Manages the user's shopping cart through REST operations. The underlying
 * {@link ShoppingCartBean} (Stateful EJB) maintains per-session state.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET    /api/cart}              — View cart contents</li>
 *   <li>{@code POST   /api/cart/items}        — Add item to cart</li>
 *   <li>{@code PUT    /api/cart/items/{id}}    — Update item quantity</li>
 *   <li>{@code DELETE /api/cart/items/{id}}    — Remove item from cart</li>
 *   <li>{@code POST   /api/cart/checkout}      — Checkout and place order</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartResource implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(CartResource.class.getName());

    /** Stateful ShoppingCartBean managed by CDI */
    @Inject
    private ShoppingCartBean shoppingCart;

    /** Stateless ProductCatalogBean for product lookups */
    @EJB
    private ProductCatalogBean productCatalog;

    /** Stateless OrderProcessingBean for checkout */
    @EJB
    private OrderProcessingBean orderProcessingBean;

    // ========================================================================
    // GET — View Cart
    // ========================================================================

    /**
     * Returns the current cart contents including items and totals.
     *
     * <p>Example: {@code GET /api/cart}</p>
     *
     * @return 200 OK with cart summary JSON
     */
    @GET
    public Response viewCart() {
        LOGGER.log(Level.FINE, "GET /cart");

        Collection<CartItem> items = shoppingCart.getCartItems();

        Map<String, Object> cartSummary = new HashMap<>();
        cartSummary.put("sessionId", shoppingCart.getSessionId());
        cartSummary.put("items", items);
        cartSummary.put("itemCount", shoppingCart.getItemCount());
        cartSummary.put("cartTotal", shoppingCart.getCartTotal());
        cartSummary.put("isEmpty", shoppingCart.isEmpty());

        return Response.ok(cartSummary).build();
    }

    // ========================================================================
    // POST — Add Item to Cart
    // ========================================================================

    /**
     * Adds a product to the shopping cart.
     *
     * <p>Example: {@code POST /api/cart/items}
     * <pre>
     * {
     *   "productId": 42,
     *   "quantity": 2
     * }
     * </pre>
     * </p>
     *
     * @param request JSON with productId and quantity
     * @return 200 OK with updated cart, or 404 if product not found
     */
    @POST
    @Path("/items")
    public Response addItem(Map<String, Object> request) {
        Long productId = Long.valueOf(request.get("productId").toString());
        int quantity = Integer.parseInt(request.getOrDefault("quantity", "1").toString());

        LOGGER.log(Level.FINE, "POST /cart/items — productId={0}, qty={1}",
                   new Object[]{productId, quantity});

        // Look up the product
        Product product = productCatalog.findById(productId);
        if (product == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Product not found\", \"productId\": " + productId + "}")
                           .build();
        }

        // Add to cart
        shoppingCart.addItem(product, quantity);

        return viewCart(); // Return updated cart state
    }

    // ========================================================================
    // PUT — Update Item Quantity
    // ========================================================================

    /**
     * Updates the quantity of a product in the cart.
     *
     * <p>Example: {@code PUT /api/cart/items/42}
     * <pre>
     * {
     *   "quantity": 5
     * }
     * </pre>
     * </p>
     *
     * @param productId the product ID
     * @param request   JSON with the new quantity
     * @return 200 OK with updated cart, or 404 if item not in cart
     */
    @PUT
    @Path("/items/{productId}")
    public Response updateItemQuantity(@PathParam("productId") Long productId,
                                        Map<String, Object> request) {
        int newQuantity = Integer.parseInt(request.get("quantity").toString());

        LOGGER.log(Level.FINE, "PUT /cart/items/{0} — qty={1}",
                   new Object[]{productId, newQuantity});

        boolean updated = shoppingCart.updateQuantity(productId, newQuantity);
        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Item not found in cart\", \"productId\": " + productId + "}")
                           .build();
        }

        return viewCart();
    }

    // ========================================================================
    // DELETE — Remove Item from Cart
    // ========================================================================

    /**
     * Removes a product from the shopping cart.
     *
     * <p>Example: {@code DELETE /api/cart/items/42}</p>
     *
     * @param productId the product ID to remove
     * @return 200 OK with updated cart, or 404 if item not in cart
     */
    @DELETE
    @Path("/items/{productId}")
    public Response removeItem(@PathParam("productId") Long productId) {
        LOGGER.log(Level.FINE, "DELETE /cart/items/{0}", productId);

        boolean removed = shoppingCart.removeItem(productId);
        if (!removed) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Item not found in cart\", \"productId\": " + productId + "}")
                           .build();
        }

        return viewCart();
    }

    // ========================================================================
    // POST — Checkout
    // ========================================================================

    /**
     * Initiates checkout — converts the cart to an order.
     * This triggers the async order processing pipeline.
     *
     * <p>Example: {@code POST /api/cart/checkout}
     * <pre>
     * {
     *   "customerEmail": "john@example.com",
     *   "customerName": "John Doe",
     *   "shippingAddress": "123 Main St, City, State 12345"
     * }
     * </pre>
     * </p>
     *
     * <p><b>Important:</b> After checkout, the Stateful ShoppingCartBean
     * is destroyed (via @Remove). Any subsequent cart operations will
     * use a new bean instance.</p>
     *
     * @param request JSON with customer details
     * @return 202 Accepted with order reference
     */
    @POST
    @Path("/checkout")
    public Response checkout(Map<String, Object> request) {
        LOGGER.log(Level.INFO, "POST /cart/checkout");

        if (shoppingCart.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Cart is empty\"}")
                           .build();
        }

        String customerEmail = (String) request.get("customerEmail");
        String customerName = (String) request.get("customerName");
        String shippingAddress = (String) request.get("shippingAddress");

        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"customerEmail is required\"}")
                           .build();
        }

        // Get cart items and destroy the Stateful bean
        var cartItems = shoppingCart.checkout();

        // Trigger async order processing
        orderProcessingBean.processOrderAsync(cartItems, customerEmail, customerName, shippingAddress);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ACCEPTED");
        response.put("message", "Order is being processed asynchronously");
        response.put("itemCount", cartItems.size());
        response.put("customerEmail", customerEmail);

        return Response.status(Response.Status.ACCEPTED)
                       .entity(response)
                       .build();
    }
}
