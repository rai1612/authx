package com.authx.dto;

import com.authx.model.Role;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRoleDto {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    public static AdminRoleDto fromRole(Role role) {
        AdminRoleDto dto = new AdminRoleDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setCreatedAt(role.getCreatedAt());
        return dto;
    }
}
