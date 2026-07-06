package com.techmart.rest;

import com.techmart.ejb.AppConfigBean;
import com.techmart.jms.InventoryMessageProducer;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * InventoryResource — RESTful Endpoint for Inventory Management
 * ============================================================================
 * Provides inventory visibility and update operations. Inventory changes
 * trigger JMS messages for real-time synchronization across warehouses.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET  /api/inventory}            — Get aggregated inventory summary</li>
 *   <li>{@code GET  /api/inventory/{productId}} — Get inventory for a specific product</li>
 *   <li>{@code POST /api/inventory/update}      — Update inventory (triggers JMS)</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    private static final Logger LOGGER = Logger.getLogger(InventoryResource.class.getName());

    /** Singleton cache for fast inventory lookups */
    @EJB
    private AppConfigBean appConfigBean;

    /** JMS producer for publishing inventory change events */
    @Inject
    private InventoryMessageProducer inventoryMessageProducer;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * Returns the aggregated inventory summary from the Singleton cache.
     * This is a fast, in-memory lookup — no database query required.
     *
     * <p>Example: {@code GET /api/inventory}</p>
     *
     * @return 200 OK with map of productId → totalQuantity
     */
    @GET
    public Response getInventorySummary() {
        LOGGER.log(Level.FINE, "GET /inventory — summary");

        Map<String, Object> response = new HashMap<>();
        response.put("inventorySummary", appConfigBean.getInventorySummary());
        response.put("cachedProductCount", appConfigBean.getCachedProductCount());
        response.put("lastRefreshTime", appConfigBean.getLastCacheRefreshTime());
        response.put("refreshCount", appConfigBean.getCacheRefreshCount());

        return Response.ok(response).build();
    }

    /**
     * Returns the cached inventory level for a specific product.
     *
     * <p>Example: {@code GET /api/inventory/42}</p>
     *
     * @param productId the product ID
     * @return 200 OK with quantity info
     */
    @GET
    @Path("/{productId}")
    public Response getProductInventory(@PathParam("productId") Long productId) {
        LOGGER.log(Level.FINE, "GET /inventory/{0}", productId);

        int quantity = appConfigBean.getCachedInventory(productId);

        Map<String, Object> response = new HashMap<>();
        response.put("productId", productId);
        response.put("totalQuantity", quantity);
        response.put("inStock", quantity > 0);
        response.put("lowStock", quantity > 0 && quantity < appConfigBean.getConfigPropertyAsInt(
            "inventory.low-stock-threshold", 10));

        return Response.ok(response).build();
    }

    // ========================================================================
    // POST Endpoints
    // ========================================================================

    /**
     * Updates inventory for a product at a specific warehouse and
     * publishes a JMS message for cross-warehouse synchronization.
     *
     * <p>Example: {@code POST /api/inventory/update}
     * <pre>
     * {
     *   "productId": 42,
     *   "warehouseId": 1,
     *   "quantityDelta": 100,
     *   "eventType": "STOCK_ADDED"
     * }
     * </pre>
     * </p>
     *
     * @param request JSON with inventory update details
     * @return 202 Accepted (update is propagated via JMS)
     */
    @POST
    @Path("/update")
    public Response updateInventory(Map<String, Object> request) {
        LOGGER.log(Level.INFO, "POST /inventory/update");

        try {
            Long productId = Long.valueOf(request.get("productId").toString());
            Long warehouseId = Long.valueOf(request.get("warehouseId").toString());
            int quantityDelta = Integer.parseInt(request.get("quantityDelta").toString());
            String eventType = (String) request.getOrDefault("eventType", "STOCK_ADJUSTED");

            // Calculate new quantity (from cache for immediate response)
            int currentQty = appConfigBean.getCachedInventory(productId);
            int newQuantity = currentQty + quantityDelta;

            // Publish JMS message — this triggers the InventoryUpdateMDB
            // which handles the database update and cache refresh
            inventoryMessageProducer.publishInventoryUpdate(
                productId, warehouseId, quantityDelta, newQuantity, eventType);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "ACCEPTED");
            response.put("message", "Inventory update published via JMS");
            response.put("productId", productId);
            response.put("warehouseId", warehouseId);
            response.put("quantityDelta", quantityDelta);
            response.put("estimatedNewQuantity", newQuantity);

            return Response.status(Response.Status.ACCEPTED)
                           .entity(response)
                           .build();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to process inventory update", e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    /**
     * Forces a full inventory cache refresh from the database.
     * This is an admin operation for cache consistency recovery.
     *
     * <p>Example: {@code POST /api/inventory/refresh}</p>
     *
     * @return 200 OK when cache refresh is complete
     */
    @POST
    @Path("/refresh")
    public Response refreshCache() {
        LOGGER.log(Level.INFO, "POST /inventory/refresh — forcing cache refresh");

        appConfigBean.refreshInventoryCache();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Inventory cache refreshed");
        response.put("cachedProductCount", appConfigBean.getCachedProductCount());

        return Response.ok(response).build();
    }
}
