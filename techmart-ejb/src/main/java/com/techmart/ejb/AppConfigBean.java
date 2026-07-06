package com.techmart.ejb;

import com.techmart.interceptor.PerformanceLogged;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * AppConfigBean — Singleton Session Bean
 * ============================================================================
 * Application-wide configuration manager and inventory cache. As a
 * {@code @Singleton} with {@code @Startup}, this bean is created once when
 * the application deploys and persists for the entire application lifecycle.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Maintains cached, aggregated inventory summaries per product</li>
 *   <li>Provides application configuration properties</li>
 *   <li>Automatically refreshes inventory cache on a schedule</li>
 * </ul>
 *
 * <p><b>Concurrency Management:</b></p>
 * Uses {@code @ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)}
 * which is the default. The container ensures thread safety:
 * <ul>
 *   <li>{@code @Lock(READ)} — allows concurrent read access (no blocking)</li>
 *   <li>{@code @Lock(WRITE)} — exclusive access for cache mutations</li>
 * </ul>
 *
 * <p><b>JNDI Binding:</b></p>
 * <pre>
 *   &#64;EJB
 *   private AppConfigBean appConfig;  // Singleton — same instance everywhere
 * </pre>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Singleton
@Startup  // Created at application deployment time
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@PerformanceLogged
public class AppConfigBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AppConfigBean.class.getName());

    // ========================================================================
    // Dependencies
    // ========================================================================

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    // ========================================================================
    // Cached State
    // ========================================================================

    /**
     * Aggregated inventory cache.
     * Key: Product ID
     * Value: Total quantity across all warehouses
     *
     * This cache avoids expensive cross-warehouse inventory queries
     * on every product page load. Refreshed periodically and on JMS events.
     */
    private ConcurrentHashMap<Long, Integer> inventoryCache;

    /**
     * Application configuration properties.
     * Key-value pairs for runtime configuration that can be changed
     * without redeployment.
     */
    private Map<String, String> configProperties;

    /** Timestamp of the last cache refresh (for monitoring) */
    private long lastCacheRefreshTime;

    /** Count of cache refreshes since application start */
    private long cacheRefreshCount;

    // ========================================================================
    // Lifecycle Callbacks
    // ========================================================================

    /**
     * Called once at application startup.
     * Initializes the configuration and performs the first cache load.
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("=== TechMart AppConfigBean Initializing ===");

        // Initialize configuration with defaults
        configProperties = new ConcurrentHashMap<>();
        configProperties.put("app.name", "TechMart Online");
        configProperties.put("app.version", "1.0.0");
        configProperties.put("inventory.low-stock-threshold", "10");
        configProperties.put("order.max-items-per-order", "50");
        configProperties.put("cart.session-timeout-minutes", "30");
        configProperties.put("notification.email-enabled", "true");
        configProperties.put("notification.sms-enabled", "false");

        // Initialize inventory cache
        inventoryCache = new ConcurrentHashMap<>();
        refreshInventoryCache();

        LOGGER.info("=== TechMart AppConfigBean Initialized Successfully ===");
    }

    /**
     * Called once at application shutdown.
     * Logs final statistics for operational review.
     */
    @PreDestroy
    public void shutdown() {
        LOGGER.log(Level.INFO,
            "AppConfigBean shutting down. Cache refreshes performed: {0}",
            cacheRefreshCount);
    }

    // ========================================================================
    // Scheduled Cache Refresh
    // ========================================================================

    /**
     * Automatically refreshes the inventory cache every 5 minutes.
     *
     * The {@code @Schedule} annotation creates a container-managed timer.
     * {@code persistent = false} means the timer is not preserved across
     * server restarts (it will be recreated on startup).
     *
     * @param timer the container-managed timer (auto-injected)
     */
    @Schedule(minute = "*/5", hour = "*", persistent = false)
    @Lock(LockType.WRITE)  // Exclusive access during cache refresh
    public void scheduledCacheRefresh(Timer timer) {
        LOGGER.log(Level.FINE, "Scheduled inventory cache refresh triggered");
        refreshInventoryCache();
    }

    // ========================================================================
    // Inventory Cache — Write Operations
    // ========================================================================

    /**
     * Refreshes the entire inventory cache from the database.
     * This queries the aggregated quantity per product across all warehouses.
     *
     * <p>WRITE lock ensures no concurrent reads see a partially-updated cache.</p>
     */
    @Lock(LockType.WRITE)
    public void refreshInventoryCache() {
        LOGGER.log(Level.INFO, "Refreshing inventory cache from database...");
        long startTime = System.nanoTime();

        try {
            // Query aggregated inventory: SUM(quantity) grouped by product_id
            @SuppressWarnings("unchecked")
            var results = em.createQuery(
                "SELECT ir.product.id, SUM(ir.quantity) " +
                "FROM InventoryRecord ir " +
                "GROUP BY ir.product.id"
            ).getResultList();

            // Build new cache atomically
            ConcurrentHashMap<Long, Integer> newCache = new ConcurrentHashMap<>();
            for (Object row : results) {
                Object[] cols = (Object[]) row;
                Long productId = (Long) cols[0];
                Long totalQty = (Long) cols[1];
                newCache.put(productId, totalQty.intValue());
            }

            // Atomic swap — readers will see either old or new cache, never partial
            this.inventoryCache = newCache;
            this.lastCacheRefreshTime = System.currentTimeMillis();
            this.cacheRefreshCount++;

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            LOGGER.log(Level.INFO,
                "Inventory cache refreshed: {0} products cached in {1}ms",
                new Object[]{newCache.size(), elapsedMs});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                "Failed to refresh inventory cache: " + e.getMessage(), e);
            // Keep the existing cache on failure — stale data is better than no data
        }
    }

    /**
     * Updates a single product's cached inventory level.
     * Called by the InventoryUpdateMDB when a JMS inventory event is received,
     * avoiding a full cache refresh for individual changes.
     *
     * @param productId   the product ID
     * @param newQuantity the new aggregated quantity
     */
    @Lock(LockType.WRITE)
    public void updateCachedInventory(Long productId, int newQuantity) {
        inventoryCache.put(productId, newQuantity);
        LOGGER.log(Level.FINE,
            "Cache updated for product {0}: quantity={1}",
            new Object[]{productId, newQuantity});
    }

    // ========================================================================
    // Inventory Cache — Read Operations
    // ========================================================================

    /**
     * Retrieves the cached total inventory for a specific product.
     * READ lock allows concurrent access from multiple threads.
     *
     * @param productId the product ID
     * @return total quantity across all warehouses, or 0 if not cached
     */
    @Lock(LockType.READ)
    public int getCachedInventory(Long productId) {
        return inventoryCache.getOrDefault(productId, 0);
    }

    /**
     * Returns a snapshot of the entire inventory cache.
     * Useful for the admin dashboard / metrics endpoint.
     *
     * @return unmodifiable map of product ID to total quantity
     */
    @Lock(LockType.READ)
    public Map<Long, Integer> getInventorySummary() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(inventoryCache));
    }

    /**
     * Returns the number of products currently in the inventory cache.
     *
     * @return cached product count
     */
    @Lock(LockType.READ)
    public int getCachedProductCount() {
        return inventoryCache.size();
    }

    // ========================================================================
    // Configuration Properties — Read Operations
    // ========================================================================

    /**
     * Retrieves a configuration property by key.
     *
     * @param key          the property key
     * @param defaultValue fallback value if the key is not found
     * @return the property value, or the default
     */
    @Lock(LockType.READ)
    public String getConfigProperty(String key, String defaultValue) {
        return configProperties.getOrDefault(key, defaultValue);
    }

    /**
     * Retrieves a configuration property as an integer.
     *
     * @param key          the property key
     * @param defaultValue fallback value if the key is not found or not a number
     * @return the property value as an integer
     */
    @Lock(LockType.READ)
    public int getConfigPropertyAsInt(String key, int defaultValue) {
        String value = configProperties.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns all configuration properties.
     *
     * @return unmodifiable map of configuration key-value pairs
     */
    @Lock(LockType.READ)
    public Map<String, String> getAllConfigProperties() {
        return Collections.unmodifiableMap(configProperties);
    }

    // ========================================================================
    // Configuration Properties — Write Operations
    // ========================================================================

    /**
     * Updates a configuration property at runtime.
     * WRITE lock ensures atomic update.
     *
     * @param key   the property key
     * @param value the new value
     */
    @Lock(LockType.WRITE)
    public void setConfigProperty(String key, String value) {
        String oldValue = configProperties.put(key, value);
        LOGGER.log(Level.INFO,
            "Configuration updated: {0} = '{1}' (was: '{2}')",
            new Object[]{key, value, oldValue});
    }

    // ========================================================================
    // Monitoring Accessors
    // ========================================================================

    /**
     * Returns the timestamp of the last inventory cache refresh.
     * Used by the MetricsResource for monitoring.
     *
     * @return epoch milliseconds of last refresh, or 0 if never refreshed
     */
    @Lock(LockType.READ)
    public long getLastCacheRefreshTime() {
        return lastCacheRefreshTime;
    }

    /**
     * Returns the total number of cache refreshes since application start.
     *
     * @return refresh count
     */
    @Lock(LockType.READ)
    public long getCacheRefreshCount() {
        return cacheRefreshCount;
    }
}
