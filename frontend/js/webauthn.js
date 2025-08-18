// AuthX WebAuthn Module

/**
 * WebAuthn Support and Utilities
 */
const WebAuthn = {
    /**
     * Check if WebAuthn is supported
     */
    isSupported() {
        return FEATURES.WEBAUTHN_SUPPORTED;
    },

    /**
     * Check if HTTPS is required and available
     */
    isSecureContext() {
        return window.isSecureContext || location.hostname === 'localhost';
    },

    /**
     * Show WebAuthn not supported message
     */
    showNotSupported() {
        showToast(CONFIG.ERRORS.WEBAUTHN_NOT_SUPPORTED, 'error');
    },

    /**
     * Show HTTPS required message
     */
    showHTTPSRequired() {
        showToast('WebAuthn requires HTTPS. Please use a secure connection.', 'error');
    }
};

/**
 * Perform WebAuthn Authentication (for MFA)
 */
async function performWebAuthnAuth() {
    if (!WebAuthn.isSupported()) {
        WebAuthn.showNotSupported();
        return;
    }

    if (!WebAuthn.isSecureContext()) {
        WebAuthn.showHTTPSRequired();
        return;
    }

    try {
        showLoading();
        
        // Get WebAuthn challenge from server
        const challengeResponse = await api.post(CONFIG.ENDPOINTS.WEBAUTHN_CHALLENGE, {}, { useMfaToken: true });
        const challengeData = JSON.parse(challengeResponse.challenge);
        
        // First try: Force platform authenticators (local biometric)
        try {
            const platformOptions = {
                challenge: base64UrlToArrayBuffer(challengeData.challenge),
                timeout: 30000, // Shorter timeout for local auth
                rpId: challengeData.rpId,
                allowCredentials: challengeData.allowCredentials.map(cred => ({
                    type: cred.type,
                    id: base64UrlToArrayBuffer(cred.id)
                })),
                userVerification: 'required',        // Force biometric verification
                authenticatorAttachment: 'platform'  // Only local device authenticators
            };

            showToast('Please use your device\'s biometric authentication', 'info');
            
            const assertion = await navigator.credentials.get({
                publicKey: platformOptions
            });
            
            if (assertion) {
                await processWebAuthnAssertion(assertion);
                return;
            }
        } catch (platformError) {
            console.log('Platform authenticator failed, trying fallback:', platformError);
            
            // Fallback: Allow cross-platform if platform fails
            showToast('Biometric authentication not available, trying alternative method...', 'warning');
        }
        
        // Fallback: Allow any authenticator type
        const fallbackOptions = {
            challenge: base64UrlToArrayBuffer(challengeData.challenge),
            timeout: challengeData.timeout,
            rpId: challengeData.rpId,
            allowCredentials: challengeData.allowCredentials.map(cred => ({
                type: cred.type,
                id: base64UrlToArrayBuffer(cred.id)
            })),
            userVerification: 'preferred'  // Allow but prefer verification
        };

        // Get assertion from any available authenticator
        const assertion = await navigator.credentials.get({
            publicKey: fallbackOptions
        });
        
        if (assertion) {
            await processWebAuthnAssertion(assertion);
        }

    } catch (error) {
        console.error('WebAuthn authentication error:', error);
        
        if (error.name === 'NotAllowedError') {
            showToast('WebAuthn authentication was cancelled or failed', 'error');
        } else if (error.name === 'InvalidStateError') {
            showToast('This device is not registered. Please use a different authentication method.', 'error');
        } else {
            showToast(error.message || CONFIG.ERRORS.WEBAUTHN_FAILED, 'error');
        }
    } finally {
        hideLoading();
    }
}

/**
 * Process WebAuthn assertion response
 */
async function processWebAuthnAssertion(assertion) {
    if (!assertion) {
        throw new Error('WebAuthn assertion failed');
    }

    // Prepare response for server
    const webAuthnResponse = {
        id: assertion.id,
        rawId: arrayBufferToBase64Url(assertion.rawId),
        type: assertion.type,
        response: {
            authenticatorData: arrayBufferToBase64Url(assertion.response.authenticatorData),
            clientDataJSON: arrayBufferToBase64Url(assertion.response.clientDataJSON),
            signature: arrayBufferToBase64Url(assertion.response.signature),
            userHandle: assertion.response.userHandle ? arrayBufferToBase64Url(assertion.response.userHandle) : null
        }
    };

    // Send to server for verification
    const mfaToken = localStorage.getItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
    const verificationResponse = await api.post(CONFIG.ENDPOINTS.MFA_VERIFY, {
        mfaToken: mfaToken,
        webAuthnResponse: JSON.stringify(webAuthnResponse)
    });

    // MFA verification successful
    Auth.setTokens(verificationResponse);
    localStorage.removeItem(CONFIG.STORAGE_KEYS.MFA_TOKEN);
    
    await loadUserProfile();
    showToast('WebAuthn authentication successful!', 'success');
    showDashboard();
}

/**
 * Show WebAuthn Setup Modal
 */
function showWebAuthnSetup() {
    if (!WebAuthn.isSupported()) {
        WebAuthn.showNotSupported();
        return;
    }

    if (!WebAuthn.isSecureContext()) {
        WebAuthn.showHTTPSRequired();
        return;
    }

    showModal('webauthn-modal');
    
    // Set up event listeners for authenticator type selection
    document.querySelectorAll('input[name="authenticator-type"]').forEach(radio => {
        radio.addEventListener('change', updateWebAuthnUI);
    });
    
    // Initialize UI based on default selection
    updateWebAuthnUI();
}

/**
 * Update WebAuthn UI based on selected authenticator type
 */
function updateWebAuthnUI() {
    const selectedTypeElement = document.querySelector('input[name="authenticator-type"]:checked');
    const selectedType = selectedTypeElement ? selectedTypeElement.value : 'platform'; // Default to platform
    const instructionElement = document.getElementById('webauthn-instruction');
    const buttonTextElement = document.getElementById('register-btn-text');
    
    // Check if elements exist before proceeding
    if (!instructionElement || !buttonTextElement) {
        console.warn('WebAuthn UI elements not found');
        return;
    }
    
    if (selectedType === 'platform') {
        instructionElement.textContent = 'Your browser will prompt you to use your device\'s fingerprint or face recognition to create a secure credential.';
        buttonTextElement.innerHTML = '<i class="fas fa-fingerprint"></i> Setup Biometric Authentication';
    } else {
        instructionElement.textContent = 'Your browser will prompt you to use a security key or create a passkey that can sync across your devices.';
        buttonTextElement.innerHTML = '<i class="fas fa-key"></i> Setup Security Key/Passkey';
    }
}

/**
 * Detect browser capabilities
 */
function detectBrowserCapabilities() {
    const isChrome = navigator.userAgent.includes('Chrome') && !navigator.userAgent.includes('Edg');
    const isSafari = navigator.userAgent.includes('Safari') && !navigator.userAgent.includes('Chrome');
    const isFirefox = navigator.userAgent.includes('Firefox');
    const isEdge = navigator.userAgent.includes('Edg');
    
    return {
        browser: isChrome ? 'chrome' : isSafari ? 'safari' : isFirefox ? 'firefox' : isEdge ? 'edge' : 'unknown',
        supportsWebAuthn: 'credentials' in navigator && 'create' in navigator.credentials
    };
}

/**
 * Register WebAuthn Credential
 */
async function registerWebAuthn() {
    const nickname = document.getElementById('credential-nickname').value.trim();
    
    if (!nickname) {
        showToast('Please enter a nickname for this credential', 'error');
        return;
    }

    if (!WebAuthn.isSupported()) {
        WebAuthn.showNotSupported();
        return;
    }

    if (!WebAuthn.isSecureContext()) {
        WebAuthn.showHTTPSRequired();
        return;
    }

    // Get selected authenticator type and browser info before try block
    const authenticatorTypeElement = document.querySelector('input[name="authenticator-type"]:checked');
    const authenticatorType = authenticatorTypeElement ? authenticatorTypeElement.value : 'platform'; // Default to platform
    const browserInfo = detectBrowserCapabilities();
    
    try {
        showLoading();
        
        // Start WebAuthn registration process
        
        // Start WebAuthn registration
        const challengeResponse = await api.post(CONFIG.ENDPOINTS.WEBAUTHN_START_REGISTRATION);
        const challengeData = JSON.parse(challengeResponse.challenge);
        
        // Configure authenticator selection based on user choice
        let authenticatorSelection;
        let extensions = {};
        
        if (authenticatorType === 'platform') {
            // Force device-bound biometric authentication
            authenticatorSelection = {
                authenticatorAttachment: 'platform',      // Built-in authenticators only
                userVerification: 'required',             // Force biometric verification
                requireResidentKey: true,                 // Store credential on device hardware
                residentKey: 'required'                   // Force resident key for biometrics
            };
            
            // Add extensions for hardware storage preference
            extensions = {
                credProps: true                           // Request credential properties
            };
        } else {
            // Allow security keys or cross-platform passkeys
            authenticatorSelection = {
                authenticatorAttachment: 'cross-platform', // External authenticators
                userVerification: 'preferred',             // Prefer but don't require verification
                requireResidentKey: false,                 // Allow non-resident keys
                residentKey: 'preferred'                   // Prefer but don't require resident key
            };
        }
        
        // Prepare credential creation options
        const createOptions = {
            challenge: base64UrlToArrayBuffer(challengeData.challenge),
            rp: challengeData.rp,
            user: {
                id: new TextEncoder().encode(challengeData.user.id),
                name: challengeData.user.name,
                displayName: challengeData.user.displayName
            },
            pubKeyCredParams: challengeData.pubKeyCredParams,
            timeout: challengeData.timeout,
            attestation: challengeData.attestation,
            authenticatorSelection: authenticatorSelection,
            extensions: extensions,
            excludeCredentials: [] // Could be populated with existing credentials
        };

        // Create credential
        const credential = await navigator.credentials.create({
            publicKey: createOptions
        });
        
        return await processCredentialRegistration(credential, nickname);
    } catch (error) {
        console.error('WebAuthn registration error:', error);
        
        if (error.name === 'NotAllowedError') {
            showToast('WebAuthn registration was cancelled', 'error');
        } else if (error.name === 'InvalidStateError') {
            showToast('This device is already registered', 'warning');
        } else if (error.name === 'NotSupportedError') {
            showToast('WebAuthn not supported on this device', 'error');
        } else {
            showToast(error.message || 'WebAuthn registration failed', 'error');
        }
    } finally {
        hideLoading();
    }
}

/**
 * Process credential registration
 */
async function processCredentialRegistration(credential, nickname) {
    if (!credential) {
        throw new Error('WebAuthn credential creation failed');
    }

    // Prepare response for server
    const webAuthnResponse = {
        id: credential.id,
        rawId: arrayBufferToBase64Url(credential.rawId),
        type: credential.type,
        response: {
            attestationObject: arrayBufferToBase64Url(credential.response.attestationObject),
            clientDataJSON: arrayBufferToBase64Url(credential.response.clientDataJSON)
        }
    };

    // Send to server to complete registration
    await api.post(CONFIG.ENDPOINTS.WEBAUTHN_FINISH_REGISTRATION, {
        response: JSON.stringify(webAuthnResponse),
        nickname: nickname
    });

    showToast('WebAuthn credential registered successfully!', 'success');
    closeModal('webauthn-modal');
    document.getElementById('credential-nickname').value = '';
    
    // Refresh credentials list
    await loadWebAuthnCredentials();
    
    // Update password form MFA visibility
    if (window.refreshPasswordFormMfaVisibility) {
        window.refreshPasswordFormMfaVisibility();
    }
    
    // Refresh Dashboard MFA status and audit logs to show the changes
    if (typeof Dashboard !== 'undefined') {
        if (Dashboard.loadMfaStatus) {
            await Dashboard.loadMfaStatus();
        }
        if (Dashboard.loadAuditLogs) {
            await Dashboard.loadAuditLogs();
        }
    }
}

/**
 * Add new WebAuthn credential (alias for registerWebAuthn)
 */
function addWebAuthnCredential() {
    showWebAuthnSetup();
}

/**
 * Delete WebAuthn Credential
 */
async function deleteWebAuthnCredential(credentialId) {
    if (!confirm('Are you sure you want to delete this WebAuthn credential?')) {
        return;
    }

    try {
        await api.delete(`${CONFIG.ENDPOINTS.WEBAUTHN_DELETE}/${credentialId}`);
        showToast('WebAuthn credential deleted successfully', 'success');
        
        // Refresh credentials list
        await loadWebAuthnCredentials();
        
        // Update password form MFA visibility
        if (window.refreshPasswordFormMfaVisibility) {
            window.refreshPasswordFormMfaVisibility();
        }
        
        // Refresh Dashboard MFA status and audit logs to show the changes
        if (typeof Dashboard !== 'undefined') {
            if (Dashboard.loadMfaStatus) {
                await Dashboard.loadMfaStatus();
            }
            if (Dashboard.loadAuditLogs) {
                await Dashboard.loadAuditLogs();
            }
        }
        
        // Check if preferred method was updated and notify user
        try {
            const mfaMethods = await api.get(CONFIG.ENDPOINTS.MFA_METHODS);
            if (mfaMethods.webAuthnCredentials.length === 0 && mfaMethods.preferredMethod === 'OTP_EMAIL') {
                showToast('Your preferred MFA method has been automatically changed to Email since you have no WebAuthn credentials remaining', 'info');
            }
            
            // Update the MFA status display and audit logs in the dashboard if available
            if (typeof Dashboard !== 'undefined' && Dashboard.renderMfaStatus) {
                Dashboard.mfaStatus = mfaMethods;
                Dashboard.renderMfaStatus();
                // Also refresh audit logs to show the credential deletion activity
                if (Dashboard.loadAuditLogs) {
                    await Dashboard.loadAuditLogs();
                }
            }
        } catch (methodsError) {
            console.warn('Could not check MFA methods after credential deletion:', methodsError);
        }
        
    } catch (error) {
        console.error('Error deleting WebAuthn credential:', error);
        showToast(error.message || 'Failed to delete credential', 'error');
    }
}

/**
 * Load WebAuthn Credentials
 */
async function loadWebAuthnCredentials() {
    try {
        const methods = await api.get(CONFIG.ENDPOINTS.MFA_METHODS);
        const credentials = methods.webAuthnCredentials || [];
        
        // Update Dashboard mfaStatus with fresh credential data
        if (typeof Dashboard !== 'undefined' && Dashboard.mfaStatus) {
            Dashboard.mfaStatus.webAuthnCredentials = credentials;
            Dashboard.mfaStatus.webAuthnConfigured = credentials.length > 0;
        }
        
        const container = document.getElementById('webauthn-credentials');
        if (!container) return;

        if (credentials.length === 0) {
            container.innerHTML = `
                <div style="text-align: center; color: var(--text-muted); padding: 2rem;">
                    <i class="fas fa-key" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p>No WebAuthn credentials registered</p>
                    <p style="font-size: 0.875rem;">Add a credential to enable biometric authentication</p>
                </div>
            `;
            return;
        }

        container.innerHTML = credentials.map(cred => `
            <div class="credential-item">
                <div class="credential-info">
                    <h4>${escapeHtml(cred.nickname)}</h4>
                    <p>
                        <i class="fas fa-calendar"></i> Added ${formatDate(cred.createdAt)}
                        ${cred.lastUsedAt ? `• <i class="fas fa-clock"></i> Last used ${formatDate(cred.lastUsedAt)}` : ''}
                    </p>
                </div>
                <div class="credential-actions">
                    <button class="btn btn-error" onclick="deleteWebAuthnCredential(${cred.id})" 
                            title="Delete credential">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `).join('');

    } catch (error) {
        console.error('Error loading WebAuthn credentials:', error);
        const container = document.getElementById('webauthn-credentials');
        if (container) {
            container.innerHTML = `
                <div style="text-align: center; color: var(--error-color); padding: 1rem;">
                    <i class="fas fa-exclamation-triangle"></i>
                    Failed to load credentials
                </div>
            `;
        }
    }
}

/**
 * Test WebAuthn (for demonstration)
 */
async function testWebAuthn() {
    if (!WebAuthn.isSupported()) {
        WebAuthn.showNotSupported();
        return;
    }

    if (!WebAuthn.isSecureContext()) {
        WebAuthn.showHTTPSRequired();
        return;
    }

    try {
        const methods = await api.get(CONFIG.ENDPOINTS.MFA_METHODS);
        
        if (!methods.webAuthnCredentials || methods.webAuthnCredentials.length === 0) {
            showToast('No WebAuthn credentials found. Please register a credential first.', 'warning');
            return;
        }

        showToast('WebAuthn test - please authenticate with your device', 'info');
        
        // This would use the same flow as performWebAuthnAuth but for testing
        const challengeResponse = await api.post(CONFIG.ENDPOINTS.WEBAUTHN_CHALLENGE);
        showToast('WebAuthn challenge generated successfully!', 'success');
        
    } catch (error) {
        console.error('WebAuthn test error:', error);
        showToast(error.message || 'WebAuthn test failed', 'error');
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Initialize WebAuthn
 */
function initializeWebAuthn() {
    // Check WebAuthn support and show appropriate UI
    if (!WebAuthn.isSupported()) {
        // Hide WebAuthn-related buttons
        document.querySelectorAll('[onclick*="WebAuthn"], [onclick*="webauthn"]').forEach(btn => {
            btn.style.display = 'none';
        });
        
        // Add note about WebAuthn support
        const webauthnSections = document.querySelectorAll('#webauthn-option, [data-webauthn]');
        webauthnSections.forEach(section => {
            const note = document.createElement('div');
            note.style.cssText = 'color: var(--text-muted); font-size: 0.875rem; margin-top: 0.5rem;';
            note.innerHTML = '<i class="fas fa-info-circle"></i> WebAuthn not supported in this browser';
            section.appendChild(note);
        });
    }

    // Check HTTPS requirement
    if (FEATURES.HTTPS_REQUIRED) {
        const note = document.createElement('div');
        note.style.cssText = 'background: var(--warning-color); color: white; padding: 0.5rem 1rem; border-radius: var(--border-radius); margin-bottom: 1rem;';
        note.innerHTML = '<i class="fas fa-exclamation-triangle"></i> WebAuthn requires HTTPS. Use https:// or localhost for testing.';
        
        document.querySelectorAll('#webauthn-option, [data-webauthn]').forEach(section => {
            section.insertBefore(note.cloneNode(true), section.firstChild);
        });
    }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeWebAuthn);
} else {
    initializeWebAuthn();
}