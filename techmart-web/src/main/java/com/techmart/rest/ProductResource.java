package com.techmart.rest;

import com.techmart.ejb.ProductCatalogBean;
import com.techmart.model.Product;

import javax.ejb.EJB;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ============================================================================
 * ProductResource — RESTful Endpoint for Product Catalog
 * ============================================================================
 * Exposes the product catalog business logic via a RESTful API.
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>{@code GET /api/products}           — List all active products (paginated)</li>
 *   <li>{@code GET /api/products/{id}}      — Get product by ID</li>
 *   <li>{@code GET /api/products/search}    — Search by keyword</li>
 *   <li>{@code GET /api/products/category/{cat}} — Filter by category</li>
 *   <li>{@code POST /api/products}          — Create a new product</li>
 * </ul>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    private static final Logger LOGGER = Logger.getLogger(ProductResource.class.getName());

    /**
     * ProductCatalogBean injected via @EJB.
     * The container resolves this to the Stateless bean from the EJB module
     * using the JNDI name: java:app/techmart-ejb/ProductCatalogBean
     */
    @EJB
    private ProductCatalogBean productCatalogBean;

    // ========================================================================
    // GET Endpoints
    // ========================================================================

    /**
     * Lists all active products with optional pagination.
     *
     * <p>Example: {@code GET /api/products?page=0&size=20}</p>
     *
     * @param page zero-based page number (default: 0)
     * @param size page size (default: 20, max: 100)
     * @return 200 OK with JSON array of products
     */
    @GET
    public Response getAllProducts(@QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("20") int size) {
        LOGGER.log(Level.FINE, "GET /products?page={0}&size={1}", new Object[]{page, size});

        List<Product> products = productCatalogBean.findAllActive(page, size);
        long totalCount = productCatalogBean.countActive();

        // Return products with pagination metadata in headers
        return Response.ok(products)
                       .header("X-Total-Count", totalCount)
                       .header("X-Page", page)
                       .header("X-Page-Size", size)
                       .build();
    }

    /**
     * Retrieves a single product by its database ID.
     *
     * <p>Example: {@code GET /api/products/42}</p>
     *
     * @param id the product ID
     * @return 200 OK with the product, or 404 Not Found
     */
    @GET
    @Path("/{id}")
    public Response getProductById(@PathParam("id") Long id) {
        LOGGER.log(Level.FINE, "GET /products/{0}", id);

        Product product = productCatalogBean.findById(id);
        if (product == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Product not found\", \"id\": " + id + "}")
                           .build();
        }
        return Response.ok(product).build();
    }

    /**
     * Searches products by keyword across name and description.
     *
     * <p>Example: {@code GET /api/products/search?q=laptop}</p>
     *
     * @param keyword the search term
     * @return 200 OK with matching products
     */
    @GET
    @Path("/search")
    public Response searchProducts(@QueryParam("q") String keyword) {
        LOGGER.log(Level.FINE, "GET /products/search?q={0}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Search query parameter 'q' is required\"}")
                           .build();
        }

        List<Product> results = productCatalogBean.searchByKeyword(keyword);
        return Response.ok(results)
                       .header("X-Total-Count", results.size())
                       .build();
    }

    /**
     * Retrieves products filtered by category.
     *
     * <p>Example: {@code GET /api/products/category/Electronics}</p>
     *
     * @param category the category name
     * @return 200 OK with products in the category
     */
    @GET
    @Path("/category/{category}")
    public Response getProductsByCategory(@PathParam("category") String category) {
        LOGGER.log(Level.FINE, "GET /products/category/{0}", category);

        List<Product> products = productCatalogBean.findByCategory(category);
        return Response.ok(products)
                       .header("X-Total-Count", products.size())
                       .build();
    }

    // ========================================================================
    // POST Endpoints
    // ========================================================================

    /**
     * Creates a new product in the catalog.
     *
     * <p>Example: {@code POST /api/products} with JSON body</p>
     *
     * @param product the product data (JSON request body)
     * @return 201 Created with the created product
     */
    @POST
    public Response createProduct(Product product) {
        LOGGER.log(Level.INFO, "POST /products — Creating: {0}", product.getSku());

        try {
            Product created = productCatalogBean.createProduct(product);
            return Response.status(Response.Status.CREATED)
                           .entity(created)
                           .build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create product", e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    // ========================================================================
    // PUT / DELETE Endpoints (CMS)
    // ========================================================================

    /**
     * Updates an existing product.
     *
     * @param id the product ID
     * @param product the updated product data
     * @return 200 OK with the updated product
     */
    @PUT
    @Path("/{id}")
    public Response updateProduct(@PathParam("id") Long id, Product product) {
        LOGGER.log(Level.INFO, "PUT /products/{0}", id);
        
        Product existing = productCatalogBean.findById(id);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Product not found\"}")
                           .build();
        }
        
        product.setId(id); // Ensure the ID matches the path
        try {
            Product updated = productCatalogBean.updateProduct(product);
            return Response.ok(updated).build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to update product", e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    /**
     * Soft-deletes a product by deactivating it.
     *
     * @param id the product ID
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    public Response deleteProduct(@PathParam("id") Long id) {
        LOGGER.log(Level.INFO, "DELETE /products/{0}", id);
        
        boolean deactivated = productCatalogBean.deactivateProduct(id);
        if (!deactivated) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\": \"Product not found\"}")
                           .build();
        }
        
        return Response.noContent().build();
    }
}
