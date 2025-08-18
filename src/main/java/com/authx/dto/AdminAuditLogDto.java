package com.authx.dto;

import com.authx.model.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAuditLogDto {
    private Long id;
    private AdminUserSummaryDto user;
    private AuditLog.EventType eventType;
    private String description;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;

    public static AdminAuditLogDto fromAuditLog(AuditLog auditLog) {
        AdminAuditLogDto dto = new AdminAuditLogDto();
        dto.setId(auditLog.getId());
        dto.setEventType(auditLog.getEventType());
        dto.setDescription(auditLog.getDescription());
        dto.setIpAddress(auditLog.getIpAddress());
        dto.setUserAgent(auditLog.getUserAgent());
        dto.setTimestamp(auditLog.getTimestamp());
        
        // Only include basic user info to avoid circular references
        if (auditLog.getUser() != null) {
            dto.setUser(AdminUserSummaryDto.fromUser(auditLog.getUser()));
        }
        
        return dto;
    }
}
