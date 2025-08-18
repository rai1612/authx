package com.authx.dto;

import com.authx.model.User;
import lombok.Data;

@Data
public class AdminUserSummaryDto {
    private Long id;
    private String username;
    private String email;

    public static AdminUserSummaryDto fromUser(User user) {
        AdminUserSummaryDto dto = new AdminUserSummaryDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
