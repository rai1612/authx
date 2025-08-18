package com.authx.service;

import com.authx.model.AuditLog;
import com.authx.model.User;
import com.authx.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;

@Service
public class AuditService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);
    
    private final AuditLogRepository auditLogRepository;
    
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    @Async
    public void logEvent(User user, String eventType, String description) {
        logEvent(user, AuditLog.EventType.valueOf(eventType), description, null);
    }
    
    @Async
    public void logEvent(User user, AuditLog.EventType eventType, String description) {
        logEvent(user, eventType, description, null);
    }
    
    @Async
    public void logEvent(User user, AuditLog.EventType eventType, String description, String additionalData) {
        try {
            AuditLog auditLog = new AuditLog();
            
            // Only set user if it exists and has an ID
            if (user != null && user.getId() != null) {
                auditLog.setUser(user);
            }
            
            auditLog.setEventType(eventType);
            auditLog.setDescription(description);
            auditLog.setAdditionalData(additionalData);
            auditLog.setTimestamp(LocalDateTime.now()); // Ensure timestamp is set
            
            // Extract request information if available
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            auditLogRepository.save(auditLog);
            String userEmail = (user != null) ? user.getEmail() : "anonymous";
            log.info("Audit event logged: {} for user: {}", eventType, userEmail);
            
        } catch (Exception e) {
            log.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Synchronous audit logging for critical events
     * Use this for events that MUST be logged immediately
     */
    public void logEventSync(User user, AuditLog.EventType eventType, String description) {
        try {
            AuditLog auditLog = new AuditLog();
            
            // Only set user if it exists and has an ID
            if (user != null && user.getId() != null) {
                auditLog.setUser(user);
            }
            
            auditLog.setEventType(eventType);
            auditLog.setDescription(description);
            auditLog.setTimestamp(LocalDateTime.now());
            
            // Extract request information if available
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            auditLogRepository.save(auditLog);
            String userEmail = (user != null) ? user.getEmail() : "anonymous";
            log.info("SYNC Audit event logged: {} for user: {}", eventType, userEmail);
            
        } catch (Exception e) {
            log.error("Failed to log SYNC audit event: {}", e.getMessage(), e);
        }
    }
    
    @Async
    public void logSecurityEvent(String eventType, String description, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(AuditLog.EventType.valueOf(eventType));
            auditLog.setDescription(description);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setTimestamp(LocalDateTime.now()); // Ensure timestamp is set
            
            auditLogRepository.save(auditLog);
            log.info("Security event logged: {}", eventType);
            
        } catch (Exception e) {
            log.error("Failed to log security event: {}", e.getMessage(), e);
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}