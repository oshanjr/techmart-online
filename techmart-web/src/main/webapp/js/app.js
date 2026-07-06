/**
 * TechMart Online - Frontend Logic
 * Interacts with the JAX-RS REST APIs.
 */

const API_BASE = '/techmart/api';

// State
let state = {
    products: [],
    cart: {
        items: [],
        itemCount: 0,
        cartTotal: 0,
        isEmpty: true
    },
    currentCategory: 'all'
};

// DOM Elements
const els = {
    productsGrid: document.getElementById('products-grid'),
    loadingSpinner: document.getElementById('loading-spinner'),
    filterBtns: document.querySelectorAll('.filter-btn'),
    
    // Cart
    cartToggle: document.getElementById('cart-toggle'),
    cartBadge: document.getElementById('cart-badge'),
    cartSidebar: document.getElementById('cart-sidebar'),
    cartClose: document.getElementById('cart-close'),
    cartOverlay: document.getElementById('cart-overlay'),
    cartItems: document.getElementById('cart-items'),
    cartTotal: document.getElementById('cart-total-price'),
    checkoutBtn: document.getElementById('checkout-btn'),
    
    // Modals
    checkoutModal: document.getElementById('checkout-modal'),
    modalClose: document.getElementById('modal-close'),
    checkoutForm: document.getElementById('checkout-form'),
    checkoutItemCount: document.getElementById('checkout-item-count'),
    checkoutTotal: document.getElementById('checkout-total'),
    submitOrderBtn: document.getElementById('submit-order-btn'),
    
    successModal: document.getElementById('success-modal'),
    successCloseBtn: document.getElementById('success-close-btn'),
    
    toast: document.getElementById('toast'),
    toastMessage: document.getElementById('toast-message'),

    // Order History
    navOrderHistory: document.getElementById('nav-order-history'),
    orderHistoryModal: document.getElementById('order-history-modal'),
    orderModalClose: document.getElementById('order-modal-close'),
    orderSearchEmail: document.getElementById('order-search-email'),
    btnSearchOrders: document.getElementById('btn-search-orders'),
    orderHistoryResults: document.getElementById('order-history-results'),
    orderHistoryLoading: document.getElementById('order-history-loading'),

    // Single Product
    productModal: document.getElementById('product-modal'),
    productModalClose: document.getElementById('product-modal-close'),
    spCategory: document.getElementById('sp-category'),
    spTitle: document.getElementById('sp-title'),
    spSku: document.getElementById('sp-sku'),
    spDesc: document.getElementById('sp-desc'),
    spPrice: document.getElementById('sp-price'),
    spStock: document.getElementById('sp-stock'),
    spAddToCart: document.getElementById('sp-add-to-cart')
};

// ==========================================
// Initialization
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

async function initApp() {
    setupEventListeners();
    await fetchCart();
    await loadProducts();
}

// ==========================================
// Event Listeners
// ==========================================
function setupEventListeners() {
    // Category Filters
    els.filterBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            els.filterBtns.forEach(b => b.classList.remove('active'));
            e.target.classList.add('active');
            state.currentCategory = e.target.dataset.category;
            renderProducts();
        });
    });

    // Cart Sidebar Toggles
    els.cartToggle.addEventListener('click', openCart);
    els.cartClose.addEventListener('click', closeCart);
    els.cartOverlay.addEventListener('click', () => {
        closeCart();
        closeModals();
    });

    // Checkout Modal
    els.checkoutBtn.addEventListener('click', () => {
        if (!state.cart.isEmpty) {
            closeCart();
            openCheckoutModal();
        }
    });
    
    els.modalClose.addEventListener('click', closeModals);
    
    // Form Submission
    els.checkoutForm.addEventListener('submit', handleCheckout);
    
    // Success Modal
    els.successCloseBtn.addEventListener('click', () => {
        closeModals();
        fetchCart(); // Refresh cart (should be empty now)
    });

    // Order History
    els.navOrderHistory.addEventListener('click', (e) => {
        e.preventDefault();
        openOrderHistoryModal();
    });
    els.orderModalClose.addEventListener('click', closeModals);
    els.btnSearchOrders.addEventListener('click', fetchOrderHistory);

    // Single Product
    els.productModalClose.addEventListener('click', closeModals);
}

// ==========================================
// API Interactions
// ==========================================

async function loadProducts() {
    showLoading(true);
    
    try {
        const prodRes = await fetch(`${API_BASE}/products?size=50`);
        const invRes = await fetch(`${API_BASE}/inventory`);
        if (prodRes.ok && invRes.ok) {
            state.products = await prodRes.json();
            const invData = await invRes.json();
            const inventory = invData.inventorySummary || {};
            state.products.forEach(p => {
                p.stockQuantity = inventory[p.id] || 0;
            });
            renderProducts();
        } else {
            showToast('Failed to load products', 'error');
        }
    } catch (error) {
        console.error('Error fetching products:', error);
        showToast('Connection error', 'error');
    } finally {
        showLoading(false);
    }
}

async function fetchCart() {
    try {
        const response = await fetch(`${API_BASE}/cart`);
        if (response.ok) {
            state.cart = await response.json();
            updateCartUI();
        }
    } catch (error) {
        console.error('Error fetching cart:', error);
    }
}

async function addToCart(productId) {
    try {
        const response = await fetch(`${API_BASE}/cart/items`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ productId: productId, quantity: 1 })
        });
        
        if (response.ok) {
            state.cart = await response.json();
            updateCartUI();
            showToast('Item added to cart!');
            popCartBadge();
        } else {
            showToast('Failed to add item', 'error');
        }
    } catch (error) {
        showToast('Connection error', 'error');
    }
}

async function updateCartItemQty(productId, newQty) {
    if (newQty <= 0) {
        return removeCartItem(productId);
    }
    
    try {
        const response = await fetch(`${API_BASE}/cart/items/${productId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ quantity: newQty })
        });
        
        if (response.ok) {
            state.cart = await response.json();
            updateCartUI();
        }
    } catch (error) {
        console.error('Error updating cart:', error);
    }
}

async function removeCartItem(productId) {
    try {
        const response = await fetch(`${API_BASE}/cart/items/${productId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            state.cart = await response.json();
            updateCartUI();
        }
    } catch (error) {
        console.error('Error removing item:', error);
    }
}

async function handleCheckout(e) {
    e.preventDefault();
    
    const customerName = document.getElementById('customerName').value;
    const customerEmail = document.getElementById('customerEmail').value;
    const shippingAddress = document.getElementById('shippingAddress').value;
    
    els.submitOrderBtn.disabled = true;
    els.submitOrderBtn.innerHTML = '<div class="spinner" style="width: 20px; height: 20px; border-width: 2px;"></div> Processing...';
    
    try {
        const response = await fetch(`${API_BASE}/cart/checkout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                customerName,
                customerEmail,
                shippingAddress
            })
        });
        
        if (response.ok || response.status === 202) {
            els.checkoutModal.classList.remove('open');
            els.successModal.classList.add('open');
            els.checkoutForm.reset();
        } else {
            const err = await response.json();
            showToast(err.error || 'Checkout failed', 'error');
        }
    } catch (error) {
        showToast('Connection error during checkout', 'error');
    } finally {
        els.submitOrderBtn.disabled = false;
        els.submitOrderBtn.innerHTML = 'Place Order';
    }
}

async function fetchOrderHistory() {
    const email = els.orderSearchEmail.value.trim();
    if (!email) {
        showToast('Please enter an email address', 'error');
        return;
    }

    els.orderHistoryLoading.classList.remove('hidden');
    els.orderHistoryResults.innerHTML = '';

    try {
        const response = await fetch(`${API_BASE}/orders/customer/${encodeURIComponent(email)}`);
        if (response.ok) {
            const orders = await response.json();
            renderOrderHistory(orders);
        } else {
            showToast('Failed to fetch orders', 'error');
        }
    } catch (error) {
        showToast('Connection error', 'error');
    } finally {
        els.orderHistoryLoading.classList.add('hidden');
    }
}

// ==========================================
// Rendering & UI Updates
// ==========================================

function renderProducts() {
    els.productsGrid.innerHTML = '';
    
    let filtered = state.products;
    if (state.currentCategory !== 'all') {
        filtered = state.products.filter(p => p.category === state.currentCategory);
    }
    
    if (filtered.length === 0) {
        els.productsGrid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted);">No products found in this category.</div>';
    } else {
        filtered.forEach(product => {
            const card = document.createElement('div');
            card.className = 'product-card';
            
            const isOutOfStock = product.stockQuantity <= 0;
            const imgHtml = product.imageUrl 
                ? `<div style="height: 200px; width: 100%; overflow: hidden; border-radius: 8px 8px 0 0; margin: -1.5rem -1.5rem 1rem -1.5rem;"><img src="${product.imageUrl}" style="width: 100%; height: 100%; object-fit: contain; object-position: center;" alt="${product.name}"></div>`
                : '';
            
            card.innerHTML = `
                ${isOutOfStock ? '<div class="out-of-stock-badge">Out of Stock</div>' : ''}
                ${imgHtml}
                <div class="product-category" onclick="openProductModal(${product.id})" style="cursor: pointer;">${product.category}</div>
                <h3 class="product-title" onclick="openProductModal(${product.id})" style="cursor: pointer;">${product.name}</h3>
                <p class="product-desc" onclick="openProductModal(${product.id})" style="cursor: pointer;">${product.description}</p>
                <div class="product-footer">
                    <span class="product-price">$${product.price.toFixed(2)}</span>
                    <button class="add-to-cart-btn ${isOutOfStock ? 'out-of-stock' : ''}" 
                            onclick="addToCart(${product.id}); event.stopPropagation();"
                            ${isOutOfStock ? 'disabled' : ''}>
                        <i data-lucide="plus"></i>
                    </button>
                </div>
            `;
            els.productsGrid.appendChild(card);
        });
        lucide.createIcons();
    }
    
    els.productsGrid.classList.remove('hidden');
}

function updateCartUI() {
    // Update Badge
    els.cartBadge.textContent = state.cart.itemCount;
    if (state.cart.itemCount > 0) {
        els.cartBadge.classList.remove('hidden');
    } else {
        els.cartBadge.classList.add('hidden');
    }
    
    // Update Sidebar
    els.cartTotal.textContent = `$${state.cart.cartTotal.toFixed(2)}`;
    
    if (state.cart.isEmpty) {
        els.cartItems.innerHTML = '<div class="empty-cart-message">Your cart is empty.</div>';
        els.checkoutBtn.disabled = true;
    } else {
        els.checkoutBtn.disabled = false;
        els.cartItems.innerHTML = '';
        
        state.cart.items.forEach(item => {
            const el = document.createElement('div');
            el.className = 'cart-item';
            el.innerHTML = `
                <div class="cart-item-details">
                    <h4>${item.product.name}</h4>
                    <div class="cart-item-price">$${item.product.price.toFixed(2)}</div>
                    <div class="qty-controls">
                        <button class="qty-btn" onclick="updateCartItemQty(${item.product.id}, ${item.quantity - 1})">
                            <i data-lucide="minus" style="width: 14px; height: 14px;"></i>
                        </button>
                        <span class="qty-value">${item.quantity}</span>
                        <button class="qty-btn" onclick="updateCartItemQty(${item.product.id}, ${item.quantity + 1})">
                            <i data-lucide="plus" style="width: 14px; height: 14px;"></i>
                        </button>
                    </div>
                </div>
                <div class="cart-item-actions">
                    <button class="btn-icon cart-item-remove" onclick="removeCartItem(${item.product.id})">
                        <i data-lucide="trash-2" style="width: 18px; height: 18px;"></i>
                    </button>
                    <div style="text-align: right; margin-top: 0.5rem; font-weight: 600;">
                        $${item.totalPrice.toFixed(2)}
                    </div>
                </div>
            `;
            els.cartItems.appendChild(el);
        });
        lucide.createIcons();
    }
}

function renderOrderHistory(orders) {
    els.orderHistoryResults.innerHTML = '';
    
    if (!orders || orders.length === 0) {
        els.orderHistoryResults.innerHTML = '<div style="text-align:center; color: var(--text-muted); padding: 2rem;">No orders found for this email.</div>';
        return;
    }

    orders.forEach(order => {
        let itemsHtml = order.items.map(item => `
            <div style="display: flex; justify-content: space-between; font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 0.25rem;">
                <span>${item.product.name} (x${item.quantity})</span>
                <span>$${item.subtotal.toFixed(2)}</span>
            </div>
        `).join('');

        const statusColors = {
            'PENDING': 'var(--text-muted)',
            'PROCESSING': 'var(--accent-secondary)',
            'SHIPPED': 'var(--accent-primary)',
            'DELIVERED': 'var(--accent-success)',
            'CANCELLED': 'var(--accent-danger)'
        };

        const el = document.createElement('div');
        el.style.cssText = `background: var(--bg-main); padding: 1rem; border-radius: 8px; border: 1px solid var(--border-light);`;
        el.innerHTML = `
            <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem;">
                <strong>Order #${order.id}</strong>
                <span style="color: ${statusColors[order.status] || 'white'}; font-weight: bold; font-size: 0.8rem; background: var(--bg-surface-elevated); padding: 2px 8px; border-radius: 12px;">${order.status}</span>
            </div>
            <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 1rem;">
                ${new Date(order.createdAt).toLocaleString()}
            </div>
            <div style="border-bottom: 1px dashed var(--border-light); padding-bottom: 0.5rem; margin-bottom: 0.5rem;">
                ${itemsHtml}
            </div>
            <div style="display: flex; justify-content: space-between; font-weight: bold;">
                <span>Total</span>
                <span>$${order.totalAmount.toFixed(2)}</span>
            </div>
        `;
        els.orderHistoryResults.appendChild(el);
    });
}

// ==========================================
// UI Helpers
// ==========================================

function openCart() {
    els.cartSidebar.classList.add('open');
    els.cartOverlay.classList.add('open');
}

function closeCart() {
    els.cartSidebar.classList.remove('open');
    els.cartOverlay.classList.remove('open');
}

function openCheckoutModal() {
    els.cartOverlay.classList.add('open');
    els.checkoutModal.classList.add('open');
    els.checkoutItemCount.textContent = state.cart.itemCount;
    els.checkoutTotal.textContent = `$${state.cart.cartTotal.toFixed(2)}`;
}

function closeModals() {
    els.cartOverlay.classList.remove('open');
    els.checkoutModal.classList.remove('open');
    els.successModal.classList.remove('open');
    els.orderHistoryModal.classList.remove('open');
    els.productModal.classList.remove('open');
}

function openOrderHistoryModal() {
    els.cartOverlay.classList.add('open');
    els.orderHistoryModal.classList.add('open');
}

function openProductModal(productId) {
    const product = state.products.find(p => p.id === productId);
    if (!product) return;
    
    els.spCategory.textContent = product.category;
    els.spTitle.textContent = product.name;
    els.spSku.textContent = `SKU: ${product.sku}`;
    els.spDesc.textContent = product.description;
    els.spPrice.textContent = `$${product.price.toFixed(2)}`;
    
    // Update Modal Image
    const modalImgContainer = document.getElementById('sp-image-container');
    if (product.imageUrl) {
        modalImgContainer.innerHTML = `<img src="${product.imageUrl}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 12px;" alt="${product.name}">`;
        modalImgContainer.style.padding = '0';
    } else {
        modalImgContainer.innerHTML = `<i data-lucide="box" style="width: 150px; height: 150px; color: var(--accent-primary); opacity: 0.5;"></i>`;
        modalImgContainer.style.padding = '2rem';
    }
    lucide.createIcons();
    
    const isOutOfStock = product.stockQuantity <= 0;
    els.spStock.textContent = isOutOfStock ? 'Out of Stock' : `In Stock: ${product.stockQuantity}`;
    els.spStock.style.color = isOutOfStock ? 'var(--accent-danger)' : 'var(--accent-success)';
    
    els.spAddToCart.disabled = isOutOfStock;
    els.spAddToCart.onclick = () => {
        addToCart(product.id);
        closeModals();
        openCart();
    };
    
    els.cartOverlay.classList.add('open');
    els.productModal.classList.add('open');
}

function showLoading(show) {
    if (show) {
        els.loadingSpinner.classList.remove('hidden');
        els.productsGrid.classList.add('hidden');
    } else {
        els.loadingSpinner.classList.add('hidden');
    }
}

function popCartBadge() {
    els.cartBadge.classList.add('pop');
    setTimeout(() => {
        els.cartBadge.classList.remove('pop');
    }, 300);
}

let toastTimeout;
function showToast(message, type = 'success') {
    els.toastMessage.textContent = message;
    
    if (type === 'error') {
        els.toast.style.borderLeft = '4px solid var(--accent-danger)';
    } else {
        els.toast.style.borderLeft = '4px solid var(--accent-success)';
    }
    
    els.toast.classList.add('show');
    
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => {
        els.toast.classList.remove('show');
    }, 3000);
}
