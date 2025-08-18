// AuthX Dashboard Module

/**
 * Dashboard state and management
 */
const Dashboard = {
    mfaStatus: null,
    userProfile: null,
    auditLogs: [],
    
    /**
     * Initialize dashboard
     */
    async initialize() {
        try {
            await this.loadUserProfile();
            await this.loadMfaStatus();
            await loadWebAuthnCredentials(); // Call global function, not method
            await this.loadAuditLogs();
            this.setupDashboard();
        } catch (error) {
            console.error('Dashboard initialization error:', error);
            showToast('Failed to load dashboard data', 'error');
        }
    },

    /**
     * Load user profile
     */
    async loadUserProfile() {
        try {
            this.userProfile = await api.get(CONFIG.ENDPOINTS.USER_PROFILE);
            this.renderUserProfile();
        } catch (error) {
            console.error('Error loading user profile:', error);
            throw error;
        }
    },

    /**
     * Load MFA status
     */
    async loadMfaStatus() {
        try {
            this.mfaStatus = await api.get(CONFIG.ENDPOINTS.MFA_METHODS);
            this.renderMfaStatus();

        } catch (error) {
            console.error('Error loading MFA status:', error);
            // Don't throw - MFA status is not critical for dashboard
        }
    },

    /**
     * Load audit logs
     */
    async loadAuditLogs() {
        try {
            const logs = await api.get(CONFIG.ENDPOINTS.USER_AUDIT_LOGS);
            this.auditLogs = Array.isArray(logs) ? logs : logs.content || [];
            this.renderAuditLogs();
        } catch (error) {
            console.error('Error loading audit logs:', error);
            // Don't throw - audit logs are not critical
        }
    },

    /**
     * Render user profile
     */
    renderUserProfile() {
        const container = document.getElementById('user-profile');
        if (!container || !this.userProfile) return;

        container.innerHTML = `
            <!-- Profile View Mode -->
            <div id="profile-view" class="profile-view">
                <div class="profile-info-grid">
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-user"></i>
                            <span>Username</span>
                        </div>
                        <div class="profile-info-value" id="display-username">
                            ${escapeHtml(this.userProfile.username)}
                        </div>
                    </div>
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-envelope"></i>
                            <span>Email</span>
                        </div>
                        <div class="profile-info-value" id="display-email">
                            ${escapeHtml(this.userProfile.email)}
                        </div>
                    </div>
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-phone"></i>
                            <span>Phone</span>
                        </div>
                        <div class="profile-info-value" id="display-phone">
                            ${this.userProfile.phoneNumber ? escapeHtml(this.userProfile.phoneNumber) : '<span class="text-muted">Not provided</span>'}
                        </div>
                    </div>
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-calendar"></i>
                            <span>Member since</span>
                        </div>
                        <div class="profile-info-value">
                            ${formatDate(this.userProfile.createdAt)}
                        </div>
                    </div>
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-clock"></i>
                            <span>Last login</span>
                        </div>
                        <div class="profile-info-value">
                            ${this.userProfile.lastLoginAt ? formatDate(this.userProfile.lastLoginAt) : '<span class="text-muted">Never</span>'}
                        </div>
                    </div>
                    <div class="profile-info-item">
                        <div class="profile-info-label">
                            <i class="fas fa-shield-alt"></i>
                            <span>Account status</span>
                        </div>
                        <div class="profile-info-value">
                            <span class="status-badge ${this.userProfile.status === 'ACTIVE' ? 'status-enabled' : 'status-disabled'}">
                                ${this.userProfile.status}
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Profile Edit Mode -->
            <div id="profile-edit" class="profile-edit" style="display: none;">
                <form id="profile-edit-form" class="profile-edit-form">
                    <div class="form-row">
                        <div class="form-group">
                            <label for="edit-username">
                                <i class="fas fa-user"></i>
                                Username
                            </label>
                            <input type="text" id="edit-username" name="username" 
                                   value="${escapeHtml(this.userProfile.username)}" 
                                   placeholder="Enter username" required>
                            <small class="form-help">Choose a unique username</small>
                        </div>
                        <div class="form-group">
                            <label for="edit-email">
                                <i class="fas fa-envelope"></i>
                                Email
                            </label>
                            <input type="email" id="edit-email" name="email" 
                                   value="${escapeHtml(this.userProfile.email)}" 
                                   placeholder="Enter email address" required>
                            <small class="form-help">This will be your login email</small>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="edit-phone">
                            <i class="fas fa-phone"></i>
                            Phone Number
                        </label>
                        <input type="tel" id="edit-phone" name="phoneNumber" 
                               value="${this.userProfile.phoneNumber || ''}" 
                               placeholder="+1234567890">
                        <small class="form-help">Optional. Include country code (e.g., +1234567890)</small>
                    </div>
                    <div class="profile-edit-actions">
                        <button type="button" class="btn btn-secondary" onclick="cancelProfileEdit()">
                            <i class="fas fa-times"></i> Cancel
                        </button>
                        <button type="submit" class="btn btn-primary">
                            <i class="fas fa-save"></i> Save Changes
                        </button>
                    </div>
                </form>
            </div>
        `;

        // Setup form submission handler
        const form = document.getElementById('profile-edit-form');
        if (form) {
            form.addEventListener('submit', this.handleProfileUpdate.bind(this));
        }
        
        // With modal-based MFA, no need for real-time form updates
    },

    /**
     * Handle profile update form submission
     */
    async handleProfileUpdate(event) {
        event.preventDefault();
        
        const formData = new FormData(event.target);
        const updateData = {
            username: formData.get('username').trim(),
            email: formData.get('email').trim(),
            phoneNumber: formData.get('phoneNumber').trim()
        };

        try {
            showLoading();
            
            const response = await api.put(CONFIG.ENDPOINTS.USER_PROFILE, updateData);
            
            if (response.data) {
                // Update the stored profile data
                this.userProfile = response.data;
                
                // Re-render the profile view
                this.renderUserProfile();
                
                // Switch back to view mode
                exitProfileEditMode();
                
                showToast('Profile updated successfully!', 'success');
                
                // Reload audit logs to show the update activity
                await this.loadAuditLogs();
            } else {
                showToast('Profile updated successfully!', 'success');
                // Reload the profile data
                await this.loadUserProfile();
                exitProfileEditMode();
            }
            
        } catch (error) {
            console.error('Error updating profile:', error);
            showToast(error.message || 'Failed to update profile', 'error');
        } finally {
            hideLoading();
        }
    },

    /**
     * Render MFA status
     */
    renderMfaStatus() {
        const container = document.getElementById('mfa-status');
        if (!container) return;

        if (!this.mfaStatus) {
            container.innerHTML = `
                <div style="color: var(--text-muted);">
                    <i class="fas fa-exclamation-triangle"></i>
                    Unable to load MFA status
                </div>
            `;
            return;
        }

        const webAuthnCount = this.mfaStatus.webAuthnCredentials ? this.mfaStatus.webAuthnCredentials.length : 0;
        
        container.innerHTML = `
            <div class="mfa-status-grid">
                <div class="mfa-status-item">
                    <div class="mfa-status-label">
                        <i class="fas fa-shield-alt"></i> MFA Status
                    </div>
                    <div class="mfa-status-value">
                        <span class="status-badge ${this.mfaStatus.mfaEnabled ? 'status-enabled' : 'status-disabled'}">
                            ${this.mfaStatus.mfaEnabled ? 'ENABLED' : 'DISABLED'}
                        </span>
                    </div>
                </div>
                <div class="mfa-status-item mfa-preferred-clickable" onclick="handleShowPreferredMethodModal()" title="Click to change preferred method">
                    <div class="mfa-status-label">
                        <i class="fas fa-star"></i> Preferred Method
                        <i class="fas fa-edit mfa-edit-icon"></i>
                    </div>
                    <div class="mfa-status-value">
                        <span class="preferred-method-display">
                            ${this.getPreferredMethodDisplay()}
                        </span>
                    </div>
                </div>
                <div class="mfa-status-item">
                    <div class="mfa-status-label">
                        <i class="fas fa-envelope"></i> Email OTP
                    </div>
                    <div class="mfa-status-value">
                        <span class="status-badge ${this.mfaStatus.emailConfigured ? 'status-enabled' : 'status-disabled'}">
                            ${this.mfaStatus.emailConfigured ? 'AVAILABLE' : 'NOT AVAILABLE'}
                        </span>
                    </div>
                </div>
                <div class="mfa-status-item">
                    <div class="mfa-status-label">
                        <i class="fas fa-mobile-alt"></i> SMS OTP
                    </div>
                    <div class="mfa-status-value">
                        <span class="status-badge ${this.mfaStatus.smsConfigured ? 'status-enabled' : 'status-disabled'}">
                            ${this.mfaStatus.smsConfigured ? 'AVAILABLE' : 'NOT AVAILABLE'}
                        </span>
                    </div>
                </div>
                <div class="mfa-status-item">
                    <div class="mfa-status-label">
                        <i class="fas fa-fingerprint"></i> WebAuthn
                    </div>
                    <div class="mfa-status-value">
                        <span class="status-badge ${webAuthnCount > 0 ? 'status-enabled' : 'status-disabled'}">
                            ${webAuthnCount} credential${webAuthnCount !== 1 ? 's' : ''}
                        </span>
                    </div>
                </div>
            </div>
        `;

        // Update toggle button text
        const toggleBtn = document.getElementById('toggle-mfa-btn');
        if (toggleBtn) {
            toggleBtn.innerHTML = `
                <i class="fas fa-power-off"></i> ${this.mfaStatus.mfaEnabled ? 'Disable' : 'Enable'} MFA
            `;
        }
    },

    /**
     * Render audit logs
     */
    renderAuditLogs() {
        const container = document.getElementById('audit-logs');
        if (!container) return;

        if (!this.auditLogs || this.auditLogs.length === 0) {
            container.innerHTML = `
                <div style="text-align: center; color: var(--text-muted); padding: 2rem;">
                    <i class="fas fa-history" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p>No recent activity</p>
                </div>
            `;
            return;
        }

        // Show only the 10 most recent logs
        const recentLogs = this.auditLogs.slice(0, 10);
        
        container.innerHTML = `
            <div class="audit-logs-list">
                ${recentLogs.map(log => `
                    <div class="audit-log-item">
                        <div class="audit-log-icon">
                            <i class="fas fa-${this.getAuditLogIcon(log.eventType)}"></i>
                        </div>
                        <div class="audit-log-content">
                            <div class="audit-log-event">${this.formatEventType(log.eventType)}</div>
                            <div class="audit-log-details">${escapeHtml(log.details || '')}</div>
                            <div class="audit-log-meta">
                                <span><i class="fas fa-clock"></i> ${formatDate(log.timestamp)}</span>
                                <span><i class="fas fa-globe"></i> ${escapeHtml(log.ipAddress || 'Unknown')}</span>
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
            ${this.auditLogs.length > 10 ? `
                <div style="text-align: center; margin-top: 1rem;">
                    <span style="color: var(--text-muted); font-size: 0.875rem;">
                        Showing 10 of ${this.auditLogs.length} recent activities
                    </span>
                </div>
            ` : ''}
        `;
    },

    /**
     * Get icon for audit log event type
     */
    getAuditLogIcon(eventType) {
        const icons = {
            'LOGIN_SUCCESS': 'sign-in-alt',
            'LOGIN_FAILURE': 'exclamation-triangle',
            'LOGOUT': 'sign-out-alt',
            'MFA_ENABLED': 'shield-alt',
            'MFA_DISABLED': 'shield-alt',
            'WEBAUTHN_REGISTRATION_STARTED': 'fingerprint',
            'WEBAUTHN_REGISTRATION_SUCCESS': 'check-circle',
            'WEBAUTHN_REGISTRATION_FAILED': 'times-circle',
            'WEBAUTHN_AUTH_SUCCESS': 'fingerprint',
            'WEBAUTHN_AUTH_FAILED': 'times-circle',
            'WEBAUTHN_CREDENTIAL_DELETED': 'trash',
            'PASSWORD_CHANGED': 'key',
            'PROFILE_UPDATED': 'user-edit'
        };
        return icons[eventType] || 'info-circle';
    },

    /**
     * Format event type for display
     */
    formatEventType(eventType) {
        const formatted = eventType.replace(/_/g, ' ').toLowerCase();
        return formatted.charAt(0).toUpperCase() + formatted.slice(1);
    },

    /**
     * Setup dashboard interactions
     */
    setupDashboard() {
        // Setup change password form
        setupChangePasswordForm();
        
        // Add any additional dashboard-specific event listeners here
        console.log('Dashboard setup complete');
    },

    /**
     * Check if a specific MFA method is available
     */
    isMethodAvailable(method) {
        if (!this.mfaStatus) return false;

        switch (method) {
            case 'OTP_EMAIL':
                return this.mfaStatus.emailConfigured;
            case 'OTP_SMS':
                return this.mfaStatus.smsConfigured;
            case 'WEBAUTHN':
                return this.mfaStatus.webAuthnCredentials && this.mfaStatus.webAuthnCredentials.length > 0;
            default:
                return false;
        }
    },

    /**
     * Get display text and icon for preferred method
     */
    getPreferredMethodDisplay() {
        if (!this.mfaStatus || !this.mfaStatus.preferredMethod) {
            return '<i class="fas fa-question-circle"></i> Not set';
        }

        switch (this.mfaStatus.preferredMethod) {
            case 'OTP_EMAIL':
                return '<i class="fas fa-envelope"></i> Email OTP';
            case 'OTP_SMS':
                return '<i class="fas fa-sms"></i> SMS OTP';
            case 'WEBAUTHN':
                return '<i class="fas fa-fingerprint"></i> WebAuthn';
            default:
                return '<i class="fas fa-question-circle"></i> ' + this.mfaStatus.preferredMethod;
        }
    }
};

/**
 * Show Dashboard
 */
async function showDashboard() {
    showSection('dashboard-section');
    await Dashboard.initialize();
}

/**
 * Toggle MFA
 */
async function toggleMFA() {
    if (!Dashboard.mfaStatus) {
        showToast('Unable to determine MFA status', 'error');
        return;
    }

    const isEnabled = Dashboard.mfaStatus.mfaEnabled;
    const action = isEnabled ? 'disable' : 'enable';
    
    if (!confirm(`Are you sure you want to ${action} multi-factor authentication?`)) {
        return;
    }

    try {
        if (isEnabled) {
            await api.post(CONFIG.ENDPOINTS.MFA_DISABLE);
            showToast('MFA disabled successfully', 'success');
        } else {
            // For enabling MFA, we need to specify a preferred method
            const preferredMethod = Dashboard.mfaStatus.webAuthnCredentials && Dashboard.mfaStatus.webAuthnCredentials.length > 0 
                ? 'WEBAUTHN' 
                : 'OTP_EMAIL';
            
            await api.post(CONFIG.ENDPOINTS.MFA_ENABLE, { preferredMethod });
            showToast(`MFA enabled with ${preferredMethod}`, 'success');
        }
        
        // Reload MFA status, user profile, and audit logs to show the activity
        await Dashboard.loadMfaStatus();
        await Dashboard.loadUserProfile(); // This updates Dashboard.userProfile.mfaEnabled
        await Dashboard.loadAuditLogs();
        
        // With modal-based MFA, no need for form updates
        
    } catch (error) {
        console.error('Error toggling MFA:', error);
        showToast(error.message || `Failed to ${action} MFA`, 'error');
    }
}

/**
 * Test Email OTP
 */
async function testEmailOTP() {
    try {
        await api.post(CONFIG.ENDPOINTS.OTP_SEND, { method: 'EMAIL' });
        showToast('Test OTP sent to your email', 'success');
        
        // Refresh audit logs to show the OTP sent activity
        await Dashboard.loadAuditLogs();
    } catch (error) {
        console.error('Error sending test OTP:', error);
        showToast(error.message || 'Failed to send test OTP', 'error');
    }
}

/**
 * Handle showing preferred method modal (wrapper for onclick)
 */
async function handleShowPreferredMethodModal() {
    try {
        await showPreferredMethodModal();
    } catch (error) {
        console.error('Error showing preferred method modal:', error);
        showToast('Failed to load MFA status', 'error');
    }
}

/**
 * Show preferred method modal
 */
async function showPreferredMethodModal() {
    // Refresh MFA status to get latest WebAuthn credentials before showing modal
    await Dashboard.loadMfaStatus();
    
    // Update the modal radio buttons based on current status
    updateModalPreferredMethodSelection();
    showModal('preferred-method-modal');
}

/**
 * Update modal preferred method selection
 */
function updateModalPreferredMethodSelection() {
    if (!Dashboard.mfaStatus) return;

    const currentMethod = Dashboard.mfaStatus.preferredMethod;
    
    // Update radio button selection in modal
    const radioButtons = document.querySelectorAll('input[name="modal-preferred-method"]');
    radioButtons.forEach(radio => {
        radio.checked = radio.value === currentMethod;
        
        // Check if this method is available
        const isAvailable = Dashboard.isMethodAvailable(radio.value);
        radio.disabled = !isAvailable;
        
        // Update visual state
        const methodOption = radio.closest('.method-option');
        if (methodOption) {
            methodOption.title = isAvailable ? '' : 'This method needs to be set up first';
        }
    });
}

/**
 * Update preferred MFA method from modal
 */
async function updatePreferredMethodFromModal() {
    const selectedMethod = document.querySelector('input[name="modal-preferred-method"]:checked');
    
    if (!selectedMethod) {
        showToast('Please select a preferred method', 'error');
        return;
    }

    const methodValue = selectedMethod.value;
    
    // Check if method is available
    if (!Dashboard.isMethodAvailable(methodValue)) {
        showToast('Selected method is not available. Please set it up first.', 'error');
        return;
    }

    try {
        showLoading();
        
        const response = await api.put(CONFIG.ENDPOINTS.MFA_PREFERRED_METHOD, {
            preferredMethod: methodValue
        });

        showToast('Preferred MFA method updated successfully', 'success');
        
        // Close modal
        closeModal('preferred-method-modal');
        
        // Reload MFA status and audit logs to show the activity
        await Dashboard.loadMfaStatus();
        await Dashboard.loadAuditLogs();
        
        // With modal-based MFA, no need for form updates
        
    } catch (error) {
        console.error('Error updating preferred method:', error);
        showToast(error.message || 'Failed to update preferred method', 'error');
    } finally {
        hideLoading();
    }
}

// Note: loadWebAuthnCredentials is defined in webauthn.js

// Add CSS for dashboard components
function addDashboardStyles() {
    if (document.querySelector('#dashboard-styles')) return;
    
    const style = document.createElement('style');
    style.id = 'dashboard-styles';
    style.textContent = `
        /* Profile Header */
        .profile-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.5rem;
            border-bottom: 2px solid var(--border-color);
            padding-bottom: 0.5rem;
        }
        
        .profile-actions {
            display: flex;
            gap: 0.75rem;
            align-items: center;
        }
        
        .profile-header h3 {
            margin-bottom: 0;
            border-bottom: none;
            padding-bottom: 0;
        }
        
        /* Modern Profile View */
        .profile-view {
            animation: fadeIn 0.3s ease;
        }
        
        .profile-info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 1.5rem;
        }
        
        .profile-info-item {
            background: var(--background-color);
            padding: 1.5rem;
            border-radius: var(--border-radius);
            border: 1px solid var(--border-color);
            transition: all 0.3s ease;
        }
        
        .profile-info-item:hover {
            transform: translateY(-2px);
            box-shadow: var(--shadow);
        }
        
        .profile-info-label {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            margin-bottom: 0.75rem;
            font-weight: 600;
            color: var(--text-muted);
            font-size: 0.875rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .profile-info-label i {
            color: var(--primary-color);
            width: 16px;
            font-size: 1rem;
        }
        
        .profile-info-value {
            font-size: 1.125rem;
            font-weight: 500;
            color: var(--text-color);
            word-break: break-word;
        }
        
        .text-muted {
            color: var(--text-muted) !important;
            font-style: italic;
        }
        
        /* Profile Edit Mode */
        .profile-edit {
            animation: fadeIn 0.3s ease;
        }
        
        .profile-edit-form {
            background: var(--background-color);
            padding: 2rem;
            border-radius: var(--border-radius);
            border: 1px solid var(--border-color);
        }
        
        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1.5rem;
            margin-bottom: 1.5rem;
        }
        
        .profile-edit-form .form-group {
            margin-bottom: 1.5rem;
        }
        
        .profile-edit-form .form-group:last-of-type {
            margin-bottom: 2rem;
        }
        
        .profile-edit-form label {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            margin-bottom: 0.75rem;
            font-weight: 600;
            color: var(--text-color);
        }
        
        .profile-edit-form label i {
            color: var(--primary-color);
            width: 16px;
        }
        
        .profile-edit-form input {
            width: 100%;
            padding: 1rem;
            border: 2px solid var(--border-color);
            border-radius: var(--border-radius);
            font-size: 1rem;
            transition: all 0.3s ease;
            background-color: var(--card-background);
            color: var(--text-color);
        }
        
        .profile-edit-form input:focus {
            outline: none;
            border-color: var(--primary-color);
            box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
        }
        
        .profile-edit-form .form-help {
            margin-top: 0.5rem;
            font-size: 0.875rem;
            color: var(--text-muted);
            display: block;
        }
        
        .profile-edit-actions {
            display: flex;
            gap: 1rem;
            justify-content: flex-end;
            margin-top: 2rem;
            padding-top: 1.5rem;
            border-top: 1px solid var(--border-color);
        }
        
        /* Legacy profile styles for backward compatibility */
        .profile-info {
            display: grid;
            gap: 1rem;
        }
        
        .profile-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.75rem 0;
            border-bottom: 1px solid var(--border-color);
        }
        
        .profile-item:last-child {
            border-bottom: none;
        }
        
        .profile-item strong {
            color: var(--text-color);
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        
        .profile-item strong i {
            color: var(--primary-color);
            width: 16px;
        }
        
        /* Change Password Form Styles */
        .change-password-form {
            background: var(--background-color);
            padding: 2rem;
            border-radius: var(--border-radius);
            border: 1px solid var(--border-color);
        }
        
        .change-password-form .form-group {
            margin-bottom: 1.5rem;
        }
        
        .change-password-form label {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            margin-bottom: 0.75rem;
            font-weight: 600;
            color: var(--text-color);
        }
        
        .change-password-form label i {
            color: var(--primary-color);
            width: 16px;
        }
        
        .change-password-form input,
        .change-password-form select {
            width: 100%;
            padding: 1rem;
            border: 2px solid var(--border-color);
            border-radius: var(--border-radius);
            font-size: 1rem;
            transition: all 0.3s ease;
            background-color: var(--card-background);
            color: var(--text-color);
        }
        
        .change-password-form input:focus,
        .change-password-form select:focus {
            outline: none;
            border-color: var(--primary-color);
            box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
        }
        
        .mfa-verification-section {
            background: rgba(59, 130, 246, 0.05);
            border: 1px solid rgba(59, 130, 246, 0.2);
            border-radius: var(--border-radius);
            padding: 1.5rem;
            margin: 1.5rem 0;
        }
        
        .mfa-verification-section h4 {
            margin: 0 0 0.5rem 0;
            color: var(--primary-color);
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        
        .mfa-verification-section p {
            margin: 0 0 1rem 0;
            color: var(--text-muted);
            font-size: 0.9rem;
        }
        
        .otp-section {
            display: flex;
            gap: 1rem;
            align-items: end;
            flex-wrap: wrap;
        }
        
        .otp-section .form-group {
            flex: 1;
            min-width: 200px;
        }
        
        .change-password-actions {
            display: flex;
            justify-content: flex-end;
            margin-top: 2rem;
            padding-top: 1.5rem;
            border-top: 1px solid var(--border-color);
        }
        
        /* Animations */
        @keyframes fadeIn {
            from {
                opacity: 0;
                transform: translateY(10px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        
        .mfa-status-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
        }
        
        .mfa-status-item {
            padding: 1rem;
            background: var(--background-color);
            border-radius: var(--border-radius);
            border: 1px solid var(--border-color);
        }
        
        .mfa-status-label {
            font-size: 0.875rem;
            color: var(--text-muted);
            margin-bottom: 0.5rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        
        .mfa-status-value {
            font-weight: 600;
            color: var(--text-color);
        }
        
        .audit-logs-list {
            max-height: 400px;
            overflow-y: auto;
        }
        
        .audit-log-item {
            display: flex;
            gap: 1rem;
            padding: 1rem;
            border-bottom: 1px solid var(--border-color);
        }
        
        .audit-log-item:last-child {
            border-bottom: none;
        }
        
        .audit-log-icon {
            flex-shrink: 0;
            width: 40px;
            height: 40px;
            background: var(--background-color);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--primary-color);
        }
        
        .audit-log-content {
            flex: 1;
        }
        
        .audit-log-event {
            font-weight: 600;
            color: var(--text-color);
            margin-bottom: 0.25rem;
        }
        
        .audit-log-details {
            color: var(--text-muted);
            font-size: 0.875rem;
            margin-bottom: 0.5rem;
        }
        
        .audit-log-meta {
            display: flex;
            gap: 1rem;
            font-size: 0.75rem;
            color: var(--text-muted);
        }
        
        .audit-log-meta span {
            display: flex;
            align-items: center;
            gap: 0.25rem;
        }
        
        @media (max-width: 768px) {
            .profile-info-grid {
                grid-template-columns: 1fr;
            }
            
            .form-row {
                grid-template-columns: 1fr;
                gap: 1rem;
            }
            
            .profile-edit-actions {
                flex-direction: column;
                gap: 0.75rem;
            }
            
            .profile-edit-actions .btn {
                width: 100%;
                justify-content: center;
            }
            
            .change-password-actions {
                flex-direction: column;
                gap: 0.75rem;
            }
            
            .change-password-actions .btn {
                width: 100%;
                justify-content: center;
            }
            
            .otp-section {
                flex-direction: column;
                align-items: stretch;
            }
            
            .mfa-status-grid {
                grid-template-columns: 1fr;
            }
            
            .audit-log-meta {
                flex-direction: column;
                gap: 0.25rem;
            }
        }

        /* Password management styles */
        .password-actions {
            text-align: center;
            padding: 1rem 0;
        }
        
        .password-actions .btn {
            margin-bottom: 0.5rem;
        }
        
        .password-actions .form-help {
            display: block;
            margin-top: 0.5rem;
            color: var(--text-muted);
            font-size: 0.875rem;
        }

        /* Modal layering - ensure MFA modal appears above password modal */
        #change-password-modal {
            z-index: 1000;
        }
        
        #mfa-password-modal {
            z-index: 1001;
        }
        
        /* Responsive profile actions */
        @media (max-width: 768px) {
            .profile-actions {
                flex-direction: column;
                gap: 0.5rem;
                align-items: stretch;
            }
            
            .profile-actions .btn {
                width: 100%;
                text-align: center;
            }
        }
    `;
    document.head.appendChild(style);
}

// Initialize dashboard styles when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', addDashboardStyles);
} else {
    addDashboardStyles();
}

/**
 * Toggle between profile view and edit modes
 */
function toggleProfileEdit() {
    const viewMode = document.getElementById('profile-view');
    const editMode = document.getElementById('profile-edit');
    const editBtn = document.getElementById('edit-profile-btn');
    
    if (!viewMode || !editMode || !editBtn) return;
    
    if (viewMode.style.display === 'none') {
        // Switch to view mode
        exitProfileEditMode();
    } else {
        // Switch to edit mode
        enterProfileEditMode();
    }
}

/**
 * Enter profile edit mode
 */
function enterProfileEditMode() {
    const viewMode = document.getElementById('profile-view');
    const editMode = document.getElementById('profile-edit');
    const editBtn = document.getElementById('edit-profile-btn');
    
    if (viewMode && editMode && editBtn) {
        viewMode.style.display = 'none';
        editMode.style.display = 'block';
        editBtn.innerHTML = '<i class="fas fa-times"></i> Cancel';
    }
}

/**
 * Exit profile edit mode
 */
function exitProfileEditMode() {
    const viewMode = document.getElementById('profile-view');
    const editMode = document.getElementById('profile-edit');
    const editBtn = document.getElementById('edit-profile-btn');
    
    if (viewMode && editMode && editBtn) {
        viewMode.style.display = 'block';
        editMode.style.display = 'none';
        editBtn.innerHTML = '<i class="fas fa-edit"></i> Edit Profile';
    }
}

/**
 * Cancel profile edit (same as exit but with confirmation if changes exist)
 */
function cancelProfileEdit() {
    // You could add change detection here if needed
    exitProfileEditMode();
}