package com.techmart.mdb;

import com.techmart.ejb.AppConfigBean;
import com.techmart.ejb.NotificationServiceBean;
import com.techmart.model.InventoryRecord;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * InventoryUpdateMDB — Message-Driven Bean (Topic Subscriber)
 * ============================================================================
 * Consumes inventory change events from the {@code InventoryUpdates} JMS Topic
 * (Publish/Subscribe pattern). This MDB is one of potentially many subscribers
 * to the inventory topic.
 *
 * <p><b>Activation Configuration:</b></p>
 * <ul>
 *   <li>{@code destinationType = javax.jms.Topic} — Pub/Sub pattern</li>
 *   <li>{@code destinationLookup} — JNDI name of the topic</li>
 *   <li>{@code subscriptionDurability = Durable} — messages are retained
 *       if this MDB is temporarily offline</li>
 *   <li>{@code subscriptionName} — unique name for durable subscription</li>
 *   <li>{@code maxSession = 5} — max concurrent instances for parallel processing</li>
 * </ul>
 *
 * <p><b>Processing Flow:</b></p>
 * <ol>
 *   <li>Receive inventory change event from the Topic</li>
 *   <li>Update the {@link InventoryRecord} entity in the database</li>
 *   <li>Signal the {@link AppConfigBean} (Singleton) to update its cache</li>
 *   <li>Check for low-stock conditions and trigger alerts</li>
 * </ol>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@MessageDriven(activationConfig = {
    // JMS Topic — Pub/Sub pattern
    @ActivationConfigProperty(
        propertyName = "destinationType",
        propertyValue = "javax.jms.Topic"
    ),
    // JNDI lookup for the inventory updates topic
    @ActivationConfigProperty(
        propertyName = "destinationLookup",
        propertyValue = "java:/jms/topic/InventoryUpdates"
    ),
    // Durable subscription — messages are not lost if MDB is temporarily down
    @ActivationConfigProperty(
        propertyName = "subscriptionDurability",
        propertyValue = "Durable"
    ),
    // Unique subscription name for this MDB
    @ActivationConfigProperty(
        propertyName = "subscriptionName",
        propertyValue = "InventoryUpdateSubscription"
    ),
    // Client ID for durable subscriptions (required by JMS spec)
    @ActivationConfigProperty(
        propertyName = "clientId",
        propertyValue = "TechMartInventoryClient"
    ),
    // Maximum concurrent MDB instances for parallel message processing
    @ActivationConfigProperty(
        propertyName = "maxSession",
        propertyValue = "5"
    ),
    // Acknowledge mode — auto-acknowledge after onMessage completes
    @ActivationConfigProperty(
        propertyName = "acknowledgeMode",
        propertyValue = "Auto-acknowledge"
    )
})
public class InventoryUpdateMDB implements MessageListener {

    private static final Logger LOGGER = Logger.getLogger(InventoryUpdateMDB.class.getName());

    // ========================================================================
    // Dependencies
    // ========================================================================

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    /** Singleton cache — updated incrementally when inventory changes */
    @EJB
    private AppConfigBean appConfigBean;

    /** Notification service — for low-stock alerts */
    @EJB
    private NotificationServiceBean notificationService;

    // ========================================================================
    // Message Processing
    // ========================================================================

    /**
     * Called by the container when an inventory update message arrives.
     * This method runs within a JTA transaction managed by the container.
     * If an exception is thrown, the message will be redelivered (up to the
     * broker's max redelivery count).
     *
     * @param message the JMS message from the inventory topic
     */
    @Override
    public void onMessage(Message message) {
        try {
            // Validate message type
            if (!(message instanceof MapMessage)) {
                LOGGER.log(Level.WARNING,
                    "Received unexpected message type: {0}. Skipping.",
                    message.getClass().getSimpleName());
                return;
            }

            MapMessage mapMessage = (MapMessage) message;

            // Extract message payload
            Long productId = mapMessage.getLong("productId");
            Long warehouseId = mapMessage.getLong("warehouseId");
            int quantityDelta = mapMessage.getInt("quantityDelta");
            int newQuantity = mapMessage.getInt("newQuantity");
            String eventType = mapMessage.getString("eventType");
            long timestamp = mapMessage.getLong("timestamp");

            LOGGER.log(Level.INFO,
                "Processing inventory update: product={0}, warehouse={1}, " +
                "delta={2}, newQty={3}, type={4}",
                new Object[]{productId, warehouseId, quantityDelta, newQuantity, eventType});

            // ----------------------------------------------------------------
            // Step 1: Update the InventoryRecord in the database
            // ----------------------------------------------------------------
            updateInventoryRecord(productId, warehouseId, newQuantity);

            // ----------------------------------------------------------------
            // Step 2: Update the Singleton cache with the new aggregate total
            // ----------------------------------------------------------------
            updateInventoryCache(productId);

            // ----------------------------------------------------------------
            // Step 3: Check for low-stock condition and alert if necessary
            // ----------------------------------------------------------------
            int threshold = appConfigBean.getConfigPropertyAsInt(
                "inventory.low-stock-threshold", 10);

            if (newQuantity < threshold) {
                LOGGER.log(Level.WARNING,
                    "Low stock detected: product={0}, warehouse={1}, quantity={2}",
                    new Object[]{productId, warehouseId, newQuantity});

                // Trigger async low-stock alert
                notificationService.sendLowStockAlertAsync(
                    "PRODUCT-" + productId,  // Would be actual SKU in production
                    "WAREHOUSE-" + warehouseId,
                    newQuantity,
                    threshold
                );
            }

            LOGGER.log(Level.INFO,
                "Inventory update processed successfully: product={0}, warehouse={1}",
                new Object[]{productId, warehouseId});

        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE,
                "Error reading JMS message in InventoryUpdateMDB", e);
            // Throwing RuntimeException causes message redelivery
            throw new RuntimeException("Failed to process inventory update message", e);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Unexpected error in InventoryUpdateMDB", e);
            throw new RuntimeException("Inventory update processing failed", e);
        }
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    /**
     * Updates or creates the InventoryRecord entity for the given
     * product-warehouse combination.
     */
    private void updateInventoryRecord(Long productId, Long warehouseId, int newQuantity) {
        TypedQuery<InventoryRecord> query = em.createNamedQuery(
            "InventoryRecord.findByProductAndWarehouse", InventoryRecord.class);
        query.setParameter("productId", productId);
        query.setParameter("warehouseId", warehouseId);

        List<InventoryRecord> results = query.getResultList();

        if (!results.isEmpty()) {
            // Update existing record
            InventoryRecord record = results.get(0);
            record.setQuantity(newQuantity);
            LOGGER.log(Level.FINE,
                "Updated inventory record: product={0}, warehouse={1}, qty={2}",
                new Object[]{productId, warehouseId, newQuantity});
        } else {
            com.techmart.model.Product product = em.find(com.techmart.model.Product.class, productId);
            com.techmart.model.Warehouse warehouse = em.find(com.techmart.model.Warehouse.class, warehouseId);
            if (product != null && warehouse != null) {
                InventoryRecord record = new InventoryRecord(product, warehouse, newQuantity);
                em.persist(record);
                LOGGER.log(Level.INFO,
                    "Created new inventory record: product={0}, warehouse={1}, qty={2}",
                    new Object[]{productId, warehouseId, newQuantity});
            } else {
                LOGGER.log(Level.WARNING,
                    "Failed to create inventory record: product or warehouse not found (product={0}, warehouse={1})",
                    new Object[]{productId, warehouseId});
            }
        }
    }

    /**
     * Recalculates the aggregate inventory for a product across all warehouses
     * and updates the Singleton cache.
     */
    private void updateInventoryCache(Long productId) {
        try {
            Long totalQuantity = em.createQuery(
                "SELECT COALESCE(SUM(ir.quantity), 0) FROM InventoryRecord ir " +
                "WHERE ir.product.id = :productId", Long.class)
                .setParameter("productId", productId)
                .getSingleResult();

            appConfigBean.updateCachedInventory(productId, totalQuantity.intValue());

            LOGGER.log(Level.FINE,
                "Cache updated: product={0}, totalQuantity={1}",
                new Object[]{productId, totalQuantity});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                "Failed to update cache for product " + productId, e);
            // Non-fatal — cache will be refreshed on the next scheduled interval
        }
    }
}
