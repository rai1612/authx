// AuthX Authentication Module

/**
 * Authentication state management
 */
const Auth = {
    currentUser: null,
    mfaToken: null,
    
    /**
     * Check if user is authenticated
     */
    isAuthenticated() {
        return !!localStorage.getItem(CONFIG.STORAGE_KEYS.ACCESS_TOKEN);
    },

    /**
     * Check if MFA is in progress
     */
    isMfaInProgress() {
        return !!localStorage.getItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
    },

    /**
     * Get current user info
     */
    getCurrentUser() {
        return Storage.get(CONFIG.STORAGE_KEYS.USER_INFO);
    },

    /**
     * Set authentication tokens
     */
    setTokens(tokens) {
        if (tokens.accessToken) {
            localStorage.setItem(CONFIG.STORAGE_KEYS.ACCESS_TOKEN, tokens.accessToken);
        }
        if (tokens.refreshToken) {
            localStorage.setItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN, tokens.refreshToken);
        }
        if (tokens.mfaToken) {
            localStorage.setItem(CONFIG.STORAGE_KEYS.MFA_TOKEN, tokens.mfaToken);
        }
    },

    /**
     * Clear authentication data
     */
    clearAuth() {
        localStorage.removeItem(CONFIG.STORAGE_KEYS.ACCESS_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_INFO);
        this.currentUser = null;
        this.mfaToken = null;
    }
};

/**
 * User Registration
 */
async function register() {
    const form = document.getElementById('register-form');
    const formData = new FormData(form);
    
    const userData = {
        username: formData.get('username') || document.getElementById('register-username').value,
        email: formData.get('email') || document.getElementById('register-email').value,
        password: formData.get('password') || document.getElementById('register-password').value,
        phoneNumber: formData.get('phoneNumber') || document.getElementById('register-phone').value
    };

    // Validation
    if (!userData.username || !userData.email || !userData.password) {
        showToast('Please fill in all required fields', 'error');
        return;
    }

    if (!isValidEmail(userData.email)) {
        showToast('Please enter a valid email address', 'error');
        return;
    }

    if (!isValidPassword(userData.password)) {
        showToast('Password must be at least 8 characters with uppercase, lowercase, number, and special character', 'error');
        return;
    }

    try {
        const response = await api.post(CONFIG.ENDPOINTS.REGISTER, userData);
        
        showToast('Registration successful! Please log in.', 'success');
        clearForm('register-form');
        showLogin();
        
    } catch (error) {
        console.error('Registration error:', error);
        showToast(error.message || 'Registration failed', 'error');
    }
}

/**
 * User Login
 */
async function login() {
    const form = document.getElementById('login-form');
    const formData = new FormData(form);
    
    const loginData = {
        email: formData.get('email') || document.getElementById('login-email').value,
        password: formData.get('password') || document.getElementById('login-password').value
    };

    // Validation
    if (!loginData.email || !loginData.password) {
        showToast('Please enter your email and password', 'error');
        return;
    }

    if (!isValidEmail(loginData.email)) {
        showToast('Please enter a valid email address', 'error');
        return;
    }

    try {
        console.log('Attempting login for:', loginData.email);
        const response = await api.post(CONFIG.ENDPOINTS.LOGIN, loginData);
        console.log('Login response:', response);
        
        if (response.mfaRequired) {
            // MFA is required
            console.log('MFA required, setting MFA token and showing challenge');
            Auth.setTokens({ mfaToken: response.mfaToken });
            showToast('Please complete multi-factor authentication', 'info');
            clearForm('login-form');
            
            // Small delay to ensure DOM is ready
            setTimeout(() => {
                showMfaChallenge();
            }, 100);
        } else {
            // Login successful
            console.log('Login successful without MFA');
            Auth.setTokens(response);
            await loadUserProfile();
            updateNavigation(); // Update navigation to show admin link if user has admin role
            showToast('Login successful!', 'success');
            clearForm('login-form');
            showDashboard();
        }
        
    } catch (error) {
        console.error('Login error:', error);
        showToast(error.message || 'Login failed', 'error');
    }
}

/**
 * Logout
 */
async function logout() {
    try {
        // Call logout endpoint if authenticated
        if (Auth.isAuthenticated()) {
            await api.post(CONFIG.ENDPOINTS.LOGOUT);
        }
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        // Clear local auth data regardless of API call success
        Auth.clearAuth();
        showToast('Logged out successfully', 'info');
        showWelcome();
    }
}

/**
 * Show MFA Challenge
 */
async function showMfaChallenge() {
    console.log('showMfaChallenge called');
    showSection('mfa-section');
    
    // Show method selection view by default
    showMfaMethodSelection();
    
    try {
        // Get user's MFA methods to determine what to show
        console.log('Attempting to load MFA methods...');
        const methods = await api.get(CONFIG.ENDPOINTS.MFA_METHODS, { useMfaToken: true });
        console.log('MFA methods loaded:', methods);
        console.log('WebAuthn credentials count:', methods.webAuthnCredentials ? methods.webAuthnCredentials.length : 0);
        console.log('Preferred method:', methods.preferredMethod);
        
        // Render available methods in the new UI
        renderMfaMethodSelection(methods);
        
    } catch (error) {
        console.error('Error loading MFA methods:', error);
        console.error('Error details:', error.message, error.status);
        
        // Show error message
        showToast('Error loading MFA options. Please try again.', 'error');
        
        // Show basic method selection with limited options
        renderMfaMethodSelection({
            emailConfigured: true,
            smsConfigured: false,
            webAuthnCredentials: [],
            preferredMethod: 'OTP_EMAIL'
        });
    }
}

/**
 * Show MFA method selection view
 */
function showMfaMethodSelection() {
    document.getElementById('mfa-method-selection').style.display = 'block';
    document.getElementById('mfa-verification-view').style.display = 'none';
}

/**
 * Render MFA method selection options
 */
function renderMfaMethodSelection(methods) {
    const container = document.getElementById('mfa-methods-container');
    if (!container) return;
    
    const availableMethods = [];
    
    // Email OTP - always available
    if (methods.emailConfigured !== false) {
        availableMethods.push({
            id: 'OTP_EMAIL',
            name: 'Email OTP',
            description: 'Receive verification code via email',
            icon: 'fas fa-envelope',
            isPreferred: methods.preferredMethod === 'OTP_EMAIL',
            available: true
        });
    }
    
    // SMS OTP - if phone is configured
    if (methods.smsConfigured) {
        availableMethods.push({
            id: 'OTP_SMS',
            name: 'SMS OTP',
            description: 'Receive verification code via text message',
            icon: 'fas fa-sms',
            isPreferred: methods.preferredMethod === 'OTP_SMS',
            available: true
        });
    }
    
    // WebAuthn - if credentials exist and browser supports it
    if (methods.webAuthnCredentials && methods.webAuthnCredentials.length > 0 && WebAuthn && WebAuthn.isSupported()) {
        availableMethods.push({
            id: 'WEBAUTHN',
            name: 'WebAuthn',
            description: 'Use biometric or security key authentication',
            icon: 'fas fa-fingerprint',
            isPreferred: methods.preferredMethod === 'WEBAUTHN',
            available: true
        });
    }
    
    if (availableMethods.length === 0) {
        container.innerHTML = `
            <div class="no-methods-message">
                <i class="fas fa-exclamation-triangle"></i>
                <h4>No authentication methods available</h4>
                <p>Please contact support for assistance.</p>
            </div>
        `;
        return;
    }
    
    // Sort methods - preferred first, then alphabetically
    availableMethods.sort((a, b) => {
        if (a.isPreferred && !b.isPreferred) return -1;
        if (!a.isPreferred && b.isPreferred) return 1;
        return a.name.localeCompare(b.name);
    });
    
    container.innerHTML = availableMethods.map(method => `
        <div class="mfa-method-card ${method.isPreferred ? 'preferred' : ''}" 
             onclick="selectMfaMethod('${method.id}')"
             tabindex="0"
             role="button"
             aria-label="Select ${method.name} authentication method"
             onkeydown="handleMfaCardKeydown(event, '${method.id}')">
            <div class="method-icon">
                <i class="${method.icon}"></i>
            </div>
            <div class="method-info">
                <h4>${method.name}</h4>
                <p>${method.description}</p>
                ${method.isPreferred ? '<span class="preferred-badge"><i class="fas fa-star"></i> Preferred</span>' : ''}
            </div>
            <div class="method-action">
                <i class="fas fa-arrow-right"></i>
            </div>
        </div>
    `).join('');
}

/**
 * Select MFA method and show verification view
 */
function selectMfaMethod(methodId) {
    console.log('Selected MFA method:', methodId);
    
    // Hide method selection, show verification view
    document.getElementById('mfa-method-selection').style.display = 'none';
    document.getElementById('mfa-verification-view').style.display = 'block';
    
    // Hide all verification content
    document.querySelectorAll('.verification-content').forEach(el => {
        el.style.display = 'none';
    });
    
    // Show selected method verification
    switch (methodId) {
        case 'OTP_EMAIL':
            document.getElementById('email-otp-verification').style.display = 'block';
            document.getElementById('verification-title').textContent = 'Email OTP Verification';
            // Auto-send email OTP when selected
            sendEmailOTP();
            break;
            
        case 'OTP_SMS':
            document.getElementById('sms-otp-verification').style.display = 'block';
            document.getElementById('verification-title').textContent = 'SMS OTP Verification';
            // Auto-send SMS OTP when selected
            sendSmsOTP();
            break;
            
        case 'WEBAUTHN':
            document.getElementById('webauthn-verification').style.display = 'block';
            document.getElementById('verification-title').textContent = 'WebAuthn Authentication';
            // Don't auto-trigger WebAuthn - let user click the button
            break;
            
        default:
            console.error('Unknown MFA method:', methodId);
            backToMethodSelection();
            return;
    }
}

/**
 * Handle keyboard events for MFA method cards
 */
function handleMfaCardKeydown(event, methodId) {
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        selectMfaMethod(methodId);
    }
}

/**
 * Go back to method selection
 */
function backToMethodSelection() {
    document.getElementById('mfa-method-selection').style.display = 'block';
    document.getElementById('mfa-verification-view').style.display = 'none';
    
    // Clear any OTP inputs
    const emailOtpInput = document.getElementById('email-otp-code');
    const smsOtpInput = document.getElementById('sms-otp-code');
    if (emailOtpInput) emailOtpInput.value = '';
    if (smsOtpInput) smsOtpInput.value = '';
}

/**
 * Send Email OTP
 */
async function sendEmailOTP() {
    try {
        await api.post(CONFIG.ENDPOINTS.OTP_SEND, { method: 'EMAIL' }, { useMfaToken: true });
        showToast('OTP sent to your email', 'success');
    } catch (error) {
        console.error('Error sending email OTP:', error);
        showToast(error.message || 'Failed to send OTP', 'error');
    }
}

/**
 * Verify Email OTP
 */
async function verifyEmailOTP() {
    const otpCode = document.getElementById('otp-code').value;
    
    if (!otpCode || otpCode.length !== 6) {
        showToast('Please enter a valid 6-digit code', 'error');
        return;
    }

    // Disable verify button during attempt
    const verifyButton = document.querySelector('button[onclick="verifyEmailOTP()"]');
    if (verifyButton) {
        verifyButton.disabled = true;
        verifyButton.textContent = 'Verifying...';
    }

    try {
        const mfaToken = localStorage.getItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
        const response = await api.post(CONFIG.ENDPOINTS.MFA_VERIFY, {
            mfaToken: mfaToken,
            otpCode: otpCode
        });

        // MFA verification successful
        Auth.setTokens(response);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
        
        await loadUserProfile();
        showToast('Authentication successful!', 'success');
        document.getElementById('otp-code').value = '';
        showDashboard();
        
    } catch (error) {
        console.error('OTP verification error:', error);
        
        // Handle rate limiting specifically
        if (error.isRateLimit) {
            handleMfaRateLimit(error.retryAfter);
        } else {
            showToast(error.message || 'OTP verification failed', 'error');
            document.getElementById('otp-code').value = '';
        }
    } finally {
        // Re-enable verify button
        if (verifyButton) {
            verifyButton.disabled = false;
            verifyButton.textContent = 'Verify';
        }
    }
}

/**
 * Handle MFA Rate Limiting
 */
function handleMfaRateLimit(retryAfterSeconds) {
    console.log('Rate limit triggered, disabling buttons for', retryAfterSeconds, 'seconds');
    
    // Clear OTP input
    const otpInput = document.getElementById('otp-code');
    if (otpInput) otpInput.value = '';
    
    // Disable verify and resend buttons with multiple selectors for reliability
    const verifyButton = document.querySelector('button[onclick="verifyEmailOTP()"]') || 
                        document.querySelector('.btn-primary[onclick="verifyEmailOTP()"]') ||
                        Array.from(document.querySelectorAll('button')).find(btn => btn.textContent.trim() === 'Verify');
    
    const resendButton = document.querySelector('button[onclick="sendEmailOTP()"]') || 
                        document.querySelector('.btn-secondary[onclick="sendEmailOTP()"]') ||
                        Array.from(document.querySelectorAll('button')).find(btn => btn.textContent.trim() === 'Resend Code');
    
    console.log('Found buttons:', { verifyButton: !!verifyButton, resendButton: !!resendButton });
    
    if (verifyButton) {
        verifyButton.disabled = true;
        verifyButton.setAttribute('data-rate-limited', 'true');
        console.log('Verify button disabled');
    }
    if (resendButton) {
        resendButton.disabled = true;
        resendButton.setAttribute('data-rate-limited', 'true');
        console.log('Resend button disabled');
    }
    
    // Show rate limit message
    showToast(`Too many attempts! Please wait ${Math.ceil(retryAfterSeconds / 60)} minutes before trying again.`, 'error', 8000);
    
    // Start countdown timer
    startMfaRateLimitCountdown(retryAfterSeconds);
}

/**
 * Start MFA Rate Limit Countdown
 */
function startMfaRateLimitCountdown(seconds) {
    let remainingTime = seconds;
    
    // Create or update countdown display
    let countdownDiv = document.getElementById('mfa-rate-limit-countdown');
    if (!countdownDiv) {
        countdownDiv = document.createElement('div');
        countdownDiv.id = 'mfa-rate-limit-countdown';
        countdownDiv.className = 'rate-limit-countdown';
        
        // Insert after the OTP input
        const otpOption = document.getElementById('email-otp-option');
        if (otpOption) {
            otpOption.appendChild(countdownDiv);
        }
    }
    
    const updateCountdown = () => {
        if (remainingTime <= 0) {
            // Re-enable buttons with robust selectors
            const verifyButton = document.querySelector('button[onclick="verifyEmailOTP()"]') || 
                                document.querySelector('.btn-primary[onclick="verifyEmailOTP()"]') ||
                                Array.from(document.querySelectorAll('button')).find(btn => btn.textContent.trim() === 'Verify');
            
            const resendButton = document.querySelector('button[onclick="sendEmailOTP()"]') || 
                                document.querySelector('.btn-secondary[onclick="sendEmailOTP()"]') ||
                                Array.from(document.querySelectorAll('button')).find(btn => btn.textContent.trim() === 'Resend Code');
            
            console.log('Re-enabling buttons after rate limit countdown');
            
            if (verifyButton) {
                verifyButton.disabled = false;
                verifyButton.removeAttribute('data-rate-limited');
                console.log('Verify button re-enabled');
            }
            if (resendButton) {
                resendButton.disabled = false;
                resendButton.removeAttribute('data-rate-limited');
                console.log('Resend button re-enabled');
            }
            
            // Remove countdown display
            if (countdownDiv && countdownDiv.parentNode) {
                countdownDiv.parentNode.removeChild(countdownDiv);
            }
            
            showToast('You can now try again!', 'success');
            return;
        }
        
        const minutes = Math.floor(remainingTime / 60);
        const seconds = remainingTime % 60;
        const timeString = minutes > 0 
            ? `${minutes}m ${seconds}s` 
            : `${seconds}s`;
            
        countdownDiv.innerHTML = `
            <div class="alert alert-warning">
                <i class="fas fa-clock"></i> 
                <strong>Rate Limited:</strong> Too many failed attempts.<br>
                <strong>Try again in:</strong> ${timeString}<br>
                <small>This helps protect your account from unauthorized access.</small>
            </div>
        `;
        
        remainingTime--;
    };
    
    // Update immediately and then every second
    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);
    
    // Clean up interval when countdown reaches zero
    setTimeout(() => clearInterval(interval), (seconds + 1) * 1000);
}

/**
 * Send Email OTP
 */
async function sendEmailOTP() {
    try {
        console.log('Sending email OTP...');
        const response = await api.post(CONFIG.ENDPOINTS.MFA_SEND_OTP, { method: 'EMAIL' }, { useMfaToken: true });
        showToast('OTP sent to your email!', 'success');
        console.log('Email OTP sent successfully');
    } catch (error) {
        console.error('Error sending email OTP:', error);
        showToast(error.message || 'Failed to send email OTP', 'error');
    }
}

/**
 * Send SMS OTP
 */
async function sendSmsOTP() {
    try {
        console.log('Sending SMS OTP...');
        const response = await api.post(CONFIG.ENDPOINTS.MFA_SEND_OTP, { method: 'SMS' }, { useMfaToken: true });
        showToast('OTP sent to your phone!', 'success');
        console.log('SMS OTP sent successfully');
    } catch (error) {
        console.error('Error sending SMS OTP:', error);
        showToast(error.message || 'Failed to send SMS OTP', 'error');
    }
}

/**
 * Verify Email OTP
 */
async function verifyEmailOTP() {
    const otpCode = document.getElementById('email-otp-code').value.trim();
    
    if (!otpCode) {
        showToast('Please enter the OTP code', 'error');
        return;
    }
    
    if (otpCode.length !== 6 || !/^\d{6}$/.test(otpCode)) {
        showToast('Please enter a valid 6-digit code', 'error');
        return;
    }
    
    try {
        console.log('Verifying email OTP...');
        const mfaToken = localStorage.getItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
        if (!mfaToken) {
            throw new Error('MFA token not found. Please try logging in again.');
        }
        const response = await api.post(CONFIG.ENDPOINTS.MFA_VERIFY, {
            mfaToken: mfaToken,
            otpCode: otpCode
        });
        
        console.log('Email OTP verification successful');
        Auth.setTokens(response);
        await loadUserProfile();
        updateNavigation();
        showToast('Authentication successful!', 'success');
        showDashboard();
        
    } catch (error) {
        console.error('Error verifying email OTP:', error);
        showToast(error.message || 'Invalid OTP code', 'error');
        document.getElementById('email-otp-code').value = '';
        
        // Handle rate limiting
        if (error.rateLimited) {
            handleRateLimit(error.secondsUntilReset || 300);
        }
    }
}

/**
 * Verify SMS OTP
 */
async function verifySmsOTP() {
    const otpCode = document.getElementById('sms-otp-code').value.trim();
    
    if (!otpCode) {
        showToast('Please enter the OTP code', 'error');
        return;
    }
    
    if (otpCode.length !== 6 || !/^\d{6}$/.test(otpCode)) {
        showToast('Please enter a valid 6-digit code', 'error');
        return;
    }
    
    try {
        console.log('Verifying SMS OTP...');
        const mfaToken = localStorage.getItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
        if (!mfaToken) {
            throw new Error('MFA token not found. Please try logging in again.');
        }
        const response = await api.post(CONFIG.ENDPOINTS.MFA_VERIFY, {
            mfaToken: mfaToken,
            otpCode: otpCode
        });
        
        console.log('SMS OTP verification successful');
        Auth.setTokens(response);
        await loadUserProfile();
        updateNavigation();
        showToast('Authentication successful!', 'success');
        showDashboard();
        
    } catch (error) {
        console.error('Error verifying SMS OTP:', error);
        showToast(error.message || 'Invalid OTP code', 'error');
        document.getElementById('sms-otp-code').value = '';
        
        // Handle rate limiting
        if (error.rateLimited) {
            handleRateLimit(error.secondsUntilReset || 300);
        }
    }
}

/**
 * Load User Profile
 */
async function loadUserProfile() {
    try {
        const profile = await api.get(CONFIG.ENDPOINTS.USER_PROFILE);
        Auth.currentUser = profile;
        Storage.set(CONFIG.STORAGE_KEYS.USER_INFO, profile);
        updateNavigation(); // Update navigation after profile is loaded
        return profile;
    } catch (error) {
        console.error('Error loading user profile:', error);
        throw error;
    }
}

/**
 * Refresh Token
 */
async function refreshToken() {
    const refreshToken = localStorage.getItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN);
    
    if (!refreshToken) {
        throw new Error('No refresh token available');
    }

    try {
        const response = await api.post(CONFIG.ENDPOINTS.REFRESH, {
            refreshToken: refreshToken
        });

        Auth.setTokens(response);
        return response;
        
    } catch (error) {
        console.error('Token refresh failed:', error);
        Auth.clearAuth();
        showLogin();
        throw error;
    }
}

/**
 * Setup OTP input handlers for the new MFA UI
 */
function setupOtpInputHandlers() {
    // Email OTP input
    const emailOtpInput = document.getElementById('email-otp-code');
    if (emailOtpInput) {
        emailOtpInput.addEventListener('input', (e) => {
            // Only allow numeric input and limit to 6 digits
            e.target.value = e.target.value.replace(/\D/g, '').substring(0, 6);
        });
        
        emailOtpInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && e.target.value.length === 6) {
                verifyEmailOTP();
            }
        });
    }
    
    // SMS OTP input
    const smsOtpInput = document.getElementById('sms-otp-code');
    if (smsOtpInput) {
        smsOtpInput.addEventListener('input', (e) => {
            // Only allow numeric input and limit to 6 digits
            e.target.value = e.target.value.replace(/\D/g, '').substring(0, 6);
        });
        
        smsOtpInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && e.target.value.length === 6) {
                verifySmsOTP();
            }
        });
    }
}

/**
 * Initialize Authentication
 */
function initializeAuth() {
    // Set up form event listeners
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', (e) => {
            e.preventDefault();
            login();
        });
    }

    const registerForm = document.getElementById('register-form');
    if (registerForm) {
        registerForm.addEventListener('submit', (e) => {
            e.preventDefault();
            register();
        });
    }

    // Set up OTP inputs for the new MFA UI
    setupOtpInputHandlers();

    // Check for password reset token first, before checking authentication
    const urlParams = new URLSearchParams(window.location.search);
    const resetToken = urlParams.get('token');
    
    if (resetToken) {
        // Password reset flow takes priority over authentication state
        console.log('Password reset token detected, showing reset form');
        return; // Let auth-password.js handle the reset flow
    }

    // Check authentication state on page load
    if (Auth.isAuthenticated()) {
        loadUserProfile().then(() => {
            showDashboard();
        }).catch(() => {
            Auth.clearAuth();
            showWelcome();
        });
    } else if (Auth.isMfaInProgress()) {
        showMfaChallenge();
    } else {
        showWelcome();
    }

    // Set up automatic token refresh
    setInterval(async () => {
        if (Auth.isAuthenticated()) {
            try {
                await refreshToken();
            } catch (error) {
                console.error('Auto token refresh failed:', error);
            }
        }
    }, CONFIG.UI.AUTO_REFRESH_INTERVAL);
}

// Navigation functions
function showWelcome() {
    showSection('welcome-section');
}

function showLogin() {
    showSection('login-section');
}

function showRegister() {
    showSection('register-section');
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeAuth);
} else {
    initializeAuth();
}