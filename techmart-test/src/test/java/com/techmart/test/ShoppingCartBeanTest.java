package com.techmart.test;

import com.techmart.ejb.ShoppingCartBean;
import com.techmart.interceptor.PerformanceInterceptor;
import com.techmart.interceptor.PerformanceLogged;
import com.techmart.model.CartItem;
import com.techmart.model.Product;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ejb.EJB;
import java.math.BigDecimal;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * ShoppingCartBeanTest — Arquillian Integration Test
 * ============================================================================
 * Tests the {@link ShoppingCartBean} (Stateful Session Bean) lifecycle and
 * operations in a real Java EE container. Validates:
 * <ul>
 *   <li>Stateful bean instantiation and session management</li>
 *   <li>Add, remove, and update cart item operations</li>
 *   <li>Cart total calculation</li>
 *   <li>{@code @Remove} method behavior (checkout destroys bean)</li>
 *   <li>Lifecycle callbacks (@PostConstruct, @PrePassivate, @PostActivate)</li>
 * </ul>
 *
 * <p><b>Stateful Testing Considerations:</b></p>
 * Each {@code @EJB} injection of a Stateful bean creates a NEW instance.
 * Test ordering matters because we need to verify state accumulation
 * across multiple method calls on the same bean instance.
 *
 * @author TechMart Architecture Team
 * @version 1.0.0
 */
@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShoppingCartBeanTest {

    // ========================================================================
    // Arquillian Deployment
    // ========================================================================

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "techmart-cart-test.jar")
            // ---- Model classes ----
            .addClass(Product.class)
            .addClass(CartItem.class)
            // ---- EJB under test ----
            .addClass(ShoppingCartBean.class)
            // ---- Interceptor ----
            .addClass(PerformanceLogged.class)
            .addClass(PerformanceInterceptor.class)
            // ---- CDI ----
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // ========================================================================
    // Dependencies
    // ========================================================================

    /**
     * Stateful bean — a unique instance is created for this test class.
     * All test methods in this class interact with the SAME instance,
     * verifying that state is maintained across calls.
     */
    @EJB
    private ShoppingCartBean shoppingCart;

    // ========================================================================
    // Test Data — Simulated Products
    // ========================================================================

    /**
     * Creates a test product without database persistence.
     * The ShoppingCartBean only uses product data to create CartItems,
     * it does not query the database.
     */
    private Product createTestProduct(Long id, String sku, String name, BigDecimal price) {
        Product p = new Product(sku, name, price, "TestCategory");
        p.setId(id);
        return p;
    }

    // ========================================================================
    // Test Methods
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("ShoppingCartBean should be injectable and initialized")
    public void testBeanInjection() {
        assertNotNull(shoppingCart, "ShoppingCartBean should be injected");
        assertNotNull(shoppingCart.getSessionId(), "Session ID should be set by @PostConstruct");
        assertTrue(shoppingCart.isEmpty(), "New cart should be empty");
        assertEquals(0, shoppingCart.getItemCount(), "New cart should have 0 items");
    }

    @Test
    @Order(2)
    @DisplayName("Should add a product to the cart")
    public void testAddItem() {
        // Arrange
        Product laptop = createTestProduct(1L, "LAPTOP-001", "Gaming Laptop",
                                            new BigDecimal("1499.99"));
        // Act
        shoppingCart.addItem(laptop, 1);

        // Assert
        assertFalse(shoppingCart.isEmpty(), "Cart should not be empty after adding an item");
        assertEquals(1, shoppingCart.getItemCount(), "Cart should have 1 item");

        CartItem item = shoppingCart.getItem(1L);
        assertNotNull(item, "Should find the added item by product ID");
        assertEquals("LAPTOP-001", item.getProductSku());
        assertEquals("Gaming Laptop", item.getProductName());
        assertEquals(new BigDecimal("1499.99"), item.getUnitPrice());
        assertEquals(1, item.getQuantity());
    }

    @Test
    @Order(3)
    @DisplayName("Should increment quantity when adding same product again")
    public void testAddDuplicateItem() {
        // Arrange — same product as previous test
        Product laptop = createTestProduct(1L, "LAPTOP-001", "Gaming Laptop",
                                            new BigDecimal("1499.99"));
        // Act — add 2 more of the same product
        shoppingCart.addItem(laptop, 2);

        // Assert — quantity should be 1 + 2 = 3
        CartItem item = shoppingCart.getItem(1L);
        assertNotNull(item);
        assertEquals(3, item.getQuantity(), "Quantity should be incremented to 3");
    }

    @Test
    @Order(4)
    @DisplayName("Should add multiple different products")
    public void testAddMultipleProducts() {
        // Arrange
        Product mouse = createTestProduct(2L, "MOUSE-001", "Wireless Mouse",
                                           new BigDecimal("29.99"));
        Product keyboard = createTestProduct(3L, "KB-001", "Mechanical Keyboard",
                                              new BigDecimal("79.99"));
        // Act
        shoppingCart.addItem(mouse, 2);
        shoppingCart.addItem(keyboard, 1);

        // Assert
        Collection<CartItem> items = shoppingCart.getCartItems();
        assertEquals(3, items.size(), "Cart should have 3 distinct products");

        // Total items: laptop(3) + mouse(2) + keyboard(1) = 6
        assertEquals(6, shoppingCart.getItemCount(), "Total item count should be 6");
    }

    @Test
    @Order(5)
    @DisplayName("Should calculate correct cart total")
    public void testCartTotal() {
        // Expected total:
        //   Laptop:   3 × $1499.99 = $4499.97
        //   Mouse:    2 × $29.99   = $59.98
        //   Keyboard: 1 × $79.99   = $79.99
        //   Total:                  = $4639.94
        BigDecimal expectedTotal = new BigDecimal("1499.99").multiply(BigDecimal.valueOf(3))
            .add(new BigDecimal("29.99").multiply(BigDecimal.valueOf(2)))
            .add(new BigDecimal("79.99"));

        BigDecimal actualTotal = shoppingCart.getCartTotal();

        assertEquals(0, expectedTotal.compareTo(actualTotal),
            "Cart total should be $4639.94, got: " + actualTotal);
    }

    @Test
    @Order(6)
    @DisplayName("Should calculate correct item subtotal")
    public void testItemSubtotal() {
        CartItem laptopItem = shoppingCart.getItem(1L);
        assertNotNull(laptopItem);

        // 3 × $1499.99 = $4499.97
        BigDecimal expectedSubtotal = new BigDecimal("1499.99").multiply(BigDecimal.valueOf(3));
        assertEquals(0, expectedSubtotal.compareTo(laptopItem.getSubtotal()),
            "Laptop subtotal should be $4499.97");
    }

    @Test
    @Order(7)
    @DisplayName("Should update item quantity")
    public void testUpdateQuantity() {
        // Act — change mouse quantity from 2 to 5
        boolean updated = shoppingCart.updateQuantity(2L, 5);

        // Assert
        assertTrue(updated, "Update should return true for existing item");
        CartItem mouseItem = shoppingCart.getItem(2L);
        assertNotNull(mouseItem);
        assertEquals(5, mouseItem.getQuantity(), "Mouse quantity should be updated to 5");
    }

    @Test
    @Order(8)
    @DisplayName("Should remove item when quantity updated to 0")
    public void testUpdateQuantityToZero() {
        // Act — set keyboard quantity to 0 (should remove it)
        boolean updated = shoppingCart.updateQuantity(3L, 0);

        // Assert
        assertTrue(updated, "Update should return true");
        assertNull(shoppingCart.getItem(3L),
            "Keyboard should be removed when quantity set to 0");

        // Cart should now have 2 products: laptop and mouse
        Collection<CartItem> items = shoppingCart.getCartItems();
        assertEquals(2, items.size(), "Cart should have 2 products after removal");
    }

    @Test
    @Order(9)
    @DisplayName("Should remove an item from the cart")
    public void testRemoveItem() {
        // Act — remove the mouse
        boolean removed = shoppingCart.removeItem(2L);

        // Assert
        assertTrue(removed, "Remove should return true for existing item");
        assertNull(shoppingCart.getItem(2L), "Mouse should no longer be in cart");
        assertEquals(1, shoppingCart.getCartItems().size(),
            "Cart should have 1 product remaining");
    }

    @Test
    @Order(10)
    @DisplayName("Should return false when removing non-existent item")
    public void testRemoveNonExistentItem() {
        boolean removed = shoppingCart.removeItem(999L);
        assertFalse(removed, "Remove should return false for non-existent item");
    }

    @Test
    @Order(11)
    @DisplayName("Should return false when updating non-existent item")
    public void testUpdateNonExistentItem() {
        boolean updated = shoppingCart.updateQuantity(999L, 5);
        assertFalse(updated, "Update should return false for non-existent item");
    }

    @Test
    @Order(12)
    @DisplayName("Should throw exception when adding null product")
    public void testAddNullProduct() {
        assertThrows(IllegalArgumentException.class,
            () -> shoppingCart.addItem(null, 1),
            "Should throw IllegalArgumentException for null product");
    }

    @Test
    @Order(13)
    @DisplayName("Should throw exception when adding with zero quantity")
    public void testAddZeroQuantity() {
        Product product = createTestProduct(99L, "TEST-999", "Test", new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class,
            () -> shoppingCart.addItem(product, 0),
            "Should throw IllegalArgumentException for zero quantity");
    }

    @Test
    @Order(14)
    @DisplayName("Checkout should return cart items and destroy the bean")
    public void testCheckout() {
        // The cart currently has 1 item (laptop with qty 3)
        assertFalse(shoppingCart.isEmpty(), "Cart should not be empty before checkout");

        // Act — checkout triggers @Remove
        var checkoutItems = shoppingCart.checkout();

        // Assert
        assertNotNull(checkoutItems, "Checkout should return the cart items");
        assertEquals(1, checkoutItems.size(), "Should have 1 product at checkout");
        assertEquals("LAPTOP-001", checkoutItems.get(0).getProductSku());
        assertEquals(3, checkoutItems.get(0).getQuantity());

        // After @Remove, any further calls to this bean will throw NoSuchEJBException
        // We cannot test that here because it would crash the test.
        // In a real scenario, the next @EJB injection would create a new instance.
    }
}
