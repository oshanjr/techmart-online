package com.techmart.jms;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * OrderMessageProducer — JMS Queue Producer (Point-to-Point Pattern)
 * ============================================================================
 * Sends order processing messages to the {@code OrderProcessing} JMS Queue.
 * In the Point-to-Point (P2P) pattern, each message is consumed by exactly
 * one consumer, ensuring that order processing steps are not duplicated.
 *
 * <p><b>P2P vs Pub/Sub Choice:</b> Orders use a Queue (P2P) rather than a
 * Topic because each order should be processed exactly once. If we used a
 * Topic, multiple subscribers would each try to process the same order,
 * causing duplicate charges, shipments, etc.</p>
 *
 * <p><b>Message Types:</b></p>
 * <ul>
 *   <li>{@code ORDER_CREATED} — triggers initial processing pipeline</li>
 *   <li>{@code ORDER_STATUS_CHANGED} — triggers downstream notifications</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@ApplicationScoped
public class OrderMessageProducer {

    private static final Logger LOGGER = Logger.getLogger(OrderMessageProducer.class.getName());

    // ========================================================================
    // JMS Resources
    // ========================================================================

    /**
     * JMSContext — JMS 2.0 simplified API.
     * Uses the XA-capable connection factory for JTA transaction integration.
     */
    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext jmsContext;

    /**
     * The JMS Queue for order processing.
     * JNDI name: java:/jms/queue/OrderProcessing
     */
    @Resource(lookup = "java:/jms/queue/OrderProcessing")
    private Queue orderQueue;

    // ========================================================================
    // Message Sending Methods
    // ========================================================================

    /**
     * Sends an ORDER_CREATED message to the processing queue.
     * This is called by {@link com.techmart.ejb.OrderProcessingBean} after
     * an order is successfully persisted to the database.
     *
     * <p>The MDB ({@link com.techmart.mdb.OrderProcessingMDB}) will pick up
     * this message and execute the next steps: payment capture, inventory
     * deduction, shipping label generation, etc.</p>
     *
     * @param orderId       the ID of the newly created order
     * @param customerEmail the customer's email (for correlation)
     */
    public void sendOrderCreatedMessage(Long orderId, String customerEmail) {
        try {
            // Create a MapMessage with order details
            MapMessage message = jmsContext.createMapMessage();
            message.setLong("orderId", orderId);
            message.setString("customerEmail", customerEmail);
            message.setString("eventType", "ORDER_CREATED");
            message.setLong("timestamp", System.currentTimeMillis());

            // Set a message priority (orders are high-priority)
            // Priority ranges from 0 (lowest) to 9 (highest), default is 4
            jmsContext.createProducer()
                      .setDeliveryMode(DeliveryMode.PERSISTENT)  // Must survive broker restart
                      .setPriority(7)                              // High priority
                      .setTimeToLive(3_600_000)                    // 1-hour TTL
                      .send(orderQueue, message);

            LOGGER.log(Level.INFO,
                "Sent ORDER_CREATED message: orderId={0}, customer={1}",
                new Object[]{orderId, customerEmail});

        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE,
                "Failed to send ORDER_CREATED message for order " + orderId, e);
            throw new RuntimeException("JMS send failed for order creation", e);
        }
    }

    /**
     * Sends an ORDER_STATUS_CHANGED message to the processing queue.
     * Used when an order transitions between lifecycle states
     * (e.g., PROCESSING → SHIPPED).
     *
     * @param orderId   the order ID
     * @param oldStatus the previous status
     * @param newStatus the new status
     */
    public void sendOrderStatusChangedMessage(Long orderId,
                                               String oldStatus,
                                               String newStatus) {
        try {
            MapMessage message = jmsContext.createMapMessage();
            message.setLong("orderId", orderId);
            message.setString("oldStatus", oldStatus);
            message.setString("newStatus", newStatus);
            message.setString("eventType", "ORDER_STATUS_CHANGED");
            message.setLong("timestamp", System.currentTimeMillis());

            // Set message property for potential selector filtering
            message.setStringProperty("NewStatus", newStatus);

            jmsContext.createProducer()
                      .setDeliveryMode(DeliveryMode.PERSISTENT)
                      .setPriority(5)
                      .setTimeToLive(3_600_000)
                      .send(orderQueue, message);

            LOGGER.log(Level.INFO,
                "Sent ORDER_STATUS_CHANGED message: orderId={0}, {1} → {2}",
                new Object[]{orderId, oldStatus, newStatus});

        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE,
                "Failed to send status change message for order " + orderId, e);
            throw new RuntimeException("JMS send failed for order status change", e);
        }
    }
}
