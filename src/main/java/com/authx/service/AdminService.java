package com.authx.service;

import com.authx.model.AuditLog;
import com.authx.model.Role;
import com.authx.model.User;
import com.authx.model.UserRole;
import com.authx.repository.AuditLogRepository;
import com.authx.repository.RoleRepository;
import com.authx.repository.UserRepository;
import com.authx.repository.UserRoleRepository;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final RateLimitService rateLimitService;

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public void assignRoleToUser(Long userId, String roleName) {
        User user = getUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        // Check if user already has this role
        boolean hasRole = user.getRoles().stream()
                .anyMatch(userRole -> userRole.getRole().getName().equals(roleName));

        if (hasRole) {
            throw new IllegalArgumentException("User already has role: " + roleName);
        }

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());

        user.getRoles().add(userRole);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.ROLE_ASSIGNED, "Role " + roleName + " assigned to user");
        log.info("Role {} assigned to user {}", roleName, user.getEmail());
    }

    @Transactional
    public void removeRoleFromUser(Long userId, String roleName) {
        User user = getUserById(userId);
        
        UserRole userRole = user.getRoles().stream()
                .filter(role -> role.getRole().getName().equals(roleName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User does not have role: " + roleName));

        user.getRoles().remove(userRole);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.ROLE_REMOVED, "Role " + roleName + " removed from user");
        log.info("Role {} removed from user {}", roleName, user.getEmail());
    }

    @Transactional
    public boolean changeUserRole(Long userId, String newRoleName, String currentAdminEmail) {
        User user = getUserById(userId);
        Role newRole = roleRepository.findByName(newRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + newRoleName));

        // Check if admin is removing their own admin role
        boolean adminRemovingOwnAdminRole = user.getEmail().equals(currentAdminEmail) && 
                                           user.getRoles().stream().anyMatch(ur -> "ADMIN".equals(ur.getRole().getName())) &&
                                           !"ADMIN".equals(newRoleName);

        // Prevent last admin from demoting themselves
        if (adminRemovingOwnAdminRole) {
            long adminCount = countActiveAdmins();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot remove admin role from the last admin. Please assign admin role to another user first.");
            }
        }

        // Clear all existing roles
        Set<UserRole> currentRoles = new HashSet<>(user.getRoles());
        for (UserRole existingRole : currentRoles) {
            user.getRoles().remove(existingRole);
            userRoleRepository.delete(existingRole);
        }

        // Assign new role
        UserRole newUserRole = new UserRole();
        newUserRole.setUser(user);
        newUserRole.setRole(newRole);
        newUserRole.setAssignedAt(LocalDateTime.now());

        user.getRoles().add(newUserRole);
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.ROLE_CHANGED, 
                "Role changed to " + newRoleName + " (single role assignment)");
        log.info("User {} role changed to {}", user.getEmail(), newRoleName);

        // Return true if admin removed their own admin role (for logout trigger)
        return adminRemovingOwnAdminRole;
    }

    @Transactional
    public void updateUserStatus(Long userId, User.UserStatus status) {
        User user = getUserById(userId);
        User.UserStatus oldStatus = user.getStatus();
        
        user.setStatus(status);
        
        // If setting to LOCKED, also set the lockedUntil timestamp to make it permanent
        // If setting to ACTIVE, clear the lockedUntil timestamp and reset failed attempts
        if (status == User.UserStatus.LOCKED) {
            // Set locked until far future (effectively permanent until admin unlocks)
            user.setLockedUntil(LocalDateTime.now().plusYears(100));
        } else if (status == User.UserStatus.ACTIVE) {
            // Clear lock timestamp and reset failed attempts when activating
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.STATUS_CHANGED, 
                "User status changed from " + oldStatus + " to " + status);
        log.info("User {} status changed from {} to {}", user.getEmail(), oldStatus, status);
    }

    @Transactional
    public void unlockUser(Long userId) {
        User user = getUserById(userId);
        
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.setStatus(User.UserStatus.ACTIVE); // Ensure status is also set to ACTIVE
        userRepository.save(user);

        auditService.logEvent(user, AuditLog.EventType.ACCOUNT_UNLOCKED, "Account unlocked by admin");
        log.info("User {} unlocked by admin", user.getEmail());
    }

    @Transactional
    public void deleteUser(Long userId, User admin) {
        User user = getUserById(userId);
        String email = user.getEmail();
        
        // Prevent deletion of admin users by other admins (safety check)
        boolean isAdminUser = user.getRoles().stream()
                .anyMatch(userRole -> "ADMIN".equals(userRole.getRole().getName()));
        
        if (isAdminUser) {
            throw new IllegalArgumentException("Cannot delete admin users for security reasons");
        }
        
        // Log deletion with admin user context for proper audit trail
        auditService.logEvent(admin, AuditLog.EventType.USER_DELETION, "User deleted: " + email + " (ID: " + userId + ")");
        userRepository.delete(user);
        
        log.info("User {} deleted by admin {}", email, admin.getEmail());
    }

    @Transactional
    public void deleteUser(Long userId) {
        // Backward compatibility method - logs deletion without admin context
        User user = getUserById(userId);
        String email = user.getEmail();
        
        // Prevent deletion of admin users by other admins (safety check)
        boolean isAdminUser = user.getRoles().stream()
                .anyMatch(userRole -> "ADMIN".equals(userRole.getRole().getName()));
        
        if (isAdminUser) {
            throw new IllegalArgumentException("Cannot delete admin users for security reasons");
        }
        
        auditService.logEvent(user, AuditLog.EventType.USER_DELETION, "User deleted by admin");
        userRepository.delete(user);
        
        log.info("User {} deleted by admin", email);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
    
    public Role getRoleById(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
    }

    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // User statistics
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long lockedUsers = userRepository.countByLockedUntilAfter(LocalDateTime.now());
        long mfaEnabledUsers = userRepository.countByMfaEnabled(true);
        
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("lockedUsers", lockedUsers);
        stats.put("mfaEnabledUsers", mfaEnabledUsers);
        stats.put("mfaAdoptionRate", totalUsers > 0 ? (double) mfaEnabledUsers / totalUsers * 100 : 0);
        
        // Role statistics
        List<Role> roles = getAllRoles();
        Map<String, Long> roleStats = new HashMap<>();
        for (Role role : roles) {
            long count = userRoleRepository.countByRole(role);
            roleStats.put(role.getName(), count);
        }
        stats.put("roleDistribution", roleStats);
        
        // System information
        stats.put("timestamp", LocalDateTime.now());
        stats.put("totalRoles", roles.size());
        
        return stats;
    }

    public void resetRateLimit(String userId, String type) {
        try {
            RateLimitService.RateLimitType rateLimitType = RateLimitService.RateLimitType.valueOf(type.toUpperCase());
            rateLimitService.resetLimit(userId, rateLimitType);
            log.info("Rate limit {} reset for user {}", type, userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rate limit type: " + type);
        }
    }

    @Transactional
    public User createAdminUser(String email, String username, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Admin user already exists");
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setUsername(username);
        admin.setPasswordHash(password); // Should be encoded
        admin.setStatus(User.UserStatus.ACTIVE);
        admin.setMfaEnabled(false);

        User savedAdmin = userRepository.save(admin);

        // Assign ADMIN role
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("ADMIN");
                    newRole.setDescription("Administrator role with full system access");
                    return roleRepository.save(newRole);
                });

        UserRole userRole = new UserRole();
        userRole.setUser(savedAdmin);
        userRole.setRole(adminRole);
        userRole.setAssignedAt(LocalDateTime.now());

        savedAdmin.getRoles().add(userRole);
        userRepository.save(savedAdmin);

        auditService.logEvent(savedAdmin, AuditLog.EventType.ADMIN_CREATED, "Admin user created");
        log.info("Admin user created: {}", email);

        return savedAdmin;
    }

    // Audit Log Management
    public Page<AuditLog> getAuditLogs(Pageable pageable, Long userId, String eventType, String startDate, String endDate) {
        if (userId != null) {
            return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }
        
        if (eventType != null) {
            AuditLog.EventType type = AuditLog.EventType.valueOf(eventType);
            return auditLogRepository.findByEventTypeOrderByTimestampDesc(type, pageable);
        }
        
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
        }
        
        return auditLogRepository.findAll(pageable);
    }

    public List<String> getAuditEventTypes() {
        return Arrays.stream(AuditLog.EventType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    // Role Management
    @Transactional
    public Role createRole(String name, String description) {
        if (roleRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("Role already exists: " + name);
        }
        
        Role role = new Role();
        role.setName(name.toUpperCase());
        role.setDescription(description);
        
        Role savedRole = roleRepository.save(role);
        log.info("Role created: {}", name);
        
        return savedRole;
    }

    @Transactional
    public Role updateRole(Long roleId, String description) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        
        role.setDescription(description);
        Role savedRole = roleRepository.save(role);
        
        log.info("Role updated: {}", role.getName());
        return savedRole;
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        
        // Check if role is assigned to any users
        long userCount = userRoleRepository.countByRole(role);
        if (userCount > 0) {
            throw new IllegalArgumentException("Cannot delete role that is assigned to users");
        }
        
        // Prevent deletion of system roles
        if ("ADMIN".equals(role.getName()) || "USER".equals(role.getName())) {
            throw new IllegalArgumentException("Cannot delete system role: " + role.getName());
        }
        
        String roleName = role.getName();
        roleRepository.delete(role);
        log.info("Role deleted: {}", roleName);
    }

    // Bulk Operations
    @Transactional
    public void bulkUpdateUserStatus(List<Long> userIds, User.UserStatus status) {
        for (Long userId : userIds) {
            try {
                updateUserStatus(userId, status);
            } catch (Exception e) {
                log.error("Failed to update status for user {}: {}", userId, e.getMessage());
            }
        }
    }

    @Transactional
    public void bulkUnlockUsers(List<Long> userIds) {
        for (Long userId : userIds) {
            try {
                unlockUser(userId);
            } catch (Exception e) {
                log.error("Failed to unlock user {}: {}", userId, e.getMessage());
            }
        }
    }

    // Search Users
    public Page<User> searchUsers(Pageable pageable, String email, String username, String status) {
        if (email != null && !email.trim().isEmpty()) {
            return userRepository.findByEmailContainingIgnoreCase(email.trim(), pageable);
        }
        
        if (username != null && !username.trim().isEmpty()) {
            return userRepository.findByUsernameContainingIgnoreCase(username.trim(), pageable);
        }
        
        if (status != null && !status.trim().isEmpty()) {
            User.UserStatus userStatus = User.UserStatus.valueOf(status.toUpperCase());
            return userRepository.findByStatus(userStatus, pageable);
        }
        
        return userRepository.findAll(pageable);
    }

    // System Health
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Database health
            long userCount = userRepository.count();
            health.put("database", Map.of(
                "status", "UP",
                "userCount", userCount
            ));
            
            // Recent activity
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            LocalDateTime now = LocalDateTime.now();
            
            Page<AuditLog> recentLogs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(
                oneHourAgo, now, 
                org.springframework.data.domain.PageRequest.of(0, 10)
            );
            
            health.put("recentActivity", Map.of(
                "logsLastHour", recentLogs.getTotalElements(),
                "lastActivity", recentLogs.hasContent() ? 
                    recentLogs.getContent().get(0).getTimestamp() : "No recent activity"
            ));
            
            // Memory usage (basic JVM info)
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            health.put("memory", Map.of(
                "used", usedMemory / (1024 * 1024) + " MB",
                "free", freeMemory / (1024 * 1024) + " MB",
                "total", totalMemory / (1024 * 1024) + " MB",
                "max", maxMemory / (1024 * 1024) + " MB",
                "usagePercent", String.format("%.2f%%", (double) usedMemory / totalMemory * 100)
            ));
            
            health.put("timestamp", LocalDateTime.now());
            health.put("status", "UP");
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            log.error("Health check failed", e);
        }
        
        return health;
    }

    // Security Validation Methods
    
    /**
     * Validate admin operation permissions
     */
    public void validateAdminOperation(String operation, Long targetUserId) {
        log.info("Admin operation requested: {} for user {}", operation, targetUserId);
        
        // Additional validation can be added here
        // For example: rate limiting admin operations, 
        // preventing operations on certain users, etc.
    }
    
    /**
     * Check if user can be modified by admin
     */
    public boolean canModifyUser(Long userId) {
        try {
            User user = getUserById(userId);
            
            // Prevent modification of certain system users if needed
            if ("admin@authx.local".equals(user.getEmail())) {
                log.warn("Attempt to modify default admin user blocked");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error checking user modification permissions", e);
            return false;
        }
    }
    
    /**
     * Validate role assignment permissions
     */
    public void validateRoleAssignment(Long userId, String roleName) {
        // Prevent assigning ADMIN role to regular users without additional verification
        if ("ADMIN".equals(roleName)) {
            log.warn("Admin role assignment requested for user {}", userId);
            // Additional verification could be added here
        }
    }

    /**
     * Count active admin users in the system
     */
    private long countActiveAdmins() {
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalArgumentException("ADMIN role not found"));
        
        return userRoleRepository.findByRole(adminRole).stream()
                .map(UserRole::getUser)
                .filter(user -> user.getStatus() == User.UserStatus.ACTIVE)
                .count();
    }
}