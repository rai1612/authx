package com.authx.controller;

import com.authx.model.AuditLog;
import com.authx.model.User;
import com.authx.service.AuditService;
import com.authx.service.MfaService;
import com.authx.service.UserService;
import com.authx.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "User profile and management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserController.class);
    
    private final UserService userService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final MfaService mfaService;
    
    public UserController(UserService userService, AuditService auditService, AuditLogRepository auditLogRepository, MfaService mfaService) {
        this.userService = userService;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.mfaService = mfaService;
    }
    
    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Get current user's profile information")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Convert roles to simple DTOs to avoid circular references
            var roleDtos = user.getRoles().stream()
                .map(userRole -> new RoleDto(
                    userRole.getRole().getId(),
                    userRole.getRole().getName(),
                    userRole.getRole().getDescription(),
                    userRole.getAssignedAt()
                ))
                .toList();
            
            UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus().toString(),
                user.isMfaEnabled(),
                user.getPreferredMfaMethod().toString(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                roleDtos // Include role DTOs instead of full entities
            );
            
            return ResponseEntity.ok(profile);
            
        } catch (Exception e) {
            log.error("Failed to get user profile", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Failed to get profile")
            );
        }
    }
    
    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update current user's profile information")
    public ResponseEntity<?> updateProfile(Authentication authentication, 
                                         @RequestBody UpdateProfileRequest request) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Update the profile using the UserService
            userService.updateProfile(user.getId(), request.username(), request.email(), request.phoneNumber());
            
            // Reload user data to get updated information
            User updatedUser = userService.findByEmail(user.getEmail())
                    .orElse(user); // fallback to original user if not found
            
            // Return updated profile data
            var roleDtos = updatedUser.getRoles().stream()
                .map(userRole -> new RoleDto(
                    userRole.getRole().getId(),
                    userRole.getRole().getName(),
                    userRole.getRole().getDescription(),
                    userRole.getAssignedAt()
                ))
                .toList();
            
            UserProfileResponse profile = new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getUsername(),
                updatedUser.getEmail(),
                updatedUser.getPhoneNumber(),
                updatedUser.getStatus().toString(),
                updatedUser.isMfaEnabled(),
                updatedUser.getPreferredMfaMethod().toString(),
                updatedUser.getLastLoginAt(),
                updatedUser.getCreatedAt(),
                roleDtos
            );
            
            return ResponseEntity.ok(new ApiSuccessResponse("Profile updated successfully", profile));
            
        } catch (IllegalArgumentException e) {
            log.warn("Profile update validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                new ApiErrorResponse(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to update user profile", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Failed to update profile")
            );
        }
    }
    
    @PutMapping("/change-password")
    @Operation(summary = "Change user password", description = "Change current user's password with MFA validation if enabled")
    public ResponseEntity<?> changePassword(Authentication authentication, 
                                          @RequestBody ChangePasswordRequest request) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Verify current password
            if (!userService.verifyPassword(user, request.currentPassword())) {
                auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                    "Password change failed: Invalid current password");
                return ResponseEntity.badRequest().body(
                    new ApiErrorResponse("Current password is incorrect"));
            }
            
            // If MFA is enabled, require MFA verification
            if (user.isMfaEnabled()) {
                User.MfaMethod method = request.mfaMethod() != null ? 
                    User.MfaMethod.valueOf(request.mfaMethod()) : user.getPreferredMfaMethod();
                
                // Handle WebAuthn verification
                if (method == User.MfaMethod.WEBAUTHN) {
                    if (request.mfaCode() == null || request.mfaCode().trim().isEmpty()) {
                        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                            "Password change failed: WebAuthn verification required");
                        return ResponseEntity.badRequest().body(
                            new ApiErrorResponse("WebAuthn verification required for password change"));
                    }
                    
                    // Verify the WebAuthn response using the existing MFA service
                    try {
                        boolean webAuthnValid = mfaService.verifyMfaCode(user, request.mfaCode(), method);
                        
                        if (!webAuthnValid) {
                            auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                                "Password change failed: Invalid WebAuthn verification");
                            return ResponseEntity.badRequest().body(
                                new ApiErrorResponse("WebAuthn verification failed"));
                        }
                        
                        log.info("WebAuthn verification successful for password change for user: {}", user.getEmail());
                        
                    } catch (Exception e) {
                        log.error("WebAuthn verification error during password change for user: {}", user.getEmail(), e);
                        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                            "Password change failed: WebAuthn verification error - " + e.getMessage());
                        return ResponseEntity.badRequest().body(
                            new ApiErrorResponse("WebAuthn verification failed: " + e.getMessage()));
                    }
                } else {
                    // Handle OTP verification (Email/SMS)
                    if (request.mfaCode() == null || request.mfaCode().trim().isEmpty()) {
                        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                            "Password change failed: MFA code required");
                        return ResponseEntity.badRequest().body(
                            new ApiErrorResponse("MFA verification required for password change"));
                    }
                    
                    // Verify OTP code
                    try {
                        boolean mfaValid = mfaService.verifyMfaCode(user, request.mfaCode(), method);
                        
                        if (!mfaValid) {
                            auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                                "Password change failed: Invalid MFA code");
                            return ResponseEntity.badRequest().body(
                                new ApiErrorResponse("Invalid MFA code"));
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("MFA verification validation error during password change: {}", e.getMessage());
                        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                            "Password change failed: Invalid MFA code - " + e.getMessage());
                        return ResponseEntity.badRequest().body(
                            new ApiErrorResponse("Invalid MFA code: " + e.getMessage()));
                    } catch (Exception e) {
                        log.error("MFA verification error during password change", e);
                        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                            "Password change failed: MFA verification error - " + e.getMessage());
                        return ResponseEntity.badRequest().body(
                            new ApiErrorResponse("MFA verification failed. Please try again."));
                    }
                }
            }
            
            // Update password
            userService.updatePassword(user.getId(), request.newPassword());
            
            // CRITICAL: Log password change (SYNC)
            auditService.logEventSync(user, AuditLog.EventType.PASSWORD_CHANGE, 
                "Password changed successfully for user: " + email);
            
            return ResponseEntity.ok(new ApiSuccessResponse("Password changed successfully", null));
            
        } catch (IllegalArgumentException e) {
            log.warn("Password change validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                new ApiErrorResponse(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to change password", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Failed to change password")
            );
        }
    }
    
    @DeleteMapping("/profile")
    @Operation(summary = "Delete user profile", description = "Delete current user's profile and account")
    public ResponseEntity<?> deleteProfile(Authentication authentication) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Log user deletion before actually deleting
            auditService.logEvent(user, AuditLog.EventType.USER_DELETION, 
                "User deleted their own profile: " + email);
            
            userService.deleteUser(email);
            return ResponseEntity.ok(new ApiSuccessResponse("Profile deleted successfully", null));
            
        } catch (Exception e) {
            log.error("Failed to delete user profile", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Failed to delete profile")
            );
        }
    }
    
    @GetMapping("/audit-logs")
    @Operation(summary = "Get user audit logs", description = "Get current user's audit logs")
    public ResponseEntity<?> getAuditLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            String email = authentication.getName();
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Create pageable request
            Pageable pageable = PageRequest.of(page, size);
            
            // Get user's audit logs
            Page<AuditLog> auditLogs = auditLogRepository.findByUserIdOrderByTimestampDesc(user.getId(), pageable);
            
            // Convert to response format with DTO to prevent circular references
            var response = new java.util.HashMap<String, Object>();
            response.put("content", auditLogs.getContent().stream()
                .map(log -> new AuditLogDto(
                    log.getId(),
                    log.getEventType().name(),
                    log.getDescription(),
                    log.getTimestamp(),
                    log.getIpAddress(),
                    log.getUserAgent()
                ))
                .collect(java.util.stream.Collectors.toList()));
            response.put("totalElements", auditLogs.getTotalElements());
            response.put("totalPages", auditLogs.getTotalPages());
            response.put("number", auditLogs.getNumber());
            response.put("size", auditLogs.getSize());
            response.put("first", auditLogs.isFirst());
            response.put("last", auditLogs.isLast());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get user audit logs", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Failed to get audit logs")
            );
        }
    }
    
    // Response DTOs
    public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String status,
        boolean mfaEnabled,
        String preferredMfaMethod,
        java.time.LocalDateTime lastLoginAt,
        java.time.LocalDateTime createdAt,
        java.util.List<RoleDto> roles
    ) {}
    
    public record RoleDto(
        Long id,
        String name,
        String description,
        java.time.LocalDateTime assignedAt
    ) {}
    
    public record UpdateProfileRequest(
        String username,
        String email,
        String phoneNumber
    ) {}
    
    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword,
        String mfaCode,
        String mfaMethod
    ) {}
    
    public record AuditLogDto(
        Long id,
        String eventType,
        String description,
        java.time.LocalDateTime timestamp,
        String ipAddress,
        String userAgent
    ) {}
    
    public record ApiSuccessResponse(String message, Object data) {}
    public record ApiErrorResponse(String error) {}
}