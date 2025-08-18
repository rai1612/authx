// AuthX Main Application

/**
 * Application state and initialization
 */
const App = {
    initialized: false,
    
    /**
     * Initialize the application
     */
    async init() {
        if (this.initialized) return;
        
        try {
            console.log('🚀 Initializing AuthX Frontend...');
            
            // Check browser compatibility
            this.checkBrowserCompatibility();
            
            // Initialize components
            this.initializeEventListeners();
            this.initializeKeyboardShortcuts();
            this.initializeTheme();
            
            // Check authentication state
            await this.checkAuthState();
            
            // Show appropriate section
            this.showInitialSection();
            
            this.initialized = true;
            console.log('✅ AuthX Frontend initialized successfully');
            
        } catch (error) {
            console.error('❌ Failed to initialize AuthX Frontend:', error);
            showToast('Application initialization failed', 'error');
        }
    },

    /**
     * Check browser compatibility
     */
    checkBrowserCompatibility() {
        const warnings = [];
        
        // Check for required features
        if (!FEATURES.STORAGE_AVAILABLE) {
            warnings.push('Local storage is not available - some features may not work');
        }
        
        if (!FEATURES.WEBAUTHN_SUPPORTED) {
            console.warn('WebAuthn not supported in this browser');
        }
        
        if (FEATURES.HTTPS_REQUIRED) {
            warnings.push('HTTPS is required for full functionality');
        }
        
        // Show warnings if any
        warnings.forEach(warning => {
            showToast(warning, 'warning');
        });
        
        // Log browser info
        console.log('Browser compatibility:', {
            webAuthn: FEATURES.WEBAUTHN_SUPPORTED,
            storage: FEATURES.STORAGE_AVAILABLE,
            https: !FEATURES.HTTPS_REQUIRED,
            mobile: ENV.IS_MOBILE
        });
    },

    /**
     * Initialize global event listeners
     */
    initializeEventListeners() {
        // Handle navigation clicks
        document.addEventListener('click', (e) => {
            const target = e.target.closest('[data-navigate]');
            if (target) {
                e.preventDefault();
                const section = target.getAttribute('data-navigate');
                this.navigate(section);
            }
        });

        // Handle form submissions
        document.addEventListener('submit', (e) => {
            // Prevent default form submission for all forms
            // Individual forms handle their own submission logic
            e.preventDefault();
        });

        // Handle online/offline status
        window.addEventListener('online', () => {
            showToast('Connection restored', 'success');
        });

        window.addEventListener('offline', () => {
            showToast('Connection lost', 'warning');
        });

        // Handle visibility change (for auto-refresh)
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && Auth.isAuthenticated()) {
                // Refresh data when returning to the app
                this.refreshAppData();
            }
        });

        // Handle window beforeunload
        window.addEventListener('beforeunload', (e) => {
            // Save any pending data or show warning if needed
            if (this.hasUnsavedChanges()) {
                e.preventDefault();
                e.returnValue = 'You have unsaved changes. Are you sure you want to leave?';
            }
        });
    },

    /**
     * Initialize keyboard shortcuts
     */
    initializeKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Ctrl/Cmd + K for quick navigation
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                this.showQuickNav();
            }
            
            // Alt + D for dashboard
            if (e.altKey && e.key === 'd' && Auth.isAuthenticated()) {
                e.preventDefault();
                showDashboard();
            }
            
            // Alt + A for admin (if user has admin role)
            if (e.altKey && e.key === 'a' && Auth.isAuthenticated() && checkAdminRole()) {
                e.preventDefault();
                showAdmin();
            }
            
            // Alt + L for login
            if (e.altKey && e.key === 'l' && !Auth.isAuthenticated()) {
                e.preventDefault();
                showLogin();
            }
            
            // Escape to close modals
            if (e.key === 'Escape') {
                this.closeAllModals();
            }
        });
    },

    /**
     * Initialize theme
     */
    initializeTheme() {
        // Call the global theme initialization function
        initializeTheme();
        
        // Listen for system theme changes
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
                if (!localStorage.getItem('theme')) {
                    // Only auto-switch if user hasn't set a preference
                    const newTheme = e.matches ? 'dark' : 'light';
                    document.documentElement.setAttribute('data-theme', newTheme);
                    
                    // Update theme icon
                    const themeIcon = document.getElementById('theme-icon');
                    if (themeIcon) {
                        themeIcon.className = newTheme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
                    }
                }
            });
        }
    },

    /**
     * Check authentication state
     */
    async checkAuthState() {
        try {
            if (Auth.isAuthenticated()) {
                // Try to load user profile to verify token validity
                await loadUserProfile();
                return true;
            } else if (Auth.isMfaInProgress()) {
                // MFA is in progress
                return 'mfa';
            }
            return false;
        } catch (error) {
            console.error('Auth state check failed:', error);
            // Clear invalid auth data
            Auth.clearAuth();
            return false;
        }
    },

    /**
     * Show initial section based on auth state
     */
    async showInitialSection() {
        const authState = await this.checkAuthState();
        
        if (authState === true) {
            showDashboard();
        } else if (authState === 'mfa') {
            showMfaChallenge();
        } else {
            showWelcome();
        }
    },

    /**
     * Navigate to section
     */
    navigate(sectionId) {
        // Map of navigation targets to functions
        const navigationMap = {
            'welcome': showWelcome,
            'login': showLogin,
            'register': showRegister,
            'dashboard': showDashboard,
            'admin': showAdmin,
            'mfa': showMfaChallenge
        };

        const navFunction = navigationMap[sectionId];
        if (navFunction) {
            navFunction();
        } else {
            console.warn('Unknown navigation target:', sectionId);
        }
    },

    /**
     * Show quick navigation (could be extended with a search/command palette)
     */
    showQuickNav() {
        // For now, just show a toast with available shortcuts
        showToast('Shortcuts: Alt+D (Dashboard), Alt+L (Login), Ctrl+K (This help)', 'info');
    },

    /**
     * Close all modals
     */
    closeAllModals() {
        document.querySelectorAll('.modal').forEach(modal => {
            modal.style.display = 'none';
        });
    },

    /**
     * Check if there are unsaved changes
     */
    hasUnsavedChanges() {
        // Check for any forms with unsaved data
        const forms = document.querySelectorAll('form');
        for (const form of forms) {
            if (form.checkValidity && form.checkValidity() === false) {
                // Form has validation errors, might indicate unsaved changes
                continue;
            }
            
            const formData = new FormData(form);
            let hasData = false;
            for (const [key, value] of formData.entries()) {
                if (value && value.trim() !== '') {
                    hasData = true;
                    break;
                }
            }
            if (hasData) return true;
        }
        return false;
    },

    /**
     * Refresh app data
     */
    async refreshAppData() {
        if (!Auth.isAuthenticated()) return;
        
        try {
            // Refresh user profile
            await loadUserProfile();
            
            // If dashboard is visible, refresh dashboard data
            const dashboardSection = document.getElementById('dashboard-section');
            if (dashboardSection && dashboardSection.classList.contains('active')) {
                await Dashboard.loadMfaStatus();
                await loadWebAuthnCredentials();
            }
        } catch (error) {
            console.error('Error refreshing app data:', error);
        }
    },

    /**
     * Handle API errors globally
     */
    handleError(error, context = '') {
        console.error(`Error in ${context}:`, error);
        
        if (error.message.includes('401') || error.message.includes('Unauthorized')) {
            Auth.clearAuth();
            showLogin();
            showToast('Your session has expired. Please log in again.', 'error');
        } else if (error.message.includes('Network')) {
            showToast('Network error. Please check your connection.', 'error');
        } else {
            showToast(error.message || 'An unexpected error occurred', 'error');
        }
    },

    /**
     * Toggle theme (if implementing theme switcher)
     */
    toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        
        document.documentElement.setAttribute('data-theme', newTheme);
        localStorage.setItem('authx_theme', newTheme);
        
        showToast(`Switched to ${newTheme} theme`, 'info');
    }
};

/**
 * Global error handler
 */
window.addEventListener('error', (e) => {
    console.error('Global error:', e.error);
    App.handleError(e.error, 'Global');
});

window.addEventListener('unhandledrejection', (e) => {
    console.error('Unhandled promise rejection:', e.reason);
    App.handleError(e.reason, 'Promise');
});

/**
 * Development helpers
 */
if (ENV.IS_DEVELOPMENT) {
    // Expose app instance for debugging
    window.AuthXApp = App;
    window.Auth = Auth;
    window.Dashboard = Dashboard;
    
    // Add development console styling
    console.log(
        '%c🔐 AuthX Frontend %c- Development Mode',
        'background: #2563eb; color: white; padding: 4px 8px; border-radius: 4px; font-weight: bold;',
        'background: #f59e0b; color: white; padding: 4px 8px; border-radius: 4px;'
    );
    
    // Add debug info
    console.table({
        'WebAuthn Support': FEATURES.WEBAUTHN_SUPPORTED,
        'Storage Available': FEATURES.STORAGE_AVAILABLE,
        'HTTPS Required': FEATURES.HTTPS_REQUIRED,
        'Mobile Device': ENV.IS_MOBILE,
        'Safari Browser': ENV.IS_SAFARI
    });
}

/**
 * Initialize app when DOM is ready
 */
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => App.init());
} else {
    App.init();
}

/**
 * Service Worker registration (for future PWA features)
 */
if ('serviceWorker' in navigator && ENV.IS_DEVELOPMENT === false) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then(registration => {
                console.log('SW registered: ', registration);
            })
            .catch(registrationError => {
                console.log('SW registration failed: ', registrationError);
            });
    });
}