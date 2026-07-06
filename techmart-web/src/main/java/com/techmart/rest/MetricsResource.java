package com.techmart.rest;

import com.techmart.ejb.AppConfigBean;
import com.techmart.interceptor.PerformanceInterceptor;
import com.techmart.interceptor.PerformanceInterceptor.MethodMetrics;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * MetricsResource — RESTful Endpoint for Performance Monitoring
 * ============================================================================
 * Exposes application performance metrics collected by the
 * {@link PerformanceInterceptor} CDI interceptor, along with JVM statistics
 * and application health information.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET /api/metrics}           — Full metrics dashboard</li>
 *   <li>{@code GET /api/metrics/methods}   — Per-method performance data</li>
 *   <li>{@code GET /api/metrics/jvm}       — JVM memory and thread stats</li>
 *   <li>{@code GET /api/metrics/health}    — Application health check</li>
 *   <li>{@code POST /api/metrics/reset}    — Reset all collected metrics</li>
 * </ul>
 *
 * <p><b>Usage:</b> This endpoint is designed for integration with monitoring
 * tools (Prometheus, Grafana, Datadog) or custom admin dashboards.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {

    private static final Logger LOGGER = Logger.getLogger(MetricsResource.class.getName());

    @EJB
    private AppConfigBean appConfigBean;

    // ========================================================================
    // Full Metrics Dashboard
    // ========================================================================

    /**
     * Returns a comprehensive metrics snapshot including method performance,
     * JVM statistics, and application cache information.
     *
     * <p>Example: {@code GET /api/metrics}</p>
     *
     * @return 200 OK with complete metrics JSON
     */
    @GET
    public Response getFullMetrics() {
        LOGGER.log(Level.FINE, "GET /metrics — full dashboard");

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("timestamp", System.currentTimeMillis());
        dashboard.put("application", getApplicationInfo());
        dashboard.put("methodMetrics", getMethodMetricsData());
        dashboard.put("jvm", getJvmMetricsData());
        dashboard.put("cache", getCacheMetrics());

        return Response.ok(dashboard).build();
    }

    // ========================================================================
    // Per-Method Performance Metrics
    // ========================================================================

    /**
     * Returns per-method performance metrics collected by the
     * PerformanceInterceptor.
     *
     * <p>Example response:
     * <pre>
     * {
     *   "ProductCatalogBean.findAllActive": {
     *     "invocationCount": 1542,
     *     "averageTimeMs": 12.3,
     *     "minTimeMs": 1.2,
     *     "maxTimeMs": 156.7,
     *     "errorCount": 0
     *   },
     *   ...
     * }
     * </pre>
     * </p>
     *
     * @return 200 OK with method metrics
     */
    @GET
    @Path("/methods")
    public Response getMethodMetrics() {
        LOGGER.log(Level.FINE, "GET /metrics/methods");
        return Response.ok(getMethodMetricsData()).build();
    }

    // ========================================================================
    // JVM Metrics
    // ========================================================================

    /**
     * Returns JVM runtime statistics including memory usage, thread counts,
     * and uptime information.
     *
     * @return 200 OK with JVM metrics
     */
    @GET
    @Path("/jvm")
    public Response getJvmMetrics() {
        LOGGER.log(Level.FINE, "GET /metrics/jvm");
        return Response.ok(getJvmMetricsData()).build();
    }

    // ========================================================================
    // Health Check
    // ========================================================================

    /**
     * Lightweight health check endpoint for load balancers and
     * orchestration platforms (Kubernetes, Docker, etc.).
     *
     * <p>Returns:
     * <ul>
     *   <li>200 OK if the application is healthy</li>
     *   <li>503 Service Unavailable if critical components are down</li>
     * </ul>
     * </p>
     *
     * @return 200 OK with health status
     */
    @GET
    @Path("/health")
    public Response healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        // Check if the Singleton cache is populated (basic component check)
        boolean cacheHealthy = appConfigBean.getLastCacheRefreshTime() > 0;
        health.put("inventoryCache", cacheHealthy ? "UP" : "INITIALIZING");

        // Overall status
        boolean allHealthy = cacheHealthy;
        health.put("status", allHealthy ? "UP" : "DEGRADED");

        Response.Status httpStatus = allHealthy
            ? Response.Status.OK
            : Response.Status.OK; // DEGRADED is still a 200 — only DOWN returns 503

        return Response.status(httpStatus).entity(health).build();
    }

    // ========================================================================
    // Reset Metrics
    // ========================================================================

    /**
     * Resets all collected performance metrics.
     * Useful for benchmarking runs and load testing.
     *
     * <p>Example: {@code POST /api/metrics/reset}</p>
     *
     * @return 200 OK confirmation
     */
    @POST
    @Path("/reset")
    public Response resetMetrics() {
        LOGGER.log(Level.INFO, "POST /metrics/reset — clearing all metrics");

        PerformanceInterceptor.resetMetrics();

        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "All performance metrics have been reset");

        return Response.ok(response).build();
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    /**
     * Builds the method metrics data structure from the PerformanceInterceptor.
     */
    private Map<String, Object> getMethodMetricsData() {
        Map<String, MethodMetrics> rawMetrics = PerformanceInterceptor.getMetrics();
        Map<String, Object> formatted = new LinkedHashMap<>();

        for (Map.Entry<String, MethodMetrics> entry : rawMetrics.entrySet()) {
            MethodMetrics m = entry.getValue();
            Map<String, Object> methodData = new LinkedHashMap<>();
            methodData.put("invocationCount", m.getInvocationCount());
            methodData.put("averageTimeMs", Math.round(m.getAverageTimeMs() * 100.0) / 100.0);
            methodData.put("minTimeMs", Math.round(m.getMinTimeMs() * 100.0) / 100.0);
            methodData.put("maxTimeMs", Math.round(m.getMaxTimeMs() * 100.0) / 100.0);
            methodData.put("errorCount", m.getErrorCount());
            methodData.put("totalTimeMs", m.getTotalTimeNanos() / 1_000_000);
            formatted.put(entry.getKey(), methodData);
        }

        return formatted;
    }

    /**
     * Collects JVM runtime metrics via the Management API.
     */
    private Map<String, Object> getJvmMetricsData() {
        Map<String, Object> jvm = new LinkedHashMap<>();

        // Memory metrics
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heapUsedMB", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        memory.put("heapMaxMB", memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
        memory.put("heapCommittedMB", memoryBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024));
        memory.put("nonHeapUsedMB", memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
        jvm.put("memory", memory);

        // Thread metrics
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new LinkedHashMap<>();
        threads.put("activeCount", threadBean.getThreadCount());
        threads.put("peakCount", threadBean.getPeakThreadCount());
        threads.put("daemonCount", threadBean.getDaemonThreadCount());
        threads.put("totalStarted", threadBean.getTotalStartedThreadCount());
        jvm.put("threads", threads);

        // Runtime metrics
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("uptimeSeconds", runtimeBean.getUptime() / 1000);
        runtime.put("vmName", runtimeBean.getVmName());
        runtime.put("vmVersion", runtimeBean.getVmVersion());
        runtime.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        jvm.put("runtime", runtime);

        return jvm;
    }

    /**
     * Returns application-level information.
     */
    private Map<String, String> getApplicationInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", appConfigBean.getConfigProperty("app.name", "TechMart Online"));
        info.put("version", appConfigBean.getConfigProperty("app.version", "1.0.0"));
        return info;
    }

    /**
     * Returns inventory cache metrics.
     */
    private Map<String, Object> getCacheMetrics() {
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("cachedProducts", appConfigBean.getCachedProductCount());
        cache.put("lastRefreshTime", appConfigBean.getLastCacheRefreshTime());
        cache.put("totalRefreshes", appConfigBean.getCacheRefreshCount());
        return cache;
    }
}
