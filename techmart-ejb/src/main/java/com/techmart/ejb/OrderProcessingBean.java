package com.techmart.ejb;

import com.techmart.interceptor.PerformanceLogged;
import com.techmart.jms.OrderMessageProducer;
import com.techmart.model.*;

import javax.annotation.PostConstruct;
import javax.ejb.*;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * OrderProcessingBean — Stateless Session Bean with Async Processing
 * ============================================================================
 * Handles the complete order processing lifecycle:
 * <ol>
 *   <li>Validates cart items and checks inventory availability</li>
 *   <li>Creates the order entity and persists it to the database</li>
 *   <li>Publishes a JMS message for downstream processing (notifications, etc.)</li>
 *   <li>Returns the result asynchronously via {@code Future<Order>}</li>
 * </ol>
 *
 * <p><b>Asynchronous Design:</b> The {@code @Asynchronous} annotation causes
 * the container to execute the method in a separate thread from its managed
 * thread pool. The caller receives a {@code Future<T>} immediately and can
 * check the result later or continue with other work.</p>
 *
 * <p><b>Error Handling:</b> If an error occurs during async processing,
 * it is wrapped in an {@code ExecutionException} when the caller invokes
 * {@code future.get()}. The {@code @AccessTimeout} prevents indefinite
 * waiting on container resources.</p>
 *
 * <p><b>Transaction:</b> Each async method runs in its own JTA transaction
 * (REQUIRED by default). If order creation fails, the entire transaction
 * rolls back.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Stateless
@PerformanceLogged
public class OrderProcessingBean {

    private static final Logger LOGGER = Logger.getLogger(OrderProcessingBean.class.getName());

    // ========================================================================
    // Dependencies (Injected by the Container)
    // ========================================================================

    /**
     * EntityManager for database operations.
     * Injected via @PersistenceContext (JPA standard).
     */
    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    /**
     * Singleton configuration bean for inventory cache lookups.
     * Injected via @EJB (EJB standard JNDI-based injection).
     *
     * JNDI equivalent: java:global/techmart-ear/techmart-ejb/AppConfigBean
     */
    @EJB
    private AppConfigBean appConfigBean;

    /**
     * JMS producer for publishing order events to the processing queue.
     * Injected via @Inject (CDI standard injection).
     */
    @Inject
    private OrderMessageProducer orderMessageProducer;

    /**
     * Notification service for sending async email/SMS alerts.
     * Injected via @EJB.
     */
    @EJB
    private NotificationServiceBean notificationService;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    @PostConstruct
    public void initialize() {
        LOGGER.info("OrderProcessingBean instance initialized in EJB pool");
    }

    // ========================================================================
    // Asynchronous Business Methods
    // ========================================================================

    /**
     * Processes an order asynchronously from a list of cart items.
     *
     * <p>This method is called after checkout from the {@link ShoppingCartBean}.
     * It runs in a separate container-managed thread, freeing the REST
     * request thread to return immediately with a 202 Accepted response.</p>
     *
     * <p><b>Processing Steps:</b></p>
     * <ol>
     *   <li>Create the Order entity with PENDING status</li>
     *   <li>Validate and convert CartItems to OrderItems</li>
     *   <li>Verify inventory availability for each item</li>
     *   <li>Persist the order to the database</li>
     *   <li>Publish a JMS message for further processing</li>
     *   <li>Trigger async notification to the customer</li>
     * </ol>
     *
     * @param cartItems      the items to order (from ShoppingCartBean.checkout())
     * @param customerEmail  the customer's email address
     * @param customerName   the customer's display name
     * @param shippingAddress the delivery address
     * @return a Future containing the created Order, or null on failure
     */
    @Asynchronous
    @AccessTimeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    public Future<Order> processOrderAsync(List<CartItem> cartItems,
                                           String customerEmail,
                                           String customerName,
                                           String shippingAddress) {
        LOGGER.log(Level.INFO,
            "Starting async order processing for customer: {0}, items: {1}",
            new Object[]{customerEmail, cartItems.size()});

        try {
            // Step 1: Create the order entity
            Order order = new Order(customerEmail, customerName);
            order.setShippingAddress(shippingAddress);
            order.setStatus(Order.OrderStatus.PENDING);

            // Step 2: Convert cart items to order items with validation
            for (CartItem cartItem : cartItems) {
                // Validate product still exists and is active
                Product product = em.find(Product.class, cartItem.getProductId());
                if (product == null || !product.isActive()) {
                    LOGGER.log(Level.WARNING,
                        "Product no longer available: {0}", cartItem.getProductSku());
                    throw new EJBException(
                        "Product no longer available: " + cartItem.getProductSku());
                }

                // Step 3: Check inventory from the Singleton cache
                int availableStock = appConfigBean.getCachedInventory(product.getId());
                if (availableStock < cartItem.getQuantity()) {
                    LOGGER.log(Level.WARNING,
                        "Insufficient stock for {0}: available={1}, requested={2}",
                        new Object[]{product.getSku(), availableStock, cartItem.getQuantity()});
                    throw new EJBException(
                        String.format("Insufficient stock for %s: available=%d, requested=%d",
                            product.getSku(), availableStock, cartItem.getQuantity()));
                }

                // Create the order item with current product price snapshot
                OrderItem orderItem = new OrderItem(product, cartItem.getQuantity());
                order.addItem(orderItem);
            }

            // Step 4: Persist the order (cascades to order items)
            em.persist(order);
            em.flush(); // Ensure ID is generated for the JMS message

            LOGGER.log(Level.INFO,
                "Order persisted: id={0}, total={1}",
                new Object[]{order.getId(), order.getTotalAmount()});

            // Step 5: Publish JMS message for downstream processing
            try {
                orderMessageProducer.sendOrderCreatedMessage(order.getId(), customerEmail);
                LOGGER.log(Level.INFO,
                    "JMS message sent for order: {0}", order.getId());
            } catch (Exception jmsEx) {
                // JMS failure should not roll back the order — log and continue
                LOGGER.log(Level.SEVERE,
                    "Failed to send JMS message for order " + order.getId(), jmsEx);
            }

            // Step 6: Trigger async notification
            try {
                notificationService.sendOrderConfirmationAsync(
                    customerEmail, customerName, order.getId(), order.getTotalAmount());
            } catch (Exception notifEx) {
                LOGGER.log(Level.SEVERE,
                    "Failed to trigger notification for order " + order.getId(), notifEx);
            }

            // Return the result wrapped in an AsyncResult
            return new AsyncResult<>(order);

        } catch (EJBException e) {
            LOGGER.log(Level.SEVERE,
                "Order processing failed for " + customerEmail + ": " + e.getMessage());
            throw e; // Will be wrapped in ExecutionException for the caller

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Unexpected error during order processing", e);
            throw new EJBException("Order processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the status of an existing order asynchronously.
     * Used by the OrderProcessingMDB to transition orders through their lifecycle.
     *
     * @param orderId   the order ID
     * @param newStatus the new status to set
     * @return a Future containing the updated Order
     */
    @Asynchronous
    @AccessTimeout(value = 15, unit = java.util.concurrent.TimeUnit.SECONDS)
    public Future<Order> updateOrderStatusAsync(Long orderId, Order.OrderStatus newStatus) {
        LOGGER.log(Level.INFO,
            "Updating order status: id={0}, newStatus={1}",
            new Object[]{orderId, newStatus});

        Order order = em.find(Order.class, orderId);
        if (order == null) {
            throw new EJBException("Order not found: " + orderId);
        }

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        LOGGER.log(Level.INFO,
            "Order status updated: id={0}, {1} → {2}",
            new Object[]{orderId, oldStatus, newStatus});

        return new AsyncResult<>(order);
    }

    // ========================================================================
    // Synchronous Query Methods
    // ========================================================================

    /**
     * Finds an order by its ID.
     *
     * @param orderId the order's primary key
     * @return the order, or null if not found
     */
    public Order findOrderById(Long orderId) {
        return em.find(Order.class, orderId);
    }

    /**
     * Finds all orders with a specific status.
     *
     * @param status the order status to filter by
     * @return list of matching orders
     */
    public List<Order> findOrdersByStatus(Order.OrderStatus status) {
        return em.createNamedQuery("Order.findByStatus", Order.class)
                 .setParameter("status", status)
                 .getResultList();
    }

    /**
     * Finds all orders placed by a specific customer.
     * Used for the Order History UI.
     *
     * @param email the customer's email address
     * @return list of matching orders, ordered by newest first
     */
    public List<Order> findOrdersByEmail(String email) {
        return em.createNamedQuery("Order.findByCustomerEmail", Order.class)
                 .setParameter("email", email)
                 .getResultList();
    }
}
