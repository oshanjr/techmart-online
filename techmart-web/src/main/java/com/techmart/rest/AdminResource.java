package com.techmart.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * AdminResource — RESTful Endpoint for Admin Authentication
 * ============================================================================
 * Provides simple hardcoded authentication for the TechMart CMS/Admin panel.
 * 
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    private static final Logger LOGGER = Logger.getLogger(AdminResource.class.getName());

    /**
     * Authenticates an admin user and returns a mock session token.
     *
     * <p>Example: {@code POST /api/admin/login} with JSON body</p>
     *
     * @param request the login payload
     * @return 200 OK with token if successful, 401 Unauthorized otherwise
     */
    @POST
    @Path("/login")
    public Response login(Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        LOGGER.log(Level.INFO, "POST /admin/login — Attempting login for user: {0}", username);

        // Simple hardcoded check for the assignment
        if ("admin".equals(username) && "Admin@123".equals(password)) {
            Map<String, String> response = new HashMap<>();
            response.put("token", "admin-session-token-12345");
            response.put("role", "ADMIN");
            return Response.ok(response).build();
        }

        return Response.status(Response.Status.UNAUTHORIZED)
                       .entity("{\"error\": \"Invalid username or password\"}")
                       .build();
    }
}
