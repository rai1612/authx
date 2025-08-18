package com.authx.controller;

import com.authx.dto.AdminAuditLogDto;
import com.authx.dto.AdminRoleDto;
import com.authx.dto.AdminUserDto;
import com.authx.model.Role;
import com.authx.model.User;
import com.authx.model.UserRole;
import com.authx.service.AdminService;
import com.authx.service.AuditService;
import com.authx.service.RateLimitService;
import com.authx.service.UserService;
import com.authx.model.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin Management", description = "Administrative endpoints for user and system management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final AuditService auditService;
    private final RateLimitService rateLimitService;
    
    /**
     * Helper method to get current admin user for audit logging
     */
    private User getCurrentAdmin(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return userService.findByEmail(authentication.getName()).orElse(null);
        }
        return null;
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Retrieve paginated list of all users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<User> users = adminService.getAllUsers(pageable);
            
            // Convert to DTOs to prevent circular references
            Page<AdminUserDto> userDtos = users.map(AdminUserDto::fromUser);
            return ResponseEntity.ok(userDtos);
        } catch (Exception e) {
            log.error("Failed to get users", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve users")
            );
        }
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve detailed user information")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            User user = adminService.getUserById(userId);
            AdminUserDto userDto = AdminUserDto.fromUser(user);
            return ResponseEntity.ok(userDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get user", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve user")
            );
        }
    }

    @PostMapping("/users/{userId}/roles/{roleName}")
    @Operation(summary = "Assign role to user", description = "Assign a role to a specific user")
    public ResponseEntity<?> assignRole(@PathVariable Long userId, @PathVariable String roleName) {
        try {
            adminService.assignRoleToUser(userId, roleName);
            return ResponseEntity.ok(Map.of("message", "Role assigned successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to assign role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to assign role")
            );
        }
    }

    @DeleteMapping("/users/{userId}/roles/{roleName}")
    @Operation(summary = "Remove role from user", description = "Remove a role from a specific user")
    public ResponseEntity<?> removeRole(@PathVariable Long userId, @PathVariable String roleName) {
        try {
            adminService.removeRoleFromUser(userId, roleName);
            return ResponseEntity.ok(Map.of("message", "Role removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to remove role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to remove role")
            );
        }
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "Change user role", description = "Change user to have only one specific role")
    public ResponseEntity<?> changeUserRole(@PathVariable Long userId, @RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String newRole = request.get("role");
            if (newRole == null || newRole.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Role is required"));
            }
            
            User admin = getCurrentAdmin(authentication);
            boolean adminRemovedOwnAdminRole = adminService.changeUserRole(userId, newRole, admin.getEmail());
            
            // Log role change with admin user context
            auditService.logEvent(admin, AuditLog.EventType.ROLE_CHANGED, 
                "User ID: " + userId + " role changed to " + newRole);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Role changed successfully");
            response.put("requireLogout", adminRemovedOwnAdminRole);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to change user role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to change user role")
            );
        }
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Update user status", description = "Enable/disable user account")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");
            adminService.updateUserStatus(userId, User.UserStatus.valueOf(status));
            return ResponseEntity.ok(Map.of("message", "User status updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update user status", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to update user status")
            );
        }
    }

    @PostMapping("/users/{userId}/unlock")
    @Operation(summary = "Unlock user account", description = "Unlock a locked user account")
    public ResponseEntity<?> unlockUser(@PathVariable Long userId) {
        try {
            adminService.unlockUser(userId);
            return ResponseEntity.ok(Map.of("message", "User unlocked successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to unlock user", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to unlock user")
            );
        }
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "Delete user", description = "Permanently delete a user account")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, Authentication authentication) {
        try {
            User admin = getCurrentAdmin(authentication);
            adminService.deleteUser(userId, admin);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete user", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to delete user")
            );
        }
    }

    @GetMapping("/roles")
    @Operation(summary = "Get all roles", description = "Retrieve list of all available roles")
    public ResponseEntity<?> getAllRoles() {
        try {
            List<Role> roles = adminService.getAllRoles();
            List<AdminRoleDto> roleDtos = roles.stream()
                    .map(AdminRoleDto::fromRole)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(roleDtos);
        } catch (Exception e) {
            log.error("Failed to get roles", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve roles")
            );
        }
    }

    @GetMapping("/roles/{roleId}")
    @Operation(summary = "Get role by ID", description = "Retrieve detailed role information")
    public ResponseEntity<?> getRoleById(@PathVariable Long roleId) {
        try {
            Role role = adminService.getRoleById(roleId);
            AdminRoleDto roleDto = AdminRoleDto.fromRole(role);
            return ResponseEntity.ok(roleDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve role")
            );
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics", description = "Retrieve system usage statistics")
    public ResponseEntity<?> getSystemStats() {
        try {
            Map<String, Object> stats = adminService.getSystemStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get system stats", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve system statistics")
            );
        }
    }

    @PostMapping("/rate-limit/reset")
    @Operation(summary = "Reset rate limit", description = "Reset rate limit for a specific user")
    public ResponseEntity<?> resetRateLimit(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String userId = request.get("userId");
            String type = request.get("type");
            adminService.resetRateLimit(userId, type);
            
            // Log rate limit reset with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.RATE_LIMIT_RESET, 
                "Rate limit reset for user " + userId + ", type: " + type);
            
            return ResponseEntity.ok(Map.of("message", "Rate limit reset successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to reset rate limit", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to reset rate limit")
            );
        }
    }
    
    @PostMapping("/rate-limit/clear-all")
    @Operation(summary = "Clear all rate limits", description = "Clear all cached rate limit buckets (use after config changes)")
    public ResponseEntity<?> clearAllRateLimits(Authentication authentication) {
        try {
            rateLimitService.clearAllLimits();
            
            // Log rate limit clear with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.RATE_LIMIT_RESET, 
                "All rate limit buckets cleared by admin");
            
            return ResponseEntity.ok(Map.of("message", "All rate limits cleared successfully"));
        } catch (Exception e) {
            log.error("Failed to clear all rate limits", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to clear rate limits")
            );
        }
    }

    // Audit Log Management
    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Retrieve paginated audit logs with optional filters")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            var logs = adminService.getAuditLogs(pageable, userId, eventType, startDate, endDate);
            
            // Convert to DTOs to prevent circular references
            var logDtos = logs.map(AdminAuditLogDto::fromAuditLog);
            return ResponseEntity.ok(logDtos);
        } catch (Exception e) {
            log.error("Failed to get audit logs", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve audit logs")
            );
        }
    }

    @GetMapping("/audit-logs/events")
    @Operation(summary = "Get audit event types", description = "Retrieve list of all audit event types")
    public ResponseEntity<?> getAuditEventTypes() {
        try {
            return ResponseEntity.ok(adminService.getAuditEventTypes());
        } catch (Exception e) {
            log.error("Failed to get audit event types", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve audit event types")
            );
        }
    }

    // Role Management
    @PostMapping("/roles")
    @Operation(summary = "Create role", description = "Create a new role")
    public ResponseEntity<?> createRole(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String name = request.get("name");
            String description = request.get("description");
            var role = adminService.createRole(name, description);
            
            // Log role creation with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.ROLE_CREATED, 
                "Role created: " + name + " - " + description);
            
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to create role")
            );
        }
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Update role", description = "Update role description")
    public ResponseEntity<?> updateRole(@PathVariable Long roleId, @RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String description = request.get("description");
            var role = adminService.updateRole(roleId, description);
            
            // Log role update with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.ROLE_UPDATED, 
                "Role updated: " + role.getName() + " - " + description);
            
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to update role")
            );
        }
    }

    @DeleteMapping("/roles/{roleId}")
    @Operation(summary = "Delete role", description = "Delete a role (only if not assigned to any users)")
    public ResponseEntity<?> deleteRole(@PathVariable Long roleId, Authentication authentication) {
        try {
            // Get role name before deletion for audit
            var role = adminService.getRoleById(roleId);
            adminService.deleteRole(roleId);
            
            // Log role deletion with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.ROLE_DELETED, 
                "Role deleted: " + role.getName());
            
            return ResponseEntity.ok(Map.of("message", "Role deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete role", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to delete role")
            );
        }
    }

    // Bulk Operations
    @PostMapping("/users/bulk/status")
    @Operation(summary = "Bulk update user status", description = "Update status for multiple users")
    public ResponseEntity<?> bulkUpdateUserStatus(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            @SuppressWarnings("unchecked")
            var userIds = (List<Long>) request.get("userIds");
            String status = (String) request.get("status");
            adminService.bulkUpdateUserStatus(userIds, User.UserStatus.valueOf(status));
            
            // Log bulk status update with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.BULK_STATUS_UPDATE, 
                "Bulk status update: " + userIds.size() + " users to " + status);
            
            return ResponseEntity.ok(Map.of("message", "Users updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to bulk update users", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to update users")
            );
        }
    }

    @PostMapping("/users/bulk/unlock")
    @Operation(summary = "Bulk unlock users", description = "Unlock multiple locked user accounts")
    public ResponseEntity<?> bulkUnlockUsers(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            @SuppressWarnings("unchecked")
            var userIds = (List<Long>) request.get("userIds");
            adminService.bulkUnlockUsers(userIds);
            
            // Log bulk unlock with admin user context
            User admin = getCurrentAdmin(authentication);
            auditService.logEvent(admin, AuditLog.EventType.BULK_UNLOCK, 
                "Bulk unlock: " + userIds.size() + " users unlocked");
            
            return ResponseEntity.ok(Map.of("message", "Users unlocked successfully"));
        } catch (Exception e) {
            log.error("Failed to bulk unlock users", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to unlock users")
            );
        }
    }

    // Search Users
    @GetMapping("/users/search")
    @Operation(summary = "Search users", description = "Search users by email, username, or status")
    public ResponseEntity<?> searchUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            var users = adminService.searchUsers(pageable, email, username, status);
            
            // Convert to DTOs to prevent circular references
            var userDtos = users.map(AdminUserDto::fromUser);
            return ResponseEntity.ok(userDtos);
        } catch (Exception e) {
            log.error("Failed to search users", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to search users")
            );
        }
    }

    // System Health
    @GetMapping("/system/health")
    @Operation(summary = "Get system health", description = "Retrieve system health and performance metrics")
    public ResponseEntity<?> getSystemHealth() {
        try {
            var health = adminService.getSystemHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Failed to get system health", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to retrieve system health")
            );
        }
    }
}