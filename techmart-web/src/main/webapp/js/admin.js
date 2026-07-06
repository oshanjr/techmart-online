const API_BASE = '/techmart/api';
let adminToken = sessionStorage.getItem('adminToken');

document.addEventListener('DOMContentLoaded', () => {
    if (adminToken) {
        showView('admin-dashboard-view');
        loadProducts();
    } else {
        showView('admin-login-view');
    }
    setupEventListeners();
});

function showView(viewId) {
    document.querySelectorAll('.admin-view').forEach(v => v.classList.remove('active'));
    document.getElementById(viewId).classList.add('active');
}

function setupEventListeners() {
    // Login Form
    document.getElementById('admin-login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const user = document.getElementById('admin-username').value;
        const pass = document.getElementById('admin-password').value;
        
        try {
            const res = await fetch(`${API_BASE}/admin/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: user, password: pass })
            });
            
            if (res.ok) {
                const data = await res.json();
                adminToken = data.token;
                sessionStorage.setItem('adminToken', adminToken);
                showToast('Login successful');
                showView('admin-dashboard-view');
                loadProducts();
            } else {
                showToast('Invalid credentials', 'error');
            }
        } catch (error) {
            showToast('Connection error', 'error');
        }
    });
    
    // Logout
    document.getElementById('btn-logout').addEventListener('click', () => {
        sessionStorage.removeItem('adminToken');
        adminToken = null;
        showView('admin-login-view');
    });

    // Tabs
    document.querySelectorAll('.tab-link').forEach(link => {
        link.addEventListener('click', (e) => {
            if (e.target.getAttribute('href') !== '#') return;
            e.preventDefault();
            document.querySelectorAll('.tab-link').forEach(l => l.classList.remove('active'));
            document.querySelectorAll('.dashboard-tab').forEach(t => t.classList.remove('active'));
            
            e.target.classList.add('active');
            const tabId = e.target.dataset.tab;
            document.getElementById(tabId).classList.add('active');
            
            if (tabId === 'tab-products') loadProducts();
            if (tabId === 'tab-orders') loadOrders();
        });
    });

    // Modal
    document.getElementById('btn-add-product').addEventListener('click', () => openCMSModal());
    document.getElementById('cms-modal-close').addEventListener('click', closeCMSModal);
    document.getElementById('cms-overlay').addEventListener('click', closeCMSModal);
    
    // CMS Form
    document.getElementById('cms-product-form').addEventListener('submit', saveProduct);
    
    // Order Filter
    document.getElementById('order-status-filter').addEventListener('change', loadOrders);
}

// =======================
// Products (CMS)
// =======================
let products = [];

async function loadProducts() {
    try {
        const prodRes = await fetch(`${API_BASE}/products?size=500`);
        const invRes = await fetch(`${API_BASE}/inventory`);
        if (prodRes.ok && invRes.ok) {
            products = await prodRes.json();
            const invData = await invRes.json();
            const inventory = invData.inventorySummary || {};
            products.forEach(p => {
                p.stockQuantity = inventory[p.id] || 0;
            });
            renderProductsTable();
        }
    } catch (e) {
        showToast('Error loading products', 'error');
    }
}

function renderProductsTable() {
    const tbody = document.getElementById('admin-products-list');
    tbody.innerHTML = '';
    
    products.forEach(p => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${p.id}</td>
            <td>${p.sku}</td>
            <td>${p.name}</td>
            <td>${p.category}</td>
            <td>$${p.price.toFixed(2)}</td>
            <td>${p.stockQuantity}</td>
            <td class="action-btns">
                <button class="btn-icon" onclick="openCMSModal(${p.id})"><i data-lucide="edit" style="width:16px;height:16px;"></i></button>
                <button class="btn-icon" onclick="deleteProduct(${p.id})" style="color:var(--accent-danger)"><i data-lucide="trash" style="width:16px;height:16px;"></i></button>
            </td>
        `;
        tbody.appendChild(tr);
    });
    lucide.createIcons();
}

function openCMSModal(id = null) {
    const form = document.getElementById('cms-product-form');
    form.reset();
    
    if (id) {
        document.getElementById('cms-modal-title').textContent = 'Edit Product';
        const p = products.find(x => x.id === id);
        document.getElementById('cms-id').value = p.id;
        document.getElementById('cms-sku').value = p.sku;
        document.getElementById('cms-name').value = p.name;
        document.getElementById('cms-category').value = p.category;
        document.getElementById('cms-desc').value = p.description || '';
        document.getElementById('cms-image').value = p.imageUrl || '';
        document.getElementById('cms-price').value = p.price;
        document.getElementById('cms-stock').value = p.stockQuantity;
        document.getElementById('cms-active').checked = p.active;
    } else {
        document.getElementById('cms-modal-title').textContent = 'Add Product';
        document.getElementById('cms-id').value = '';
    }
    
    document.getElementById('cms-overlay').classList.add('open');
    document.getElementById('cms-product-modal').classList.add('open');
}

function closeCMSModal() {
    document.getElementById('cms-overlay').classList.remove('open');
    document.getElementById('cms-product-modal').classList.remove('open');
}

async function saveProduct(e) {
    e.preventDefault();
    const id = document.getElementById('cms-id').value;
    
    const payload = {
        sku: document.getElementById('cms-sku').value,
        name: document.getElementById('cms-name').value,
        category: document.getElementById('cms-category').value,
        description: document.getElementById('cms-desc').value,
        imageUrl: document.getElementById('cms-image').value,
        price: parseFloat(document.getElementById('cms-price').value),
        stockQuantity: parseInt(document.getElementById('cms-stock').value),
        active: document.getElementById('cms-active').checked
    };
    
    if (id) {
        const existingProduct = products.find(x => x.id == id);
        if (existingProduct) {
            payload.version = existingProduct.version;
        }
    }
    
    try {
        const method = id ? 'PUT' : 'POST';
        const url = id ? `${API_BASE}/products/${id}` : `${API_BASE}/products`;
        
        const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        
        if (res.ok) {
            const savedProduct = id ? { ...payload, id: parseInt(id) } : await res.json();
            
            // Sync Inventory
            const currentStock = id ? (products.find(x => x.id == id)?.stockQuantity || 0) : 0;
            const newStock = payload.stockQuantity;
            const delta = newStock - currentStock;
            
            if (delta !== 0) {
                 await fetch(`${API_BASE}/inventory/update`, {
                     method: 'POST',
                     headers: { 'Content-Type': 'application/json' },
                     body: JSON.stringify({ productId: savedProduct.id, warehouseId: 1, quantityDelta: delta, eventType: 'STOCK_ADJUSTED' })
                 });
                 // wait a bit for JMS to process
                 await new Promise(r => setTimeout(r, 500));
                 // force refresh cache
                 await fetch(`${API_BASE}/inventory/refresh`, { method: 'POST' });
            }

            showToast('Product saved!');
            closeCMSModal();
            loadProducts();
        } else {
            const err = await res.json();
            showToast(err.error || 'Failed to save', 'error');
        }
    } catch (error) {
        showToast('Connection error', 'error');
    }
}

async function deleteProduct(id) {
    if (!confirm('Are you sure you want to deactivate this product?')) return;
    
    try {
        const res = await fetch(`${API_BASE}/products/${id}`, { method: 'DELETE' });
        if (res.ok || res.status === 204) {
            showToast('Product deactivated');
            loadProducts();
        } else {
            showToast('Failed to delete', 'error');
        }
    } catch (e) {
        showToast('Connection error', 'error');
    }
}

// =======================
// Orders
// =======================
async function loadOrders() {
    const status = document.getElementById('order-status-filter').value;
    let url = `${API_BASE}/orders`;
    if (status) url += `?status=${status}`;
    
    try {
        const res = await fetch(url);
        if (res.ok) {
            const orders = await res.json();
            renderOrdersTable(orders);
        }
    } catch (e) {
        showToast('Error loading orders', 'error');
    }
}

function renderOrdersTable(orders) {
    const tbody = document.getElementById('admin-orders-list');
    tbody.innerHTML = '';
    
    orders.forEach(o => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>#${o.id}</td>
            <td>${o.customerEmail}<br><small style="color:var(--text-muted)">${o.customerName || ''}</small></td>
            <td>$${o.totalAmount.toFixed(2)}</td>
            <td>${new Date(o.createdAt).toLocaleString()}</td>
            <td><strong>${o.status}</strong></td>
            <td>
                <select onchange="updateOrderStatus(${o.id}, this.value)" style="padding:4px; background:var(--bg-main); color:white; border:1px solid var(--border-light)">
                    <option value="" disabled selected>Change Status...</option>
                    <option value="PROCESSING">Processing</option>
                    <option value="SHIPPED">Shipped</option>
                    <option value="DELIVERED">Delivered</option>
                    <option value="CANCELLED">Cancelled</option>
                </select>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function updateOrderStatus(id, newStatus) {
    if (!newStatus) return;
    try {
        const res = await fetch(`${API_BASE}/orders/${id}/status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: newStatus })
        });
        
        if (res.ok || res.status === 202) {
            showToast('Order status updated!');
            loadOrders(); // reload
        } else {
            showToast('Update failed', 'error');
        }
    } catch (e) {
        showToast('Connection error', 'error');
    }
}

// Toast logic
let toastTimeout;
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    document.getElementById('toast-message').textContent = message;
    toast.style.borderLeft = type === 'error' ? '4px solid var(--accent-danger)' : '4px solid var(--accent-success)';
    toast.classList.add('show');
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => toast.classList.remove('show'), 3000);
}
