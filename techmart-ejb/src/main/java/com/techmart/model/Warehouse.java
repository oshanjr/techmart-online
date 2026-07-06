package com.techmart.model;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * Warehouse Entity
 * ============================================================================
 * Represents a physical warehouse or distribution center in the TechMart
 * fulfillment network. Warehouses are the source of inventory records and
 * are referenced during order fulfillment to determine the optimal shipping
 * origin.
 *
 * <p><b>Multi-Warehouse Architecture:</b> The system supports multiple
 * warehouses for geographic distribution. Inventory is tracked per-product
 * per-warehouse via {@link InventoryRecord}.</p>
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "warehouses",
    indexes = {
        @Index(name = "idx_warehouse_code", columnList = "code", unique = true)
    }
)
@NamedQueries({
    @NamedQuery(
        name = "Warehouse.findAll",
        query = "SELECT w FROM Warehouse w WHERE w.active = true ORDER BY w.name"
    ),
    @NamedQuery(
        name = "Warehouse.findByCode",
        query = "SELECT w FROM Warehouse w WHERE w.code = :code"
    )
})
public class Warehouse implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // Primary Key
    // ========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // ========================================================================
    // Business Fields
    // ========================================================================

    /** Unique warehouse code (e.g., "WH-EAST-01") */
    @NotBlank(message = "Warehouse code is required")
    @Size(max = 30, message = "Warehouse code must not exceed 30 characters")
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    /** Human-readable warehouse name */
    @NotBlank(message = "Warehouse name is required")
    @Size(max = 100, message = "Warehouse name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Physical location / city */
    @Size(max = 255, message = "Location must not exceed 255 characters")
    @Column(name = "location", length = 255)
    private String location;

    /** Whether this warehouse is currently operational */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ========================================================================
    // Audit Fields
    // ========================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ========================================================================
    // Constructors
    // ========================================================================

    public Warehouse() {
    }

    public Warehouse(String code, String name, String location) {
        this.code = code;
        this.name = name;
        this.location = location;
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Warehouse{" +
               "id=" + id +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", location='" + location + '\'' +
               ", active=" + active +
               '}';
    }
}
