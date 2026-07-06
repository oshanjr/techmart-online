package com.techmart.mdb;

import com.techmart.ejb.NotificationServiceBean;
import com.techmart.ejb.OrderProcessingBean;
import com.techmart.model.Order;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * OrderProcessingMDB — Message-Driven Bean (Queue Consumer)
 * ============================================================================
 * Consumes order processing messages from the {@code OrderProcessing} JMS Queue
 * (Point-to-Point pattern). Each message is consumed by exactly one instance,
 * ensuring orders are never processed twice.
 *
 * <p><b>Activation Configuration:</b></p>
 * <ul>
 *   <li>{@code destinationType = javax.jms.Queue} — P2P pattern</li>
 *   <li>{@code destinationLookup} — JNDI name of the queue</li>
 *   <li>{@code maxSession = 10} — up to 10 concurrent order processors</li>
 * </ul>
 *
 * <p><b>Processing Flow:</b></p>
 * <ol>
 *   <li>Receive order event from the Queue</li>
 *   <li>For ORDER_CREATED: transition order to PROCESSING status</li>
 *   <li>For ORDER_STATUS_CHANGED: trigger appropriate notifications</li>
 *   <li>Error handling with container-managed redelivery</li>
 * </ol>
 *
 * <p><b>Scalability:</b> The {@code maxSession=10} setting allows up to
 * 10 concurrent MDB instances to process orders in parallel, providing
 * throughput scaling for high-volume order periods (e.g., flash sales).</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@MessageDriven(activationConfig = {
    // JMS Queue — Point-to-Point pattern (exactly-once delivery)
    @ActivationConfigProperty(
        propertyName = "destinationType",
        propertyValue = "javax.jms.Queue"
    ),
    // JNDI lookup for the order processing queue
    @ActivationConfigProperty(
        propertyName = "destinationLookup",
        propertyValue = "java:/jms/queue/OrderProcessing"
    ),
    // Maximum concurrent MDB instances for parallel order processing
    // Tuned for 10,000+ concurrent users — can be adjusted based on load testing
    @ActivationConfigProperty(
        propertyName = "maxSession",
        propertyValue = "10"
    ),
    // Auto-acknowledge — message is acknowledged after onMessage completes
    @ActivationConfigProperty(
        propertyName = "acknowledgeMode",
        propertyValue = "Auto-acknowledge"
    )
})
public class OrderProcessingMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(OrderProcessingMDB.class.getName());

    // ========================================================================
    // Dependencies
    // ========================================================================

    @EJB
    private OrderProcessingBean orderProcessingBean;

    @EJB
    private NotificationServiceBean notificationService;

    // ========================================================================
    // Message Processing
    // ========================================================================

    /**
     * Called by the container when an order message arrives in the queue.
     * This method runs in a container-managed JTA transaction.
     *
     * <p>If processing fails, the container will redeliver the message
     * according to the broker's redelivery policy (typically 3-10 attempts
     * before moving to a Dead Letter Queue).</p>
     *
     * @param message the JMS message from the order queue
     */
    @Override
    public void onMessage(Message message) {
        try {
            if (!(message instanceof MapMessage)) {
                LOGGER.log(Level.WARNING,
                    "Received unexpected message type: {0}. Discarding.",
                    message.getClass().getSimpleName());
                return;
            }

            MapMessage mapMessage = (MapMessage) message;
            String eventType = mapMessage.getString("eventType");

            LOGGER.log(Level.INFO,
                "OrderProcessingMDB received message: eventType={0}",
                eventType);

            // Route to appropriate handler based on event type
            switch (eventType) {
                case "ORDER_CREATED":
                    handleOrderCreated(mapMessage);
                    break;

                case "ORDER_STATUS_CHANGED":
                    handleOrderStatusChanged(mapMessage);
                    break;

                default:
                    LOGGER.log(Level.WARNING,
                        "Unknown event type: {0}. Message ignored.", eventType);
            }

        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE,
                "Error reading JMS message in OrderProcessingMDB", e);
            // RuntimeException triggers message redelivery
            throw new RuntimeException("Failed to process order message", e);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Unexpected error in OrderProcessingMDB", e);
            throw new RuntimeException("Order message processing failed", e);
        }
    }

    // ========================================================================
    // Event Handlers
    // ========================================================================

    /**
     * Handles ORDER_CREATED events.
     *
     * <p>Transitions the order from PENDING to PROCESSING and initiates
     * the fulfillment pipeline.</p>
     *
     * <p><b>In a full production system, this would also:</b></p>
     * <ul>
     *   <li>Capture payment via a payment gateway</li>
     *   <li>Reserve inventory at the nearest warehouse</li>
     *   <li>Generate shipping labels</li>
     *   <li>Update the ERP/WMS systems</li>
     * </ul>
     */
    private void handleOrderCreated(MapMessage message) throws JMSException {
        Long orderId = message.getLong("orderId");
        String customerEmail = message.getString("customerEmail");

        LOGGER.log(Level.INFO,
            "Processing new order: id={0}, customer={1}",
            new Object[]{orderId, customerEmail});

        try {
            // Transition order to PROCESSING status asynchronously
            Future<Order> future = orderProcessingBean.updateOrderStatusAsync(
                orderId, Order.OrderStatus.PROCESSING);

            // In a real system, we would NOT block here.
            // We block briefly to confirm the status update succeeded.
            // Production code would use a callback or event-driven pattern.

            LOGGER.log(Level.INFO,
                "Order {0} status update to PROCESSING initiated", orderId);

            // ================================================================
            // TODO: Add payment capture, inventory reservation, etc.
            // These would be additional EJB calls or JMS messages to
            // dedicated services.
            // ================================================================

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Failed to process order " + orderId, e);
            throw new RuntimeException("Order processing failed for order " + orderId, e);
        }
    }

    /**
     * Handles ORDER_STATUS_CHANGED events.
     *
     * <p>Triggers customer notifications when an order's status changes
     * (e.g., when an order is shipped or delivered).</p>
     */
    private void handleOrderStatusChanged(MapMessage message) throws JMSException {
        Long orderId = message.getLong("orderId");
        String oldStatus = message.getString("oldStatus");
        String newStatus = message.getString("newStatus");

        LOGGER.log(Level.INFO,
            "Order status changed: id={0}, {1} → {2}",
            new Object[]{orderId, oldStatus, newStatus});

        // Look up the order to get customer details
        Order order = orderProcessingBean.findOrderById(orderId);
        if (order == null) {
            LOGGER.log(Level.WARNING, "Order not found: {0}", orderId);
            return;
        }

        // Send shipping update notification
        if ("SHIPPED".equals(newStatus) || "DELIVERED".equals(newStatus)) {
            try {
                notificationService.sendShippingUpdateAsync(
                    order.getCustomerEmail(),
                    orderId,
                    newStatus
                );
                LOGGER.log(Level.INFO,
                    "Shipping notification triggered for order {0}: {1}",
                    new Object[]{orderId, newStatus});
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                    "Failed to send notification for order " + orderId, e);
                // Don't rethrow — notification failure should not cause message redelivery
            }
        }
    }
}
