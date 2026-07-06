package com.techmart.jms;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * InventoryMessageProducer — JMS Topic Publisher (Pub/Sub Pattern)
 * ============================================================================
 * Publishes inventory change events to the {@code InventoryUpdates} JMS Topic.
 * Multiple subscribers (MDBs, external systems) can independently consume
 * these events for real-time inventory synchronization across warehouses.
 *
 * <p><b>Pub/Sub Pattern:</b> Using a Topic (not a Queue) because inventory
 * changes need to be broadcast to ALL interested consumers:
 * <ul>
 *   <li>{@link com.techmart.mdb.InventoryUpdateMDB} — updates local cache</li>
 *   <li>Future: external warehouse management systems</li>
 *   <li>Future: analytics/reporting pipeline</li>
 * </ul>
 * </p>
 *
 * <p><b>JMS 2.0 Simplified API:</b> Uses {@code JMSContext} (injected by the
 * container) instead of the classic {@code ConnectionFactory} + {@code Session}
 * pattern, reducing boilerplate significantly.</p>
 *
 * <p><b>WildFly Configuration:</b> The Topic and ConnectionFactory must be
 * configured in WildFly's standalone.xml. See {@code config/wildfly-standalone-snippet.xml}.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@ApplicationScoped
public class InventoryMessageProducer {

    private static final Logger LOGGER = Logger.getLogger(InventoryMessageProducer.class.getName());

    // ========================================================================
    // JMS Resources (Injected by the Container)
    // ========================================================================

    /**
     * JMSContext — JMS 2.0 simplified API.
     * Injected using @Inject with the connection factory qualifier.
     * The container manages the lifecycle (open/close per transaction).
     *
     * JNDI name: java:/JmsXA (WildFly default pooled connection factory)
     */
    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext jmsContext;

    /**
     * The JMS Topic for inventory updates.
     * JNDI name: java:/jms/topic/InventoryUpdates
     *
     * Configured in WildFly standalone.xml under the messaging subsystem.
     */
    @Resource(lookup = "java:/jms/topic/InventoryUpdates")
    private Topic inventoryTopic;

    // ========================================================================
    // Message Publishing Methods
    // ========================================================================

    /**
     * Publishes an inventory update event to the Topic.
     * All subscribers will receive a copy of this message.
     *
     * <p>The message is a MapMessage containing:
     * <ul>
     *   <li>{@code productId} — the product whose inventory changed</li>
     *   <li>{@code warehouseId} — the warehouse where the change occurred</li>
     *   <li>{@code quantityDelta} — the change amount (+/-)</li>
     *   <li>{@code newQuantity} — the new quantity at this warehouse</li>
     *   <li>{@code eventType} — "STOCK_ADDED", "STOCK_REMOVED", "STOCK_ADJUSTED"</li>
     *   <li>{@code timestamp} — when the event occurred</li>
     * </ul>
     * </p>
     *
     * @param productId     the product ID
     * @param warehouseId   the warehouse ID
     * @param quantityDelta the quantity change (positive for additions, negative for removals)
     * @param newQuantity   the resulting quantity at this warehouse
     * @param eventType     the type of inventory event
     */
    public void publishInventoryUpdate(Long productId, Long warehouseId,
                                        int quantityDelta, int newQuantity,
                                        String eventType) {
        try {
            // Create a MapMessage (structured key-value pairs)
            MapMessage message = jmsContext.createMapMessage();
            message.setLong("productId", productId);
            message.setLong("warehouseId", warehouseId);
            message.setInt("quantityDelta", quantityDelta);
            message.setInt("newQuantity", newQuantity);
            message.setString("eventType", eventType);
            message.setLong("timestamp", System.currentTimeMillis());

            // Set message properties for potential selector-based filtering
            message.setStringProperty("EventType", eventType);
            message.setBooleanProperty("LowStock", newQuantity < 10);

            // Publish to the Topic
            jmsContext.createProducer()
                      .setDeliveryMode(DeliveryMode.PERSISTENT)  // Survive broker restart
                      .setTimeToLive(300_000)                     // 5-minute TTL
                      .send(inventoryTopic, message);

            LOGGER.log(Level.INFO,
                "Published inventory update: product={0}, warehouse={1}, " +
                "delta={2}, newQty={3}, type={4}",
                new Object[]{productId, warehouseId, quantityDelta, newQuantity, eventType});

        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE,
                "Failed to publish inventory update for product " + productId, e);
            throw new RuntimeException("JMS publish failed for inventory update", e);
        }
    }

    /**
     * Convenience method for publishing a stock addition event.
     *
     * @param productId   the product ID
     * @param warehouseId the warehouse ID
     * @param quantity    the quantity added
     * @param newTotal    the new total at this warehouse
     */
    public void publishStockAdded(Long productId, Long warehouseId,
                                   int quantity, int newTotal) {
        publishInventoryUpdate(productId, warehouseId, quantity, newTotal, "STOCK_ADDED");
    }

    /**
     * Convenience method for publishing a stock removal event.
     *
     * @param productId   the product ID
     * @param warehouseId the warehouse ID
     * @param quantity    the quantity removed (positive number)
     * @param newTotal    the new total at this warehouse
     */
    public void publishStockRemoved(Long productId, Long warehouseId,
                                     int quantity, int newTotal) {
        publishInventoryUpdate(productId, warehouseId, -quantity, newTotal, "STOCK_REMOVED");
    }
}
