package com.authx.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoleChangeRequest {
    
    @NotNull(message = "Role name is required")
    private String roleName;
    
    @NotNull(message = "User ID is required")
    private Long userId;
}
