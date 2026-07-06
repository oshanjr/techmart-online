package com.techmart.ejb;

import com.techmart.interceptor.PerformanceLogged;

import javax.annotation.PostConstruct;
import javax.ejb.*;
import java.math.BigDecimal;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * NotificationServiceBean — Stateless Session Bean with Async Notifications
 * ============================================================================
 * Handles asynchronous delivery of customer notifications including:
 * <ul>
 *   <li>Order confirmation emails</li>
 *   <li>Shipping status update emails</li>
 *   <li>Inventory low-stock alerts (internal)</li>
 * </ul>
 *
 * <p><b>Asynchronous Design:</b> All notification methods use
 * {@code @Asynchronous} to avoid blocking the calling thread. Email/SMS
 * delivery is inherently slow (network I/O), so async processing is
 * critical for maintaining sub-second response times.</p>
 *
 * <p><b>Error Handling & Retry:</b> Failed notifications are logged with
 * full context. In a production system, you would integrate with a retry
 * queue (DLQ pattern) or a notification service like Amazon SES, SendGrid, etc.
 * The current implementation includes basic retry logic with configurable
 * max attempts.</p>
 *
 * <p><b>Future Integration Points:</b></p>
 * <ul>
 *   <li>JavaMail API (javax.mail) for SMTP delivery</li>
 *   <li>SMS gateway integration (Twilio, SNS)</li>
 *   <li>Push notification services</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Stateless
@PerformanceLogged
public class NotificationServiceBean {

    private static final Logger LOGGER = Logger.getLogger(NotificationServiceBean.class.getName());

    /** Maximum retry attempts for failed notification delivery */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Delay between retries in milliseconds */
    private static final long RETRY_DELAY_MS = 2000;

    // ========================================================================
    // Lifecycle
    // ========================================================================

    @PostConstruct
    public void initialize() {
        LOGGER.info("NotificationServiceBean instance initialized in EJB pool");
    }

    // ========================================================================
    // Asynchronous Notification Methods
    // ========================================================================

    /**
     * Sends an order confirmation email asynchronously.
     *
     * <p>This method is called by {@link OrderProcessingBean} after an order
     * is successfully created. The caller does not wait for the email to
     * be delivered — it receives a {@code Future<Boolean>} immediately.</p>
     *
     * @param customerEmail the recipient's email address
     * @param customerName  the customer's display name
     * @param orderId       the order ID for reference
     * @param totalAmount   the order total for the confirmation
     * @return Future containing true if notification was sent successfully
     */
    @Asynchronous
    @AccessTimeout(value = 60, unit = java.util.concurrent.TimeUnit.SECONDS)
    public Future<Boolean> sendOrderConfirmationAsync(String customerEmail,
                                                       String customerName,
                                                       Long orderId,
                                                       BigDecimal totalAmount) {
        LOGGER.log(Level.INFO,
            "Sending order confirmation: email={0}, orderId={1}, total={2}",
            new Object[]{customerEmail, orderId, totalAmount});

        boolean success = sendWithRetry(() -> {
            // ================================================================
            // TODO: Replace with actual JavaMail or third-party email service
            // ================================================================
            // Example JavaMail integration:
            //   Session mailSession = (Session) ctx.lookup("java:jboss/mail/TechMart");
            //   MimeMessage message = new MimeMessage(mailSession);
            //   message.setFrom(new InternetAddress("orders@techmart.com"));
            //   message.setRecipients(Message.RecipientType.TO, customerEmail);
            //   message.setSubject("TechMart Order Confirmation #" + orderId);
            //   message.setText(buildOrderConfirmationBody(customerName, orderId, totalAmount));
            //   Transport.send(message);

            // Simulated email delivery (replace with actual implementation)
            LOGGER.log(Level.INFO,
                "[EMAIL SIMULATION] To: {0}, Subject: Order Confirmation #{1}, Amount: ${2}",
                new Object[]{customerEmail, orderId, totalAmount});

            // Simulate network latency for realistic performance testing
            Thread.sleep(100);

        }, "Order Confirmation for #" + orderId);

        return new AsyncResult<>(success);
    }

    /**
     * Sends a shipping status update notification asynchronously.
     *
     * @param customerEmail the recipient's email address
     * @param orderId       the order ID
     * @param newStatus     the new shipping status
     * @return Future containing true if notification was sent successfully
     */
    @Asynchronous
    @AccessTimeout(value = 60, unit = java.util.concurrent.TimeUnit.SECONDS)
    public Future<Boolean> sendShippingUpdateAsync(String customerEmail,
                                                    Long orderId,
                                                    String newStatus) {
        LOGGER.log(Level.INFO,
            "Sending shipping update: email={0}, orderId={1}, status={2}",
            new Object[]{customerEmail, orderId, newStatus});

        boolean success = sendWithRetry(() -> {
            // Simulated email delivery
            LOGGER.log(Level.INFO,
                "[EMAIL SIMULATION] To: {0}, Subject: Order #{1} Status Update: {2}",
                new Object[]{customerEmail, orderId, newStatus});

            Thread.sleep(100);

        }, "Shipping Update for #" + orderId);

        return new AsyncResult<>(success);
    }

    /**
     * Sends an internal low-stock alert notification asynchronously.
     * This is triggered when inventory falls below the configured threshold.
     *
     * @param productSku    the product SKU with low stock
     * @param warehouseCode the warehouse code
     * @param currentStock  the current stock level
     * @param threshold     the configured threshold
     * @return Future containing true if alert was sent successfully
     */
    @Asynchronous
    @AccessTimeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    public Future<Boolean> sendLowStockAlertAsync(String productSku,
                                                   String warehouseCode,
                                                   int currentStock,
                                                   int threshold) {
        LOGGER.log(Level.WARNING,
            "LOW STOCK ALERT: product={0}, warehouse={1}, stock={2}, threshold={3}",
            new Object[]{productSku, warehouseCode, currentStock, threshold});

        boolean success = sendWithRetry(() -> {
            // Simulated internal alert (would go to ops team email or Slack/PagerDuty)
            LOGGER.log(Level.INFO,
                "[ALERT SIMULATION] Low stock for {0} at warehouse {1}: {2} units remaining",
                new Object[]{productSku, warehouseCode, currentStock});

            Thread.sleep(50);

        }, "Low Stock Alert for " + productSku);

        return new AsyncResult<>(success);
    }

    // ========================================================================
    // Retry Logic
    // ========================================================================

    /**
     * Executes a notification action with retry logic.
     * Retries up to {@code MAX_RETRY_ATTEMPTS} times with exponential backoff.
     *
     * @param action      the notification action to execute
     * @param description human-readable description for logging
     * @return true if the action succeeded within the retry limit
     */
    private boolean sendWithRetry(NotificationAction action, String description) {
        int attempts = 0;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                attempts++;
                action.execute();

                LOGGER.log(Level.FINE,
                    "Notification sent successfully: {0} (attempt {1})",
                    new Object[]{description, attempts});
                return true;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING,
                    "Notification interrupted: {0}", description);
                return false;

            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                    "Notification attempt {0}/{1} failed for {2}: {3}",
                    new Object[]{attempts, MAX_RETRY_ATTEMPTS, description, e.getMessage()});

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        // Exponential backoff: 2s, 4s, 8s...
                        long delay = RETRY_DELAY_MS * (long) Math.pow(2, attempts - 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }

        LOGGER.log(Level.SEVERE,
            "Notification PERMANENTLY FAILED after {0} attempts: {1}",
            new Object[]{MAX_RETRY_ATTEMPTS, description});
        return false;
    }

    /**
     * Functional interface for notification actions that may throw exceptions.
     */
    @FunctionalInterface
    private interface NotificationAction {
        void execute() throws Exception;
    }
}
