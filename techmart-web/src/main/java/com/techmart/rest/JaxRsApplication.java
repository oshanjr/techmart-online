package com.techmart.rest;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * ============================================================================
 * JAX-RS Application Activator
 * ============================================================================
 * Activates JAX-RS (Java API for RESTful Web Services) for the TechMart
 * web module. The {@code @ApplicationPath} annotation sets the base URI
 * for all REST endpoints.
 *
 * <p><b>Base URL:</b> {@code http://<host>:<port>/techmart/api/}</p>
 *
 * <p><b>Available Endpoints:</b></p>
 * <ul>
 *   <li>{@code /api/products}   — Product catalog operations</li>
 *   <li>{@code /api/cart}       — Shopping cart management</li>
 *   <li>{@code /api/orders}     — Order processing</li>
 *   <li>{@code /api/inventory}  — Inventory management</li>
 *   <li>{@code /api/metrics}    — Performance monitoring</li>
 * </ul>
 *
 * <p><b>No web.xml Required:</b> JAX-RS resources are auto-discovered
 * by the container via classpath scanning. This empty Application subclass
 * is sufficient to activate the JAX-RS runtime.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@ApplicationPath("/api")
public class JaxRsApplication extends Application {
    // No methods to override — all resources are auto-discovered via @Path annotations
}
