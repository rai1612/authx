// AuthX Frontend Utilities

/**
 * HTTP Client for API requests
 */
class APIClient {
    constructor(baseURL = CONFIG.API_BASE_URL) {
        this.baseURL = baseURL;
    }

    /**
     * Get authorization headers
     */
    getAuthHeaders(useMfaToken = false) {
        const headers = {
            'Content-Type': 'application/json'
        };

        const tokenKey = useMfaToken ? CONFIG.STORAGE_KEYS.MFA_TOKEN : CONFIG.STORAGE_KEYS.ACCESS_TOKEN;
        const token = localStorage.getItem(tokenKey);
        
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        return headers;
    }

    /**
     * Make API request
     */
    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;
        const config = {
            headers: this.getAuthHeaders(options.useMfaToken),
            ...options
        };

        try {
            showLoading();
            const response = await fetch(url, config);
            const data = await response.json();

            if (!response.ok) {
                // Handle rate limiting specifically
                if (response.status === 429) {
                    const retryAfter = data.retryAfter || 300; // default 5 minutes
                    const error = new Error(data.message || 'Rate limit exceeded');
                    error.isRateLimit = true;
                    error.retryAfter = retryAfter;
                    throw error;
                }
                
                throw new Error(data.error || `HTTP ${response.status}`);
            }

            return data;
        } catch (error) {
            console.error('API Request failed:', error);
            
            // Add status code to error object for better handling
            if (error.message.includes('HTTP 401')) {
                error.status = 401;
            }
            
            // Only auto-handle unauthorized for authentication endpoints
            // Let other endpoints handle 401 errors themselves
            if ((error.message.includes('401') || error.message.includes('Unauthorized')) && 
                (endpoint.includes('/auth/') || endpoint.includes('/mfa/'))) {
                this.handleUnauthorized();
            }
            throw error;
        } finally {
            hideLoading();
        }
    }

    /**
     * Handle unauthorized responses
     */
    handleUnauthorized() {
        localStorage.clear();
        showToast(CONFIG.ERRORS.UNAUTHORIZED, 'error');
        showLogin();
    }

    // HTTP methods
    async get(endpoint, options = {}) {
        return this.request(endpoint, { method: 'GET', ...options });
    }

    async post(endpoint, data, options = {}) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data),
            ...options
        });
    }

    async put(endpoint, data, options = {}) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data),
            ...options
        });
    }

    async delete(endpoint, options = {}) {
        return this.request(endpoint, { method: 'DELETE', ...options });
    }
}

// Global API client instance
const api = new APIClient();

/**
 * Storage utilities
 */
const Storage = {
    set(key, value) {
        if (!FEATURES.STORAGE_AVAILABLE) return false;
        try {
            localStorage.setItem(key, JSON.stringify(value));
            return true;
        } catch (error) {
            console.error('Storage set error:', error);
            return false;
        }
    },

    get(key) {
        if (!FEATURES.STORAGE_AVAILABLE) return null;
        try {
            const item = localStorage.getItem(key);
            return item ? JSON.parse(item) : null;
        } catch (error) {
            console.error('Storage get error:', error);
            return null;
        }
    },

    remove(key) {
        if (!FEATURES.STORAGE_AVAILABLE) return false;
        try {
            localStorage.removeItem(key);
            return true;
        } catch (error) {
            console.error('Storage remove error:', error);
            return false;
        }
    },

    clear() {
        if (!FEATURES.STORAGE_AVAILABLE) return false;
        try {
            localStorage.clear();
            return true;
        } catch (error) {
            console.error('Storage clear error:', error);
            return false;
        }
    }
};

/**
 * UI Utilities
 */

// Show loading overlay
function showLoading() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = 'block';
    }
}

// Hide loading overlay
function hideLoading() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.style.display = 'none';
    }
}

// Show toast notification
function showToast(message, type = 'info', duration = CONFIG.UI.TOAST_DURATION) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <div style="display: flex; align-items: center; gap: 0.5rem;">
            <i class="fas fa-${getToastIcon(type)}"></i>
            <span>${message}</span>
        </div>
    `;

    container.appendChild(toast);

    // Auto remove toast
    setTimeout(() => {
        if (toast.parentNode) {
            toast.style.animation = 'slideOutToTop 0.3s ease forwards';
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 300);
        }
    }, duration);

    // Click to dismiss
    toast.addEventListener('click', () => {
        if (toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    });
}

// Get icon for toast type
function getToastIcon(type) {
    const icons = {
        success: 'check-circle',
        error: 'exclamation-circle',
        warning: 'exclamation-triangle',
        info: 'info-circle'
    };
    return icons[type] || icons.info;
}

// Show modal
function showModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'block';
    }
}

// Close modal
function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
}

// Show section
function showSection(sectionId) {
    console.log('showSection called with:', sectionId);
    
    // Hide all sections
    document.querySelectorAll('.section').forEach(section => {
        section.classList.remove('active');
    });

    // Show target section
    const targetSection = document.getElementById(sectionId);
    if (targetSection) {
        targetSection.classList.add('active');
        console.log('Section shown:', sectionId);
    } else {
        console.error('Section not found:', sectionId);
    }

    // Update navigation
    updateNavigation();
}

// Show home/welcome section
function showHome() {
    showSection('welcome-section');
}

// Theme management
function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    // Update theme icon
    const themeIcon = document.getElementById('theme-icon');
    if (themeIcon) {
        themeIcon.className = newTheme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
    }
    
    console.log('Theme switched to:', newTheme);
}

// Initialize theme on page load
function initializeTheme() {
    const savedTheme = localStorage.getItem('theme') || 
                     (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    
    document.documentElement.setAttribute('data-theme', savedTheme);
    
    // Update theme icon
    const themeIcon = document.getElementById('theme-icon');
    if (themeIcon) {
        themeIcon.className = savedTheme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
    }
    
    console.log('Theme initialized:', savedTheme);
}

// Update navigation based on auth state
function updateNavigation() {
    const isAuthenticated = !!localStorage.getItem(CONFIG.STORAGE_KEYS.ACCESS_TOKEN);
    
    // Show/hide nav items based on auth state
    document.getElementById('dashboard-link').style.display = isAuthenticated ? 'block' : 'none';
    document.getElementById('logout-link').style.display = isAuthenticated ? 'block' : 'none';
    
    // Check admin role for admin link
    const isAdmin = isAuthenticated && checkAdminRole();
    const adminLink = document.getElementById('admin-link');
    if (adminLink) {
        adminLink.style.display = isAdmin ? 'block' : 'none';
    }
    
    // Hide login/register links when authenticated
    document.querySelectorAll('.nav-link').forEach(link => {
        if (link.textContent === 'Login' || link.textContent === 'Register') {
            link.style.display = isAuthenticated ? 'none' : 'block';
        }
    });
}

// Check if user has admin role
function checkAdminRole() {
    try {
        const userInfo = localStorage.getItem(CONFIG.STORAGE_KEYS.USER_INFO);
        if (userInfo) {
            const user = JSON.parse(userInfo);
            if (user.roles && Array.isArray(user.roles)) {
                return user.roles.some(role => role.name === 'ADMIN');
            }
        }
    } catch (error) {
        console.error('Error checking admin role:', error);
    }
    return false;
}

/**
 * Form utilities
 */

// Get form data as object
function getFormData(formId) {
    const form = document.getElementById(formId);
    if (!form) return {};

    const formData = new FormData(form);
    const data = {};
    
    for (const [key, value] of formData.entries()) {
        data[key] = value;
    }

    return data;
}

// Clear form
function clearForm(formId) {
    const form = document.getElementById(formId);
    if (form) {
        form.reset();
    }
}

// Set form errors
function setFormErrors(errors) {
    // Clear existing errors
    document.querySelectorAll('.form-error').forEach(error => {
        error.remove();
    });

    // Add new errors
    Object.entries(errors).forEach(([field, message]) => {
        const input = document.getElementById(field);
        if (input) {
            const error = document.createElement('div');
            error.className = 'form-error';
            error.style.color = 'var(--error-color)';
            error.style.fontSize = '0.875rem';
            error.style.marginTop = '0.25rem';
            error.textContent = message;
            input.parentNode.appendChild(error);
        }
    });
}

/**
 * Validation utilities
 */

// Email validation
function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

// Password validation
function isValidPassword(password) {
    // At least 8 characters, 1 uppercase, 1 lowercase, 1 number, 1 special char
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    return passwordRegex.test(password);
}

// Phone validation
function isValidPhone(phone) {
    const phoneRegex = /^\+?[\d\s\-\(\)]{10,}$/;
    return phoneRegex.test(phone);
}

/**
 * WebAuthn utilities
 */

// Convert ArrayBuffer to Base64URL
function arrayBufferToBase64Url(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    bytes.forEach(byte => binary += String.fromCharCode(byte));
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

// Convert Base64URL to ArrayBuffer
function base64UrlToArrayBuffer(base64url) {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
    const binary = atob(padded);
    const buffer = new ArrayBuffer(binary.length);
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return buffer;
}

/**
 * Date formatting utilities
 */

// Format date for display
function formatDate(dateString) {
    if (!dateString) return 'Never';
    
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins} minutes ago`;
    if (diffHours < 24) return `${diffHours} hours ago`;
    if (diffDays < 7) return `${diffDays} days ago`;
    
    return date.toLocaleDateString();
}

// Format relative time
function formatRelativeTime(dateString) {
    if (!dateString) return 'Never';
    
    const date = new Date(dateString);
    const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
    const diffMs = date - new Date();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (Math.abs(diffMins) < 60) return rtf.format(diffMins, 'minute');
    if (Math.abs(diffHours) < 24) return rtf.format(diffHours, 'hour');
    return rtf.format(diffDays, 'day');
}

/**
 * Debounce utility
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Initialize utilities
 */
function initializeUtils() {
    // Add CSS for slideOut animation
    if (!document.querySelector('#slideout-style')) {
        const style = document.createElement('style');
        style.id = 'slideout-style';
        style.textContent = `
            @keyframes slideOut {
                to {
                    opacity: 0;
                    transform: translateX(100%);
                }
            }
        `;
        document.head.appendChild(style);
    }

    // Close modals when clicking outside
    document.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.style.display = 'none';
        }
    });

    // Handle escape key for modals
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelectorAll('.modal').forEach(modal => {
                if (modal.style.display === 'block') {
                    modal.style.display = 'none';
                }
            });
        }
    });
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeUtils);
} else {
    initializeUtils();
}