// AuthX Frontend Configuration

const CONFIG = {
    // API Base URL - automatically detects environment
    API_BASE_URL: (() => {
        // Check if running locally
        if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
            return 'http://localhost:8080/api/v1';
        }
        // Production environment - update this with your Render backend URL
        return window.BACKEND_URL || 'https://authx-backend-n1ls.onrender.com/api/v1';
    })(),

    // WebAuthn Configuration
    WEBAUTHN: {
        RP_ID: (() => {
            if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
                return 'localhost';
            }
            return location.hostname;
        })(),
        RP_NAME: 'AuthX MFA System',
        ORIGIN: window.location.origin,
        TIMEOUT: 300000, // 5 minutes
    },

    // Token storage keys
    STORAGE_KEYS: {
        ACCESS_TOKEN: 'authx_access_token',
        REFRESH_TOKEN: 'authx_refresh_token',
        MFA_TOKEN: 'authx_mfa_token',
        USER_INFO: 'authx_user_info'
    },

    // API Endpoints
    ENDPOINTS: {
        // Auth endpoints
        REGISTER: '/auth/register',
        LOGIN: '/auth/login',
        REFRESH: '/auth/refresh',
        LOGOUT: '/auth/logout',
        MFA_VERIFY: '/auth/mfa/verify',
        FORGOT_PASSWORD: '/auth/forgot-password',
        RESET_PASSWORD: '/auth/reset-password',

        // User endpoints
        USER_PROFILE: '/users/profile',
        USER_AUDIT_LOGS: '/users/audit-logs',
        CHANGE_PASSWORD: '/users/change-password',

        // MFA endpoints
        MFA_METHODS: '/mfa/methods',
        MFA_ENABLE: '/mfa/enable',
        MFA_DISABLE: '/mfa/disable',
        MFA_PREFERRED_METHOD: '/mfa/preferred-method',

        // WebAuthn endpoints
        WEBAUTHN_START_REGISTRATION: '/mfa/setup/webauthn/start',
        WEBAUTHN_FINISH_REGISTRATION: '/mfa/setup/webauthn/finish',
        WEBAUTHN_CHALLENGE: '/mfa/webauthn/challenge',
        WEBAUTHN_DELETE: '/mfa/webauthn',

        // OTP endpoints
        OTP_SEND: '/mfa/setup/otp/send',
        MFA_SEND_OTP: '/mfa/setup/otp/send',

        // Admin endpoints
        ADMIN_STATS: '/admin/stats',
        ADMIN_USERS: '/admin/users',
        ADMIN_USER_BY_ID: '/admin/users',
        ADMIN_USERS_SEARCH: '/admin/users/search',
        ADMIN_ASSIGN_ROLE: '/admin/users',
        ADMIN_REMOVE_ROLE: '/admin/users',
        ADMIN_UPDATE_STATUS: '/admin/users',
        ADMIN_UNLOCK_USER: '/admin/users',
        ADMIN_DELETE_USER: '/admin/users',
        ADMIN_BULK_STATUS: '/admin/users/bulk/status',
        ADMIN_BULK_UNLOCK: '/admin/users/bulk/unlock',
        ADMIN_ROLES: '/admin/roles',
        ADMIN_AUDIT_LOGS: '/admin/audit-logs',
        ADMIN_AUDIT_EVENTS: '/admin/audit-logs/events',
        ADMIN_SYSTEM_HEALTH: '/admin/system/health',
        ADMIN_RATE_LIMIT_RESET: '/admin/rate-limit/reset'
    },

    // UI Settings
    UI: {
        TOAST_DURATION: 5000,
        LOADING_MIN_DURATION: 500,
        AUTO_REFRESH_INTERVAL: 300000 // 5 minutes
    },

    // Error messages
    ERRORS: {
        NETWORK_ERROR: 'Network error. Please check your connection.',
        UNAUTHORIZED: 'Your session has expired. Please log in again.',
        WEBAUTHN_NOT_SUPPORTED: 'WebAuthn is not supported in this browser.',
        WEBAUTHN_FAILED: 'WebAuthn authentication failed. Please try again.',
        GENERIC_ERROR: 'An unexpected error occurred. Please try again.'
    }
};

// Feature detection
const FEATURES = {
    WEBAUTHN_SUPPORTED: (() => {
        return window.PublicKeyCredential !== undefined &&
            window.navigator.credentials !== undefined &&
            typeof window.navigator.credentials.create === 'function' &&
            typeof window.navigator.credentials.get === 'function';
    })(),

    STORAGE_AVAILABLE: (() => {
        try {
            const test = '__storage_test__';
            localStorage.setItem(test, test);
            localStorage.removeItem(test);
            return true;
        } catch (e) {
            return false;
        }
    })(),

    HTTPS_REQUIRED: location.protocol !== 'https:' && location.hostname !== 'localhost'
};

// Environment detection
const ENV = {
    IS_DEVELOPMENT: location.hostname === 'localhost' || location.hostname === '127.0.0.1',
    IS_MOBILE: /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent),
    IS_SAFARI: /^((?!chrome|android).)*safari/i.test(navigator.userAgent)
};

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { CONFIG, FEATURES, ENV };
}