package com.authx.dto;

import com.authx.model.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class AdminUserDto {
    private Long id;
    private String username;
    private String email;
    private String phoneNumber;
    private User.UserStatus status;
    private boolean mfaEnabled;
    private User.MfaMethod preferredMfaMethod;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdminRoleDto> roles;

    public static AdminUserDto fromUser(User user) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setStatus(user.getStatus());
        dto.setMfaEnabled(user.isMfaEnabled());
        dto.setPreferredMfaMethod(user.getPreferredMfaMethod());
        dto.setFailedLoginAttempts(user.getFailedLoginAttempts());
        dto.setLockedUntil(user.getLockedUntil());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        
        // Convert roles to DTOs
        dto.setRoles(user.getRoles().stream()
                .map(userRole -> AdminRoleDto.fromRole(userRole.getRole()))
                .collect(Collectors.toList()));
        
        return dto;
    }
}
