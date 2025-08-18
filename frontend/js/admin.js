// AuthX Admin Panel

/**
 * Admin panel state and functionality
 */
const AdminPanel = {
    currentTab: 'dashboard',
    currentPage: 0,
    pageSize: 20,
    selectedUsers: new Set(),
    filters: {
        users: { search: '', status: '' },
        audit: { eventType: '', startDate: '', endDate: '' }
    },

    /**
     * Initialize admin panel
     */
    async init() {
        console.log('🔧 Initializing Admin Panel...');
        
        try {
            // Set up event listeners
            this.setupEventListeners();
            
            // Load initial data
            await this.loadAdminData();
            
            console.log('✅ Admin Panel initialized successfully');
        } catch (error) {
            console.error('❌ Failed to initialize Admin Panel:', error);
            showToast('Failed to initialize admin panel', 'error');
        }
    },

    /**
     * Set up event listeners
     */
    setupEventListeners() {
        // Create role form
        const createRoleForm = document.getElementById('create-role-form');
        if (createRoleForm) {
            createRoleForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.createRole();
            });
        }

        // Edit user form
        const editUserForm = document.getElementById('edit-user-form');
        if (editUserForm) {
            editUserForm.addEventListener('submit', (e) => {
                e.preventDefault();
                this.saveUserChanges();
            });
        }

        // Search debouncing
        let searchTimeout;
        const userSearch = document.getElementById('user-search');
        if (userSearch) {
            userSearch.addEventListener('input', (e) => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    this.filters.users.search = e.target.value;
                    this.loadUsers();
                }, 300);
            });
        }
    },

    /**
     * Load initial admin data
     */
    async loadAdminData() {
        await Promise.all([
            this.loadStats(),
            this.loadUsers(),
            this.loadRoles(),
            this.loadAuditEventTypes(),
            this.loadSystemHealth()
        ]);
    },

    /**
     * Load system statistics
     */
    async loadStats() {
        try {
            const response = await api.get('/admin/stats');
            if (response) {
                this.updateStatsDisplay(response);
            }
        } catch (error) {
            console.error('Failed to load stats:', error);
        }
    },

    /**
     * Update statistics display
     */
    updateStatsDisplay(stats) {
        document.getElementById('total-users').textContent = stats.totalUsers || 0;
        document.getElementById('active-users').textContent = stats.activeUsers || 0;
        document.getElementById('locked-users').textContent = stats.lockedUsers || 0;
        document.getElementById('mfa-users').textContent = stats.mfaEnabledUsers || 0;

        // Update role distribution
        const roleDistribution = document.getElementById('role-distribution');
        if (stats.roleDistribution) {
            roleDistribution.innerHTML = Object.entries(stats.roleDistribution)
                .map(([role, count]) => `
                    <div class="role-stat">
                        <span class="role-name">${role}</span>
                        <span class="role-count">${count}</span>
                    </div>
                `).join('');
        }
    },

    /**
     * Load users with pagination and filters
     */
    async loadUsers(page = 0) {
        try {
            showLoading();
            this.currentPage = page;
            
            const params = new URLSearchParams({
                page: page.toString(),
                size: this.pageSize.toString()
            });

            // Add filters
            if (this.filters.users.search) {
                params.append('email', this.filters.users.search);
            }
            if (this.filters.users.status) {
                params.append('status', this.filters.users.status);
            }

            const endpoint = this.filters.users.search || this.filters.users.status 
                ? '/admin/users/search' 
                : '/admin/users';

            const response = await api.get(`${endpoint}?${params}`);
            if (response) {
                this.updateUsersTable(response);
                this.updateUsersPagination(response);
            }
        } catch (error) {
            console.error('Failed to load users:', error);
            showToast('Failed to load users', 'error');
        } finally {
            hideLoading();
        }
    },

    /**
     * Update users table
     */
    updateUsersTable(usersData) {
        const tbody = document.getElementById('users-table-body');
        if (!tbody) return;

        tbody.innerHTML = usersData.content.map(user => `
            <tr>
                <td>
                    <input type="checkbox" class="user-checkbox" 
                           value="${user.id}" onchange="AdminPanel.toggleUserSelection(${user.id})">
                </td>
                <td>${user.id}</td>
                <td>${user.username}</td>
                <td>${user.email}</td>
                <td>
                    <span class="status-badge status-${user.status.toLowerCase()}">
                        ${user.status}
                    </span>
                </td>
                <td>
                    <span class="mfa-badge ${user.mfaEnabled ? 'enabled' : 'disabled'}">
                        ${user.mfaEnabled ? 'Enabled' : 'Disabled'}
                    </span>
                </td>
                <td>${user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleDateString() : 'Never'}</td>
                <td class="actions">
                    <button class="btn btn-sm btn-primary" onclick="AdminPanel.editUser(${user.id})" title="Edit User">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-warning" onclick="AdminPanel.unlockUser(${user.id})" title="Unlock User">
                        <i class="fas fa-unlock"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="AdminPanel.deleteUser(${user.id})" title="Delete User">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>
        `).join('');
    },

    /**
     * Update users pagination
     */
    updateUsersPagination(usersData) {
        const pagination = document.getElementById('users-pagination');
        if (!pagination) return;

        const totalPages = usersData.totalPages;
        const currentPage = usersData.number;

        let paginationHTML = '';
        
        // Previous button
        if (currentPage > 0) {
            paginationHTML += `<button class="btn btn-sm btn-secondary" onclick="AdminPanel.loadUsers(${currentPage - 1})">Previous</button>`;
        }

        // Page numbers
        for (let i = Math.max(0, currentPage - 2); i <= Math.min(totalPages - 1, currentPage + 2); i++) {
            const isActive = i === currentPage ? 'active' : '';
            paginationHTML += `<button class="btn btn-sm btn-secondary ${isActive}" onclick="AdminPanel.loadUsers(${i})">${i + 1}</button>`;
        }

        // Next button
        if (currentPage < totalPages - 1) {
            paginationHTML += `<button class="btn btn-sm btn-secondary" onclick="AdminPanel.loadUsers(${currentPage + 1})">Next</button>`;
        }

        pagination.innerHTML = paginationHTML;
    },

    /**
     * Load roles
     */
    async loadRoles() {
        try {
            const response = await api.get('/admin/roles');
            if (response) {
                this.updateRolesGrid(response);
            }
        } catch (error) {
            console.error('Failed to load roles:', error);
        }
    },

    /**
     * Update roles grid
     */
    updateRolesGrid(roles) {
        const grid = document.getElementById('roles-grid');
        if (!grid) return;

        grid.innerHTML = roles.map(role => `
            <div class="role-card">
                <div class="role-header">
                    <h4>${role.name}</h4>
                    <div class="role-actions">
                        <button class="btn btn-sm btn-primary" onclick="AdminPanel.editRole(${role.id})" title="Edit Role">
                            <i class="fas fa-edit"></i>
                        </button>
                        ${!['ADMIN', 'USER'].includes(role.name) ? `
                            <button class="btn btn-sm btn-danger" onclick="AdminPanel.deleteRole(${role.id})" title="Delete Role">
                                <i class="fas fa-trash"></i>
                            </button>
                        ` : ''}
                    </div>
                </div>
                <p class="role-description">${role.description}</p>
            </div>
        `).join('');
    },

    /**
     * Load audit event types
     */
    async loadAuditEventTypes() {
        try {
            const response = await api.get('/admin/audit-logs/events');
            if (response) {
                const select = document.getElementById('event-type-filter');
                if (select) {
                    select.innerHTML = '<option value="">All Events</option>' +
                        response.map(eventType => `<option value="${eventType}">${eventType}</option>`).join('');
                }
            }
        } catch (error) {
            console.error('Failed to load audit event types:', error);
        }
    },

    /**
     * Load audit logs
     */
    async loadAuditLogs(page = 0) {
        try {
            showLoading();
            
            const params = new URLSearchParams({
                page: page.toString(),
                size: this.pageSize.toString()
            });

            // Add filters
            if (this.filters.audit.eventType) {
                params.append('eventType', this.filters.audit.eventType);
            }
            if (this.filters.audit.startDate) {
                params.append('startDate', this.filters.audit.startDate);
            }
            if (this.filters.audit.endDate) {
                params.append('endDate', this.filters.audit.endDate);
            }

            const response = await api.get(`/admin/audit-logs?${params}`);
            if (response) {
                this.updateAuditTable(response);
                this.updateAuditPagination(response);
            }
        } catch (error) {
            console.error('Failed to load audit logs:', error);
            showToast('Failed to load audit logs', 'error');
        } finally {
            hideLoading();
        }
    },

    /**
     * Update audit logs table
     */
    updateAuditTable(auditData) {
        const tbody = document.getElementById('audit-table-body');
        if (!tbody) return;

        tbody.innerHTML = auditData.content.map(log => `
            <tr>
                <td>${new Date(log.timestamp).toLocaleString()}</td>
                <td>${log.user ? log.user.email : 'System'}</td>
                <td>
                    <span class="event-badge event-${log.eventType.toLowerCase()}">
                        ${log.eventType}
                    </span>
                </td>
                <td>${log.description}</td>
                <td>${log.ipAddress || 'N/A'}</td>
            </tr>
        `).join('');
    },

    /**
     * Update audit pagination
     */
    updateAuditPagination(auditData) {
        const pagination = document.getElementById('audit-pagination');
        if (!pagination) return;

        const totalPages = auditData.totalPages;
        const currentPage = auditData.number;

        let paginationHTML = '';
        
        if (currentPage > 0) {
            paginationHTML += `<button class="btn btn-sm btn-secondary" onclick="AdminPanel.loadAuditLogs(${currentPage - 1})">Previous</button>`;
        }

        for (let i = Math.max(0, currentPage - 2); i <= Math.min(totalPages - 1, currentPage + 2); i++) {
            const isActive = i === currentPage ? 'active' : '';
            paginationHTML += `<button class="btn btn-sm btn-secondary ${isActive}" onclick="AdminPanel.loadAuditLogs(${i})">${i + 1}</button>`;
        }

        if (currentPage < totalPages - 1) {
            paginationHTML += `<button class="btn btn-sm btn-secondary" onclick="AdminPanel.loadAuditLogs(${currentPage + 1})">Next</button>`;
        }

        pagination.innerHTML = paginationHTML;
    },

    /**
     * Load system health
     */
    async loadSystemHealth() {
        try {
            const response = await api.get('/admin/system/health');
            if (response) {
                this.updateSystemHealth(response);
            }
        } catch (error) {
            console.error('Failed to load system health:', error);
        }
    },

    /**
     * Update system health display
     */
    updateSystemHealth(health) {
        const systemHealth = document.getElementById('system-health');
        if (systemHealth) {
            systemHealth.innerHTML = `
                <div class="health-item">
                    <span class="health-label">Status:</span>
                    <span class="health-value status-${health.status.toLowerCase()}">${health.status}</span>
                </div>
                <div class="health-item">
                    <span class="health-label">Last Updated:</span>
                    <span class="health-value">${new Date(health.timestamp).toLocaleString()}</span>
                </div>
            `;
        }

        // Update individual system components
        if (health.database) {
            const dbStatus = document.getElementById('database-status');
            if (dbStatus) {
                dbStatus.innerHTML = `
                    <p>Status: <span class="status-${health.database.status.toLowerCase()}">${health.database.status}</span></p>
                    <p>User Count: ${health.database.userCount}</p>
                `;
            }
        }

        if (health.memory) {
            const memoryUsage = document.getElementById('memory-usage');
            if (memoryUsage) {
                memoryUsage.innerHTML = `
                    <p>Used: ${health.memory.used}</p>
                    <p>Free: ${health.memory.free}</p>
                    <p>Usage: ${health.memory.usagePercent}</p>
                `;
            }
        }

        if (health.recentActivity) {
            const recentActivity = document.getElementById('recent-activity');
            if (recentActivity) {
                recentActivity.innerHTML = `
                    <p>Logs (Last Hour): ${health.recentActivity.logsLastHour}</p>
                    <p>Last Activity: ${health.recentActivity.lastActivity}</p>
                `;
            }
        }
    },

    /**
     * Toggle user selection
     */
    toggleUserSelection(userId) {
        if (this.selectedUsers.has(userId)) {
            this.selectedUsers.delete(userId);
        } else {
            this.selectedUsers.add(userId);
        }
        this.updateBulkActions();
    },

    /**
     * Toggle all users selection
     */
    toggleAllUsers() {
        const checkbox = document.getElementById('select-all-checkbox');
        const userCheckboxes = document.querySelectorAll('.user-checkbox');
        
        if (checkbox.checked) {
            userCheckboxes.forEach(cb => {
                cb.checked = true;
                this.selectedUsers.add(parseInt(cb.value));
            });
        } else {
            userCheckboxes.forEach(cb => {
                cb.checked = false;
                this.selectedUsers.delete(parseInt(cb.value));
            });
        }
        
        this.updateBulkActions();
    },

    /**
     * Update bulk actions state
     */
    updateBulkActions() {
        const count = this.selectedUsers.size;
        const bulkButtons = document.querySelectorAll('.bulk-actions button');
        
        bulkButtons.forEach(btn => {
            btn.disabled = count === 0;
        });

        // Update count in modal
        const countSpan = document.getElementById('selected-user-count');
        if (countSpan) {
            countSpan.textContent = count;
        }
    },

    /**
     * Create new role
     */
    async createRole() {
        try {
            const name = document.getElementById('role-name').value;
            const description = document.getElementById('role-description').value;

            const response = await api.post('/admin/roles', { name, description });

            if (response) {
                showToast('Role created successfully', 'success');
                closeModal('create-role-modal');
                document.getElementById('create-role-form').reset();
                await this.loadRoles();
            }
        } catch (error) {
            console.error('Failed to create role:', error);
            showToast('Failed to create role', 'error');
        }
    },

    /**
     * Edit user
     */
    async editUser(userId) {
        try {
            const user = await api.get(`/admin/users/${userId}`);
            const roles = await api.get('/admin/roles');
            
            if (user && roles) {
                // Store current user data
                this.currentEditUser = user;
                
                // Populate hidden fields
                document.getElementById('edit-user-id').value = user.id;
                document.getElementById('edit-user-email').value = user.email;
                
                // Update modal title to show user being edited
                const modalTitle = document.querySelector('#edit-user-modal h3');
                modalTitle.innerHTML = `<i class="fas fa-user-edit"></i> Edit User: ${user.email}`;
                
                // Set current status in dropdown
                document.getElementById('edit-user-status').value = user.status;
                
                // Populate roles radio buttons (single role only)
                const rolesContainer = document.getElementById('edit-user-roles');
                const currentRole = user.roles && user.roles.length > 0 ? user.roles[0].name : '';
                
                rolesContainer.innerHTML = `
                    <div class="role-selection">
                        <p class="role-info"><i class="fas fa-info-circle"></i> Each user can have only one role</p>
                        ${roles.map(role => `
                            <label class="radio-label">
                                <input type="radio" name="user-role" value="${role.name}" 
                                       ${currentRole === role.name ? 'checked' : ''}>
                                <span class="role-option">
                                    <i class="fas ${role.name === 'ADMIN' ? 'fa-crown' : 'fa-user'}"></i>
                                    ${role.name}
                                </span>
                            </label>
                        `).join('')}
                    </div>
                `;
                
                showModal('edit-user-modal');
            }
        } catch (error) {
            console.error('Failed to load user data:', error);
            showToast('Failed to load user data', 'error');
        }
    },

    /**
     * Save user changes
     */
    async saveUserChanges() {
        try {
            const userId = document.getElementById('edit-user-id').value;
            const status = document.getElementById('edit-user-status').value;
            
            // Update status
            await api.put(`/admin/users/${userId}/status`, { status });

            // Get selected role from radio buttons (single role only)
            const selectedRole = document.querySelector('#edit-user-roles input[type="radio"]:checked');
            
            if (!selectedRole) {
                showToast('Please select a role for the user', 'error');
                return;
            }

            // Change user role (single role assignment)
            const response = await api.put(`/admin/users/${userId}/role`, { role: selectedRole.value });
            
            // Check if admin removed their own admin role
            if (response.requireLogout) {
                // Close modal immediately before logout process
                closeModal('edit-user-modal');
                showToast('You removed your own admin privileges. You will be logged out.', 'warning');
                setTimeout(() => {
                    logout();
                }, 2000);
                return;
            }

            showToast('User updated successfully', 'success');
            closeModal('edit-user-modal');
            await this.loadUsers(this.currentPage);
            await this.loadStats(); // Refresh stats to show updated role counts
            
            // Refresh dashboard if we're on the dashboard tab
            if (this.currentTab === 'dashboard') {
                await this.loadSystemHealth();
            }
        } catch (error) {
            console.error('Failed to update user:', error);
            
            // Check for specific error messages
            if (error.message && error.message.includes('last admin')) {
                showToast(error.message, 'warning');
            } else {
                showToast('Failed to update user', 'error');
            }
        }
    },

    /**
     * Unlock user
     */
    async unlockUser(userId) {
        try {
            await api.post(`/admin/users/${userId}/unlock`, {});
            showToast('User unlocked successfully', 'success');
            await this.loadUsers(this.currentPage);
        } catch (error) {
            console.error('Failed to unlock user:', error);
            showToast('Failed to unlock user', 'error');
        }
    },

    /**
     * Get user details for permission checks
     */
    async getUserDetails(userId) {
        try {
            return await api.get(`/admin/users/${userId}`);
        } catch (error) {
            console.error('Failed to get user details:', error);
            return null;
        }
    },

    /**
     * Delete user
     */
    async deleteUser(userId) {
        // Additional confirmation for admin users
        const user = await this.getUserDetails(userId);
        const isAdminUser = user && user.roles && user.roles.some(role => role.name === 'ADMIN');
        
        let confirmMessage = 'Are you sure you want to delete this user? This action cannot be undone.';
        if (isAdminUser) {
            confirmMessage = 'WARNING: This is an admin user! Deletion may be blocked for security reasons. Continue?';
        }
        
        if (!confirm(confirmMessage)) {
            return;
        }

        try {
            await api.delete(`/admin/users/${userId}`);
            showToast('User deleted successfully', 'success');
            await this.loadUsers(this.currentPage);
            await this.loadStats(); // Refresh dashboard stats to show updated user count
        } catch (error) {
            console.error('Failed to delete user:', error);
            
            // Show specific error message for admin deletion attempts
            if (error.message && error.message.includes('admin users')) {
                showToast('Cannot delete admin users for security reasons', 'error');
            } else {
                showToast('Failed to delete user', 'error');
            }
        }
    },

    /**
     * Bulk update user status
     */
    async bulkUpdateStatus() {
        if (this.selectedUsers.size === 0) {
            showToast('No users selected', 'warning');
            return;
        }
        showModal('bulk-status-modal');
    },

    /**
     * Confirm bulk status update
     */
    async confirmBulkStatusUpdate() {
        try {
            const status = document.getElementById('bulk-status').value;
            const userIds = Array.from(this.selectedUsers);

            await api.post('/admin/users/bulk/status', { userIds, status });

            showToast('Users updated successfully', 'success');
            closeModal('bulk-status-modal');
            this.selectedUsers.clear();
            await this.loadUsers(this.currentPage);
        } catch (error) {
            console.error('Failed to bulk update users:', error);
            showToast('Failed to update users', 'error');
        }
    },

    /**
     * Edit role
     */
    async editRole(roleId) {
        try {
            const role = await api.get(`/admin/roles/${roleId}`);
            if (role) {
                // For now, just show an alert with role info
                // You can implement a proper edit modal later
                const newDescription = prompt(`Edit description for role "${role.name}":`, role.description);
                if (newDescription !== null && newDescription !== role.description) {
                    await api.put(`/admin/roles/${roleId}`, { description: newDescription });
                    showToast('Role updated successfully', 'success');
                    await this.loadRoles();
                }
            }
        } catch (error) {
            console.error('Failed to edit role:', error);
            showToast('Failed to edit role', 'error');
        }
    },

    /**
     * Delete role
     */
    async deleteRole(roleId) {
        try {
            const role = await api.get(`/admin/roles/${roleId}`);
            if (role && confirm(`Are you sure you want to delete the role "${role.name}"? This action cannot be undone.`)) {
                await api.delete(`/admin/roles/${roleId}`);
                showToast('Role deleted successfully', 'success');
                await this.loadRoles();
            }
        } catch (error) {
            console.error('Failed to delete role:', error);
            showToast('Failed to delete role', 'error');
        }
    },

    /**
     * Quick unlock user (from edit modal)
     */
    async quickUnlockUser() {
        if (!this.currentEditUser) return;
        
        try {
            await api.post(`/admin/users/${this.currentEditUser.id}/unlock`, {});
            showToast('🔓 Account Unlocked: Failed login attempts reset to 0', 'success');
            
            // Refresh user data in modal
            await this.editUser(this.currentEditUser.id);
            // Refresh users table
            await this.loadUsers(this.currentPage);
        } catch (error) {
            console.error('Failed to unlock user:', error);
            showToast('❌ Failed to unlock account or reset failed attempts', 'error');
        }
    },

    /**
     * Reset user rate limits (from edit modal)
     */
    async resetUserRateLimit() {
        if (!this.currentEditUser) return;
        
        try {
            // Reset all rate limit types
            const rateLimitTypes = ['LOGIN', 'OTP', 'MFA', 'API'];
            const promises = rateLimitTypes.map(type => 
                api.post('/admin/rate-limit/reset', { 
                    userId: this.currentEditUser.id.toString(), 
                    type: type 
                })
            );
            
            await Promise.allSettled(promises);
            showToast('⏰ Rate Limits Reset: LOGIN, OTP, MFA & API tokens restored', 'success');
        } catch (error) {
            console.error('Failed to reset rate limits:', error);
            showToast('❌ Failed to reset rate limit tokens', 'error');
        }
    },

    /**
     * Activate user (from edit modal)
     */
    async activateUser() {
        if (!this.currentEditUser) return;
        
        try {
            // Set status to ACTIVE and unlock user
            await api.put(`/admin/users/${this.currentEditUser.id}/status`, { status: 'ACTIVE' });
            await api.post(`/admin/users/${this.currentEditUser.id}/unlock`, {});
            
            showToast('✅ User Activated: Status set to ACTIVE + Account unlocked + Attempts reset', 'success');
            
            // Refresh user data in modal
            await this.editUser(this.currentEditUser.id);
            // Refresh users table
            await this.loadUsers(this.currentPage);
        } catch (error) {
            console.error('Failed to activate user:', error);
            showToast('❌ Failed to activate user (status + unlock combination)', 'error');
        }
    },

    /**
     * Bulk unlock users
     */
    async bulkUnlockUsers() {
        if (this.selectedUsers.size === 0) {
            showToast('No users selected', 'warning');
            return;
        }

        if (!confirm(`Are you sure you want to unlock ${this.selectedUsers.size} selected users?`)) {
            return;
        }

        try {
            const userIds = Array.from(this.selectedUsers);
            await api.post('/admin/users/bulk/unlock', { userIds });

            showToast('Users unlocked successfully', 'success');
            this.selectedUsers.clear();
            await this.loadUsers(this.currentPage);
        } catch (error) {
            console.error('Failed to bulk unlock users:', error);
            showToast('Failed to unlock users', 'error');
        }
    }
};

// Make AdminPanel globally accessible for onclick handlers
window.AdminPanel = AdminPanel;

/**
 * Show admin section
 */
async function showAdmin() {
    // Verify admin access
    if (!checkAdminRole()) {
        showToast('Access denied: Admin privileges required', 'error');
        showDashboard();
        return;
    }
    
    showSection('admin-section');
    
    // Initialize admin panel if not already done
    if (!AdminPanel.initialized) {
        try {
            await AdminPanel.init();
            AdminPanel.initialized = true;
        } catch (error) {
            console.error('Failed to initialize admin panel:', error);
            showToast('Failed to initialize admin panel', 'error');
        }
    }
}

/**
 * Show admin tab
 */
function showAdminTab(tabName) {
    // Update navigation
    document.querySelectorAll('.admin-tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelector(`[onclick="showAdminTab('${tabName}')"]`).classList.add('active');
    
    // Update content
    document.querySelectorAll('.admin-tab').forEach(tab => tab.classList.remove('active'));
    document.getElementById(`admin-${tabName}-tab`).classList.add('active');
    
    AdminPanel.currentTab = tabName;
    
    // Load tab-specific data
    switch (tabName) {
        case 'dashboard':
            AdminPanel.loadStats();
            AdminPanel.loadSystemHealth();
            break;
        case 'users':
            AdminPanel.loadUsers();
            break;
        case 'roles':
            AdminPanel.loadRoles();
            break;
        case 'audit':
            AdminPanel.loadAuditLogs();
            break;
        case 'system':
            AdminPanel.loadSystemHealth();
            break;
    }
}

/**
 * Show create role modal
 */
function showCreateRoleModal() {
    showModal('create-role-modal');
}

/**
 * Filter users
 */
function filterUsers() {
    const statusFilter = document.getElementById('status-filter').value;
    AdminPanel.filters.users.status = statusFilter;
    AdminPanel.loadUsers();
}

/**
 * Search users (called by onkeyup)
 */
function searchUsers() {
    // Handled by event listener in setupEventListeners
}

/**
 * Filter audit logs
 */
function filterAuditLogs() {
    const eventType = document.getElementById('event-type-filter').value;
    const startDate = document.getElementById('start-date-filter').value;
    const endDate = document.getElementById('end-date-filter').value;
    
    AdminPanel.filters.audit.eventType = eventType;
    AdminPanel.filters.audit.startDate = startDate;
    AdminPanel.filters.audit.endDate = endDate;
    
    AdminPanel.loadAuditLogs();
}

/**
 * Clear audit filters
 */
function clearAuditFilters() {
    document.getElementById('event-type-filter').value = '';
    document.getElementById('start-date-filter').value = '';
    document.getElementById('end-date-filter').value = '';
    
    AdminPanel.filters.audit = { eventType: '', startDate: '', endDate: '' };
    AdminPanel.loadAuditLogs();
}

/**
 * Refresh system health
 */
function refreshSystemHealth() {
    AdminPanel.loadSystemHealth();
}

/**
 * Select all users
 */
function selectAllUsers() {
    document.getElementById('select-all-checkbox').checked = true;
    AdminPanel.toggleAllUsers();
}

/**
 * Toggle all users (called by checkbox onchange)
 */
function toggleAllUsers() {
    AdminPanel.toggleAllUsers();
}

/**
 * Bulk update status
 */
function bulkUpdateStatus() {
    AdminPanel.bulkUpdateStatus();
}

/**
 * Bulk unlock users
 */
function bulkUnlockUsers() {
    AdminPanel.bulkUnlockUsers();
}

/**
 * Confirm bulk status update
 */
function confirmBulkStatusUpdate() {
    AdminPanel.confirmBulkStatusUpdate();
}