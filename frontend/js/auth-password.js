// AuthX Password Management Module

/**
 * Show forgot password section
 */
function showForgotPassword() {
    showSection('forgot-password-section');
    
    // Setup form handler
    const form = document.getElementById('forgot-password-form');
    if (form) {
        form.removeEventListener('submit', handleForgotPassword);
        form.addEventListener('submit', handleForgotPassword);
    }
}

/**
 * Show reset password section
 */
function showResetPassword(token = null) {
    // If token is provided in URL, extract it
    if (!token) {
        const urlParams = new URLSearchParams(window.location.search);
        token = urlParams.get('token');
    }
    
    showSection('reset-password-section');
    
    if (token) {
        const tokenInput = document.getElementById('reset-token');
        if (tokenInput) {
            tokenInput.value = token;
        }
    }
    
    // Setup form handler
    const form = document.getElementById('reset-password-form');
    if (form) {
        form.removeEventListener('submit', handleResetPassword);
        form.addEventListener('submit', handleResetPassword);
    }
}

/**
 * Handle forgot password form submission
 */
async function handleForgotPassword(event) {
    event.preventDefault();
    
    const email = document.getElementById('forgot-email').value.trim();
    
    if (!email) {
        showToast('Please enter your email address', 'error');
        return;
    }
    
    try {
        showLoading();
        
        const response = await api.post(CONFIG.ENDPOINTS.FORGOT_PASSWORD, { email });
        
        showToast('If the email exists, a password reset link has been sent.', 'success');
        
        // Clear form
        document.getElementById('forgot-password-form').reset();
        
        // Show login after a delay
        setTimeout(() => {
            showLogin();
        }, 3000);
        
    } catch (error) {
        console.error('Forgot password error:', error);
        // Always show success message to prevent email enumeration
        showToast('If the email exists, a password reset link has been sent.', 'success');
        
        setTimeout(() => {
            showLogin();
        }, 3000);
    } finally {
        hideLoading();
    }
}

/**
 * Handle reset password form submission
 */
async function handleResetPassword(event) {
    event.preventDefault();
    
    const token = document.getElementById('reset-token').value;
    const newPassword = document.getElementById('new-password').value;
    const confirmPassword = document.getElementById('confirm-password').value;
    
    // Validate inputs
    if (!token) {
        showToast('Invalid reset token', 'error');
        return;
    }
    
    if (!newPassword || newPassword.length < 8) {
        showToast('Password must be at least 8 characters long', 'error');
        return;
    }
    
    if (newPassword !== confirmPassword) {
        showToast('Passwords do not match', 'error');
        return;
    }
    
    try {
        showLoading();
        
        const response = await api.post(CONFIG.ENDPOINTS.RESET_PASSWORD, {
            token: token,
            newPassword: newPassword
        });
        
        showToast('Password reset successfully! You can now log in with your new password.', 'success');
        
        // Clear form
        document.getElementById('reset-password-form').reset();
        
        // Show login after a delay
        setTimeout(() => {
            showLogin();
        }, 2000);
        
    } catch (error) {
        console.error('Reset password error:', error);
        showToast(error.message || 'Failed to reset password', 'error');
    } finally {
        hideLoading();
    }
}

/**
 * Show change password modal
 */
function showChangePasswordModal() {
    // Reset the form
    const form = document.getElementById('change-password-form');
    if (form) {
        form.reset();
    }
    
    // Show the modal
    showModal('change-password-modal');
}

/**
 * Setup change password form in dashboard
 */
function setupChangePasswordForm() {
    console.log('Setting up change password form');
    const form = document.getElementById('change-password-form');
    if (!form) {
        console.error('Change password form not found');
        return;
    }
    
    // Remove existing event listener
    form.removeEventListener('submit', handleChangePassword);
    form.addEventListener('submit', handleChangePassword);
    
    console.log('Change password form setup complete');
    
    // Note: No need for old MFA-related setup since we use modal approach
}

/**
 * Update MFA method options based on available methods
 * Note: This function is kept for potential future use but is currently unused
 * as the modal-based approach handles MFA method selection directly
 */
function updateMfaMethodOptions() {
    const mfaMethodSelect = document.getElementById('mfa-method-select');
    if (!mfaMethodSelect || !Dashboard.mfaStatus) {
        console.log('updateMfaMethodOptions: Missing select element or Dashboard.mfaStatus');
        return;
    }
    
    console.log('updateMfaMethodOptions: Updating options with mfaStatus:', {
        webAuthnCredentials: Dashboard.mfaStatus.webAuthnCredentials?.length || 0,
        emailConfigured: Dashboard.mfaStatus.emailConfigured,
        smsConfigured: Dashboard.mfaStatus.smsConfigured
    });
    
    // Enable/disable options based on availability
    const options = mfaMethodSelect.querySelectorAll('option');
    options.forEach(option => {
        const method = option.value;
        
        if (method === '') {
            // "Use preferred method" option - always available
            option.disabled = false;
            return;
        }
        
        // Check if method is available
        let isAvailable = false;
        switch (method) {
            case 'OTP_EMAIL':
                isAvailable = Dashboard.mfaStatus.emailConfigured;
                break;
            case 'OTP_SMS':
                isAvailable = Dashboard.mfaStatus.smsConfigured;
                break;
            case 'WEBAUTHN':
                isAvailable = Dashboard.mfaStatus.webAuthnCredentials && 
                             Dashboard.mfaStatus.webAuthnCredentials.length > 0;
                console.log('WebAuthn availability check:', {
                    hasCredentials: !!Dashboard.mfaStatus.webAuthnCredentials,
                    credentialCount: Dashboard.mfaStatus.webAuthnCredentials?.length || 0,
                    isAvailable: isAvailable
                });
                break;
        }
        
        option.disabled = !isAvailable;
        if (!isAvailable) {
            option.textContent = option.textContent.replace(' (Not Available)', '') + ' (Not Available)';
        } else {
            option.textContent = option.textContent.replace(' (Not Available)', '');
        }
    });
    
    // Set preferred method as default if available
    if (Dashboard.mfaStatus.preferredMethod) {
        mfaMethodSelect.value = Dashboard.mfaStatus.preferredMethod;
        handleMfaMethodChange({ target: mfaMethodSelect });
    }
}

/**
 * Handle MFA method change
 */
function handleMfaMethodChange(event) {
    const method = event.target.value;
    const otpSection = document.getElementById('otp-section');
    
    if (method === 'WEBAUTHN') {
        otpSection.style.display = 'none';
    } else {
        otpSection.style.display = 'block';
    }
}

/**
 * Send MFA code for password change
 */
async function sendMfaCodeForPasswordChange() {
    const method = document.getElementById('mfa-password-method')?.value || Dashboard.userProfile?.preferredMfaMethod;
    
    if (method === 'WEBAUTHN') {
        showToast('WebAuthn verification will be performed during password change', 'info');
        return;
    }
    
    try {
        showLoading();
        
        const response = await api.post(CONFIG.ENDPOINTS.OTP_SEND, {
            method: method === 'OTP_SMS' ? 'SMS' : 'EMAIL'
        });
        
        showToast(`Verification code sent via ${method === 'OTP_SMS' ? 'SMS' : 'Email'}`, 'success');
        
    } catch (error) {
        console.error('Error sending MFA code:', error);
        showToast(error.message || 'Failed to send verification code', 'error');
    } finally {
        hideLoading();
    }
}

/**
 * Handle change password form submission
 */
async function handleChangePassword(event) {
    event.preventDefault();
    console.log('handleChangePassword called, event prevented');
    
    const currentPassword = document.getElementById('current-password').value;
    const newPassword = document.getElementById('new-password-change').value;
    const confirmPassword = document.getElementById('confirm-new-password').value;
    
    // Validate inputs
    if (!currentPassword) {
        showToast('Please enter your current password', 'error');
        return;
    }
    
    if (!newPassword || newPassword.length < 8) {
        showToast('New password must be at least 8 characters long', 'error');
        return;
    }
    
    if (newPassword !== confirmPassword) {
        showToast('New passwords do not match', 'error');
        return;
    }
    
    // Store password data for later use in the modal
    window.passwordChangeData = {
        currentPassword: currentPassword,
        newPassword: newPassword
    };
    
    // Check if MFA is required - check both sources for reliability
    const mfaEnabledFromProfile = Dashboard.userProfile?.mfaEnabled;
    const mfaEnabledFromStatus = Dashboard.mfaStatus?.mfaEnabled;
    const mfaEnabled = mfaEnabledFromProfile || mfaEnabledFromStatus;
    
    console.log('Checking MFA status:', {
        userProfile: Dashboard.userProfile,
        mfaEnabledFromProfile: mfaEnabledFromProfile,
        mfaStatus: Dashboard.mfaStatus,
        mfaEnabledFromStatus: mfaEnabledFromStatus,
        finalMfaEnabled: mfaEnabled
    });
    
    if (mfaEnabled) {
        console.log('MFA is enabled, showing MFA verification modal');
        
        try {
            // Setup the MFA modal
            setupMfaPasswordModal();
            
            // Show the MFA verification modal
            console.log('About to show modal mfa-password-modal');
            const modalElement = document.getElementById('mfa-password-modal');
            console.log('Modal element found:', !!modalElement);
            
            if (modalElement) {
                showModal('mfa-password-modal');
                console.log('Modal show command executed, display style:', modalElement.style.display);
            } else {
                throw new Error('Modal element mfa-password-modal not found in DOM');
            }
            
            // IMPORTANT: Return here - do NOT proceed with password change
            // Password change will happen when modal form is submitted
            return;
            
        } catch (error) {
            console.error('Error setting up or showing MFA modal:', error);
            showToast('Error showing MFA verification. Please try again.', 'error');
            return; // Don't proceed on error
        }
    } else {
        console.log('MFA is disabled, proceeding directly with password change');
        
        // MFA not required, proceed directly
        await performPasswordChange({
            currentPassword: currentPassword,
            newPassword: newPassword,
            mfaCode: null,
            mfaMethod: null
        });
    }
}

/**
 * Setup the MFA password modal
 */
function setupMfaPasswordModal() {
    console.log('Setting up MFA password modal');
    
    // Check if modal elements exist
    const modal = document.getElementById('mfa-password-modal');
    const form = document.getElementById('mfa-password-form');
    const methodSelect = document.getElementById('mfa-password-method');
    const otpSection = document.getElementById('mfa-password-otp-section');
    
    console.log('Modal elements found:', {
        modal: !!modal,
        form: !!form,
        methodSelect: !!methodSelect,
        otpSection: !!otpSection
    });
    
    if (!modal || !form || !methodSelect || !otpSection) {
        console.error('Required modal elements not found:', {
            modal: modal ? 'found' : 'MISSING',
            form: form ? 'found' : 'MISSING', 
            methodSelect: methodSelect ? 'found' : 'MISSING',
            otpSection: otpSection ? 'found' : 'MISSING'
        });
        showToast('MFA modal setup failed. Please refresh the page.', 'error');
        return;
    }
    
    updateMfaPasswordMethodOptions();
    
    // Reset form
    form.reset();
    
    // Hide OTP section initially
    otpSection.style.display = 'none';
    
    // Remove existing listeners to avoid duplicates
    methodSelect.removeEventListener('change', handleMfaPasswordMethodChange);
    form.removeEventListener('submit', handleMfaPasswordSubmit);
    
    // Add new listeners
    methodSelect.addEventListener('change', handleMfaPasswordMethodChange);
    form.addEventListener('submit', handleMfaPasswordSubmit);
    
    // Set preferred method as default and configure dropdown to show only alternatives
    const preferredMethod = Dashboard.mfaStatus?.preferredMethod;
    if (preferredMethod) {
        console.log('Setting preferred method in modal:', preferredMethod);
        
        // Pre-select the preferred method
        methodSelect.value = preferredMethod;
        
        // Update dropdown to show only alternative methods
        updateMethodDropdownForPreferred(methodSelect, preferredMethod);
        
        // Show the appropriate section based on preferred method
        if (preferredMethod === 'WEBAUTHN') {
            otpSection.style.display = 'none';
        } else {
            otpSection.style.display = 'block';
        }
    } else {
        // No preferred method, show all options
        updateMfaPasswordMethodOptions();
    }
    
    console.log('MFA password modal setup complete');
}

/**
 * Update method dropdown to show preferred method first, then alternatives
 */
function updateMethodDropdownForPreferred(methodSelect, preferredMethod) {
    if (!methodSelect || !Dashboard.mfaStatus) return;
    
    const methodNames = {
        'OTP_EMAIL': 'Email OTP',
        'OTP_SMS': 'SMS OTP', 
        'WEBAUTHN': 'WebAuthn'
    };
    
    // Clear existing options
    methodSelect.innerHTML = '';
    
    // Add preferred method as first option (selected)
    const preferredOption = document.createElement('option');
    preferredOption.value = preferredMethod;
    preferredOption.textContent = `${methodNames[preferredMethod]} (Preferred)`;
    preferredOption.selected = true;
    methodSelect.appendChild(preferredOption);
    
    // Add alternative methods that are available
    const alternatives = [];
    
    // Check availability of other methods
    if (preferredMethod !== 'OTP_EMAIL' && Dashboard.mfaStatus.emailConfigured) {
        alternatives.push({ value: 'OTP_EMAIL', name: methodNames['OTP_EMAIL'] });
    }
    
    if (preferredMethod !== 'OTP_SMS' && Dashboard.mfaStatus.smsConfigured) {
        alternatives.push({ value: 'OTP_SMS', name: methodNames['OTP_SMS'] });
    }
    
    if (preferredMethod !== 'WEBAUTHN' && 
        Dashboard.mfaStatus.webAuthnCredentials && 
        Dashboard.mfaStatus.webAuthnCredentials.length > 0) {
        alternatives.push({ value: 'WEBAUTHN', name: methodNames['WEBAUTHN'] });
    }
    
    // Add alternative methods to dropdown
    alternatives.forEach(alt => {
        const option = document.createElement('option');
        option.value = alt.value;
        option.textContent = alt.name;
        methodSelect.appendChild(option);
    });
    
    console.log('Updated dropdown with preferred method:', preferredMethod, 'and alternatives:', alternatives);
}

/**
 * Update MFA method options in the password modal
 */
function updateMfaPasswordMethodOptions() {
    const methodSelect = document.getElementById('mfa-password-method');
    if (!methodSelect || !Dashboard.mfaStatus) return;
    
    const options = methodSelect.querySelectorAll('option');
    options.forEach(option => {
        const method = option.value;
        
        if (method === '') {
            option.disabled = false;
            return;
        }
        
        let isAvailable = false;
        switch (method) {
            case 'OTP_EMAIL':
                isAvailable = Dashboard.mfaStatus.emailConfigured;
                break;
            case 'OTP_SMS':
                isAvailable = Dashboard.mfaStatus.smsConfigured;
                break;
            case 'WEBAUTHN':
                isAvailable = Dashboard.mfaStatus.webAuthnCredentials && 
                             Dashboard.mfaStatus.webAuthnCredentials.length > 0;
                break;
        }
        
        option.disabled = !isAvailable;
        if (!isAvailable) {
            option.textContent = option.textContent.replace(' (Not Available)', '') + ' (Not Available)';
        } else {
            option.textContent = option.textContent.replace(' (Not Available)', '');
        }
    });
}

/**
 * Handle MFA method change in password modal
 */
function handleMfaPasswordMethodChange(event) {
    const method = event.target.value;
    const otpSection = document.getElementById('mfa-password-otp-section');
    
    if (method === 'WEBAUTHN' || method === '') {
        otpSection.style.display = 'none';
    } else {
        otpSection.style.display = 'block';
        // Clear any existing code
        document.getElementById('mfa-password-code').value = '';
    }
}

/**
 * Handle MFA password form submission
 */
async function handleMfaPasswordSubmit(event) {
    event.preventDefault();
    
    const method = document.getElementById('mfa-password-method').value;
    const code = document.getElementById('mfa-password-code').value;
    
    console.log('Form submitted with method:', method);
    
    if (!method) {
        showToast('Please select a verification method', 'error');
        return;
    }
    
    if (method !== 'WEBAUTHN' && (!code || code.trim().length === 0)) {
        showToast('Please enter the verification code', 'error');
        return;
    }
    
    try {
        let mfaCode = code;
        
        // Handle WebAuthn verification
        if (method === 'WEBAUTHN') {
            showToast('Please complete biometric verification...', 'info');
            mfaCode = await performWebAuthnForPasswordChange();
        }
        
        // Close the modal
        closeModal('mfa-password-modal');
        
        // Perform the password change
        await performPasswordChange({
            currentPassword: window.passwordChangeData.currentPassword,
            newPassword: window.passwordChangeData.newPassword,
            mfaCode: mfaCode,
            mfaMethod: method
        });
        
    } catch (error) {
        console.error('MFA verification error:', error);
        if (error.name === 'NotAllowedError') {
            showToast('WebAuthn verification was cancelled or timed out', 'error');
        } else if (error.name === 'AbortError') {
            showToast('WebAuthn verification was aborted', 'error');
        } else {
            showToast('MFA verification failed: ' + (error.message || 'Unknown error'), 'error');
        }
    }
}

/**
 * Perform the actual password change
 */
async function performPasswordChange(requestData) {
    try {
        showLoading();
        
        const response = await api.put(CONFIG.ENDPOINTS.CHANGE_PASSWORD, requestData);
        
        showToast('Password changed successfully!', 'success');
        
        // Clear form
        document.getElementById('change-password-form').reset();
        
        // Clear stored password data
        delete window.passwordChangeData;
        
        // Close the password change modal
        closeModal('change-password-modal');
        
        // Reload audit logs to show the password change activity
        if (Dashboard.loadAuditLogs) {
            await Dashboard.loadAuditLogs();
        }
        
    } catch (error) {
        console.error('Change password error details:', {
            error: error,
            message: error.message,
            status: error.status,
            stack: error.stack
        });
        
        // Handle specific error cases
        if (error.status === 401 || error.message?.includes('Unauthorized')) {
            showToast('Your session has expired. Please log in again.', 'error');
            // Clear tokens and redirect to login
            localStorage.removeItem(CONFIG.STORAGE_KEYS.ACCESS_TOKEN);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.REFRESH_TOKEN);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_INFO);
            setTimeout(() => {
                showLogin();
            }, 2000);
        } else if (error.message?.includes('MFA')) {
            showToast(error.message, 'error');
        } else {
            showToast(error.message || 'Failed to change password', 'error');
        }
    } finally {
        hideLoading();
    }
}

/**
 * Perform WebAuthn authentication specifically for password changes
 * This reuses the existing MFA infrastructure to avoid session conflicts
 */
async function performWebAuthnForPasswordChange() {
    try {
        // Get a challenge for WebAuthn verification using existing MFA endpoint
        const challengeData = await api.post('/mfa/webauthn/challenge');
        
        console.log('Received challenge data:', challengeData);
        console.log('challengeData type:', typeof challengeData);
        console.log('challengeData.challenge:', challengeData.challenge);
        console.log('challengeData.challenge type:', typeof challengeData.challenge);
        
        // Extract just the challenge string from the response
        let challengeString;
        let webAuthnOptions;
        
        if (typeof challengeData === 'string') {
            // Simple string challenge
            challengeString = challengeData;
        } else if (challengeData && typeof challengeData === 'object') {
            if (typeof challengeData.challenge === 'string') {
                // The challenge field contains a JSON string with WebAuthn options
                try {
                    webAuthnOptions = JSON.parse(challengeData.challenge);
                    challengeString = webAuthnOptions.challenge;
                    console.log('Parsed WebAuthn options:', webAuthnOptions);
                    console.log('Actual challenge UUID:', challengeString);
                } catch (parseError) {
                    // If parsing fails, treat it as a simple string
                    challengeString = challengeData.challenge;
                    console.log('Could not parse challenge as JSON, using as string');
                }
            } else if (challengeData.challenge && typeof challengeData.challenge === 'object' && challengeData.challenge.challenge) {
                // Challenge is nested as object
                webAuthnOptions = challengeData.challenge;
                challengeString = challengeData.challenge.challenge;
            } else {
                console.error('Could not extract challenge string from response:', challengeData);
                throw new Error('Invalid challenge format from server');
            }
        } else {
            console.error('Unexpected challenge data format:', challengeData);
            throw new Error('Invalid challenge response from server');
        }
        
        console.log('Extracted challenge string:', challengeString);
        console.log('Challenge type:', typeof challengeString);
        console.log('Challenge length:', challengeString?.length);
        
        // Prepare the credential request options
        // Handle the challenge format - backend sends UUID string, convert to bytes
        let challengeBytes;
        
        // Check if it looks like a UUID (contains hyphens and is 36 chars)
        if (challengeString && challengeString.includes('-') && challengeString.length === 36) {
            // Backend is sending a UUID string, convert to bytes using TextEncoder
            challengeBytes = new TextEncoder().encode(challengeString);
            console.log('UUID challenge converted to bytes, length:', challengeBytes.length);
        } else {
            // Try base64 decoding methods
            try {
                // Try standard base64 first
                challengeBytes = Uint8Array.from(atob(challengeString), c => c.charCodeAt(0));
                console.log('Standard base64 decoding successful, bytes length:', challengeBytes.length);
            } catch (e) {
                console.log('Standard base64 failed:', e.message);
                try {
                    // Try base64url decoding if standard base64 fails
                    const base64 = challengeString.replace(/-/g, '+').replace(/_/g, '/');
                    const padding = base64.length % 4;
                    const paddedBase64 = padding ? base64 + '='.repeat(4 - padding) : base64;
                    challengeBytes = Uint8Array.from(atob(paddedBase64), c => c.charCodeAt(0));
                    console.log('Base64url decoding successful, bytes length:', challengeBytes.length);
                } catch (e2) {
                    console.error('All challenge decoding methods failed:');
                    console.error('Challenge:', challengeString);
                    console.error('Standard base64 error:', e.message);
                    console.error('Base64url error:', e2.message);
                    // Last resort: convert string to bytes directly
                    challengeBytes = new TextEncoder().encode(challengeString);
                    console.log('Fallback: converted string to bytes, length:', challengeBytes.length);
                }
            }
        }
        
        const publicKeyCredentialRequestOptions = {
            challenge: challengeBytes,
            allowCredentials: Dashboard.mfaStatus.webAuthnCredentials.map(cred => {
                // Handle credential ID decoding safely
                let credentialIdBytes;
                try {
                    credentialIdBytes = Uint8Array.from(atob(cred.credentialId), c => c.charCodeAt(0));
                } catch (e) {
                    // Try base64url decoding if standard base64 fails
                    const base64 = cred.credentialId.replace(/-/g, '+').replace(/_/g, '/');
                    const padding = base64.length % 4;
                    const paddedBase64 = padding ? base64 + '='.repeat(4 - padding) : base64;
                    credentialIdBytes = Uint8Array.from(atob(paddedBase64), c => c.charCodeAt(0));
                }
                
                return {
                    id: credentialIdBytes,
                    type: 'public-key',
                    transports: ['internal', 'hybrid', 'usb', 'nfc', 'ble']
                };
            }),
            timeout: 60000,
            userVerification: 'required' // Require biometric/PIN verification
        };

        console.log('Starting WebAuthn authentication for password change...');
        
        // Request user verification (biometric/PIN)
        const credential = await navigator.credentials.get({
            publicKey: publicKeyCredentialRequestOptions
        });

        if (!credential) {
            throw new Error('WebAuthn credential verification failed');
        }

        // Prepare the response data in the format expected by existing MFA verification
        const webAuthnResponse = {
            id: credential.id,
            response: {
                authenticatorData: btoa(String.fromCharCode(...new Uint8Array(credential.response.authenticatorData))),
                clientDataJSON: btoa(String.fromCharCode(...new Uint8Array(credential.response.clientDataJSON))),
                signature: btoa(String.fromCharCode(...new Uint8Array(credential.response.signature))),
                userHandle: credential.response.userHandle ? 
                    btoa(String.fromCharCode(...new Uint8Array(credential.response.userHandle))) : null
            },
            type: credential.type
        };

        // Convert to JSON string format that MfaService.verifyWebAuthn expects
        const webAuthnResponseString = JSON.stringify(webAuthnResponse);
        
        console.log('WebAuthn verification successful for password change');
        return webAuthnResponseString;

    } catch (error) {
        console.error('WebAuthn authentication error:', error);
        throw error;
    }
}

/**
 * Public function to refresh MFA visibility (can be called from other modules)
 * Note: With modal-based MFA, this function is mainly for compatibility
 */
window.refreshPasswordFormMfaVisibility = function() {
    // The modal-based approach doesn't need real-time updates
    // But we can update the modal options if it's open
    const modal = document.getElementById('mfa-password-modal');
    if (modal && modal.style.display === 'block') {
        updateMfaPasswordMethodOptions();
    }
};

// Initialize password functionality when DOM is ready
function initializePasswordReset() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');
    
    if (token) {
        // Small delay to ensure DOM is ready and other scripts have loaded
        setTimeout(() => {
            showResetPassword(token);
        }, 100);
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializePasswordReset);
} else {
    initializePasswordReset();
}
