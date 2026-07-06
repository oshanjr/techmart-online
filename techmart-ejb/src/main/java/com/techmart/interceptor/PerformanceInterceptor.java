package com.techmart.interceptor;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * PerformanceInterceptor — CDI Interceptor for Metrics Collection
 * ============================================================================
 * Intercepts method invocations on beans annotated with {@link PerformanceLogged}
 * and collects performance metrics including:
 * <ul>
 *   <li>Total invocation count per method</li>
 *   <li>Total execution time per method (nanoseconds)</li>
 *   <li>Error count per method</li>
 *   <li>Minimum / Maximum execution time per method</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> All metric counters use {@link AtomicLong} for
 * lock-free concurrent updates. The metrics map itself is a
 * {@link ConcurrentHashMap} for safe concurrent reads/writes.</p>
 *
 * <p><b>Metrics Access:</b> Metrics are exposed via the static
 * {@link #getMetrics()} method, which is called by the
 * {@code MetricsResource} REST endpoint.</p>
 *
 * <p><b>Priority:</b> Set to {@code Interceptor.Priority.APPLICATION + 10}
 * to execute after platform interceptors but before other application
 * interceptors.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@PerformanceLogged
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class PerformanceInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PerformanceInterceptor.class.getName());

    // ========================================================================
    // Metrics Storage
    // ========================================================================

    /**
     * Thread-safe map of method metrics.
     * Key: "ClassName.methodName" (e.g., "ProductCatalogBean.findAllActive")
     * Value: MethodMetrics object holding counters
     */
    private static final ConcurrentHashMap<String, MethodMetrics> METRICS =
        new ConcurrentHashMap<>();

    // ========================================================================
    // Interceptor Logic
    // ========================================================================

    /**
     * Intercepts the method invocation, measures execution time, and records
     * the result (success or error) in the metrics store.
     *
     * @param ctx the invocation context provided by the container
     * @return the result of the intercepted method
     * @throws Exception if the intercepted method throws an exception
     */
    @AroundInvoke
    public Object measurePerformance(InvocationContext ctx) throws Exception {
        // Build the metric key from the target class and method name
        String className = ctx.getTarget().getClass().getSimpleName();
        // Remove proxy suffix if present (e.g., "ProductCatalogBean$Proxy$_$$_" -> "ProductCatalogBean")
        if (className.contains("$")) {
            className = className.substring(0, className.indexOf('$'));
        }
        String methodName = ctx.getMethod().getName();
        String metricKey = className + "." + methodName;

        // Get or create the metrics entry for this method
        MethodMetrics metrics = METRICS.computeIfAbsent(metricKey, k -> new MethodMetrics());

        // Measure execution time using System.nanoTime() for high precision
        long startTime = System.nanoTime();
        try {
            // Proceed with the actual method invocation
            Object result = ctx.proceed();

            // Record successful execution
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.recordSuccess(elapsedNanos);

            // Log slow methods (> 500ms) as warnings for operational visibility
            long elapsedMs = elapsedNanos / 1_000_000;
            if (elapsedMs > 500) {
                LOGGER.log(Level.WARNING,
                    "SLOW METHOD DETECTED: {0} took {1}ms",
                    new Object[]{metricKey, elapsedMs});
            } else if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                    "Performance: {0} completed in {1}ms",
                    new Object[]{metricKey, elapsedMs});
            }

            return result;

        } catch (Exception e) {
            // Record the error and re-throw to preserve the original exception flow
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.recordError(elapsedNanos);

            LOGGER.log(Level.SEVERE,
                "ERROR in {0} after {1}ms: {2}",
                new Object[]{metricKey, elapsedNanos / 1_000_000, e.getMessage()});

            throw e;
        }
    }

    // ========================================================================
    // Public Metrics Access
    // ========================================================================

    /**
     * Returns a snapshot of all collected metrics.
     * Called by {@code MetricsResource} to expose data via REST.
     *
     * @return unmodifiable view of the metrics map
     */
    public static Map<String, MethodMetrics> getMetrics() {
        return new ConcurrentHashMap<>(METRICS);
    }

    /**
     * Resets all collected metrics. Useful for testing and operational resets.
     */
    public static void resetMetrics() {
        METRICS.clear();
    }

    // ========================================================================
    // MethodMetrics — Inner Class for Per-Method Statistics
    // ========================================================================

    /**
     * Holds performance statistics for a single method.
     * All fields use atomic operations for thread safety.
     */
    public static class MethodMetrics implements Serializable {

        private static final long serialVersionUID = 1L;

        private final AtomicLong invocationCount = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);

        /** Records a successful method execution with the given duration */
        public void recordSuccess(long elapsedNanos) {
            invocationCount.incrementAndGet();
            totalTimeNanos.addAndGet(elapsedNanos);
            updateMin(elapsedNanos);
            updateMax(elapsedNanos);
        }

        /** Records a failed method execution with the given duration */
        public void recordError(long elapsedNanos) {
            invocationCount.incrementAndGet();
            errorCount.incrementAndGet();
            totalTimeNanos.addAndGet(elapsedNanos);
            updateMin(elapsedNanos);
            updateMax(elapsedNanos);
        }

        /**
         * Updates the minimum time using a compare-and-swap loop.
         * This is lock-free and safe for concurrent updates.
         */
        private void updateMin(long elapsedNanos) {
            long currentMin;
            do {
                currentMin = minTimeNanos.get();
                if (elapsedNanos >= currentMin) return;
            } while (!minTimeNanos.compareAndSet(currentMin, elapsedNanos));
        }

        /**
         * Updates the maximum time using a compare-and-swap loop.
         */
        private void updateMax(long elapsedNanos) {
            long currentMax;
            do {
                currentMax = maxTimeNanos.get();
                if (elapsedNanos <= currentMax) return;
            } while (!maxTimeNanos.compareAndSet(currentMax, elapsedNanos));
        }

        // Accessors for the MetricsResource endpoint
        public long getInvocationCount() { return invocationCount.get(); }
        public long getTotalTimeNanos() { return totalTimeNanos.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public long getMinTimeNanos() { return minTimeNanos.get(); }
        public long getMaxTimeNanos() { return maxTimeNanos.get(); }

        /** Average execution time in milliseconds */
        public double getAverageTimeMs() {
            long count = invocationCount.get();
            if (count == 0) return 0.0;
            return (totalTimeNanos.get() / (double) count) / 1_000_000.0;
        }

        /** Minimum execution time in milliseconds */
        public double getMinTimeMs() {
            long min = minTimeNanos.get();
            return min == Long.MAX_VALUE ? 0.0 : min / 1_000_000.0;
        }

        /** Maximum execution time in milliseconds */
        public double getMaxTimeMs() {
            return maxTimeNanos.get() / 1_000_000.0;
        }
    }
}
