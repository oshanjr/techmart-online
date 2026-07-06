package com.techmart.interceptor;

import javax.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * ============================================================================
 * @PerformanceLogged — Custom Interceptor Binding
 * ============================================================================
 * Marks a class or method for automatic performance monitoring. When applied,
 * the {@link PerformanceInterceptor} will measure execution time and record
 * metrics that are exposed via the {@code /api/metrics} REST endpoint.
 *
 * <p><b>Usage:</b></p>
 * <pre>
 *   // Apply to an entire bean — all business methods will be monitored
 *   &#64;PerformanceLogged
 *   &#64;Stateless
 *   public class ProductCatalogBean { ... }
 *
 *   // Or apply to a specific method
 *   &#64;PerformanceLogged
 *   public List&lt;Product&gt; searchByKeyword(String keyword) { ... }
 * </pre>
 *
 * <p><b>CDI Integration:</b> This annotation is registered as an interceptor
 * binding in {@code beans.xml}. The interceptor is activated globally across
 * the EJB module.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PerformanceLogged {
}
