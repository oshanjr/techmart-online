package com.techmart.rest;

import com.techmart.ejb.OrderProcessingBean;
import com.techmart.model.Order;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * OrderResource — RESTful Endpoint for Order Management
 * ============================================================================
 * Exposes order processing and query operations via REST.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET  /api/orders/{id}}      — Get order by ID</li>
 *   <li>{@code GET  /api/orders}           — List orders by status</li>
 *   <li>{@code PUT  /api/orders/{id}/status} — Update order status</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger LOGGER = Logger.getLogger(OrderResource.class.getName());

    @EJB
    private OrderProcessingBean orderProcessingBean;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * Retrieves an order by its ID.
     *
     * <p>Example: {@code GET /api/orders/123}</p>
     *
     * @param id the order ID
     * @return 200 OK with the order, or 404 Not Found
     */
    @GET
    @Path("/{id}")
    public Response getOrderById(@PathParam("id") Long id) {
        LOGGER.log(Level.FINE, "GET /orders/{0}", id);

        Order order = orderProcessingBean.findOrderById(id);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Order not found\", \"id\": " + id + "}")
                           .build();
        }
        return Response.ok(order).build();
    }

    /**
     * Lists orders filtered by status.
     *
     * <p>Example: {@code GET /api/orders?status=PROCESSING}</p>
     *
     * @param statusParam the order status to filter by (optional)
     * @return 200 OK with list of orders
     */
    @GET
    public Response getOrders(@QueryParam("status") String statusParam) {
        LOGGER.log(Level.FINE, "GET /orders?status={0}", statusParam);

        if (statusParam == null || statusParam.trim().isEmpty()) {
            // Return all orders if no status filter specified
            List<Order> orders = orderProcessingBean.findAllOrders();
            return Response.ok(orders).build();
        }

        try {
            Order.OrderStatus status = Order.OrderStatus.valueOf(statusParam.toUpperCase());
            List<Order> orders = orderProcessingBean.findOrdersByStatus(status);
            return Response.ok(orders)
                           .header("X-Total-Count", orders.size())
                           .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Invalid status. Valid values: " +
                                   "PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED\"}")
                           .build();
        }
    }

    /**
     * Retrieves the order history for a specific customer.
     *
     * <p>Example: {@code GET /api/orders/customer/john@example.com}</p>
     *
     * @param email the customer's email address
     * @return 200 OK with list of orders
     */
    @GET
    @Path("/customer/{email}")
    public Response getOrdersByCustomerEmail(@PathParam("email") String email) {
        LOGGER.log(Level.FINE, "GET /orders/customer/{0}", email);
        
        if (email == null || email.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Email is required\"}")
                           .build();
        }

        List<Order> orders = orderProcessingBean.findOrdersByEmail(email);
        return Response.ok(orders)
                       .header("X-Total-Count", orders.size())
                       .build();
    }

    // ========================================================================
    // PUT Endpoints
    // ========================================================================

    /**
     * Updates the status of an existing order asynchronously.
     *
     * <p>Example: {@code PUT /api/orders/123/status}
     * <pre>
     * {
     *   "status": "SHIPPED"
     * }
     * </pre>
     * </p>
     *
     * @param id      the order ID
     * @param request JSON with the new status
     * @return 202 Accepted (status update is async)
     */
    @PUT
    @Path("/{id}/status")
    public Response updateOrderStatus(@PathParam("id") Long id,
                                       Map<String, String> request) {
        String newStatusStr = request.get("status");
        LOGGER.log(Level.INFO, "PUT /orders/{0}/status — newStatus={1}",
                   new Object[]{id, newStatusStr});

        if (newStatusStr == null || newStatusStr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'status' field is required\"}")
                           .build();
        }

        try {
            Order.OrderStatus newStatus = Order.OrderStatus.valueOf(
                newStatusStr.toUpperCase());

            // Trigger async status update
            orderProcessingBean.updateOrderStatusAsync(id, newStatus);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", id);
            response.put("newStatus", newStatus.name());
            response.put("message", "Status update initiated asynchronously");

            return Response.status(Response.Status.ACCEPTED)
                           .entity(response)
                           .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Invalid status: " + newStatusStr + "\"}")
                           .build();
        }
    }
}
