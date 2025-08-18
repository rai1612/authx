package com.authx.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getAdditionalData() { return additionalData; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;
    
    @Column(nullable = false)
    private String description;
    
    private String ipAddress;
    
    private String userAgent;
    
    @Column(columnDefinition = "TEXT")
    private String additionalData;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    public enum EventType {
        // Authentication Events
        USER_REGISTRATION, LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, LOGOUT_FAILURE,
        
        // MFA Events
        MFA_SETUP, MFA_SUCCESS, MFA_FAILURE, MFA_ENABLED, MFA_DISABLED, MFA_METHOD_UPDATED,
        MFA_OTP_SENT, MFA_OTP_SUCCESS, MFA_OTP_FAILURE, MFA_OTP_BLOCKED,
        MFA_WEBAUTHN_ERROR, MFA_WEBAUTHN_SUCCESS, MFA_WEBAUTHN_FAILURE,
        
        // Phone Verification Events
        PHONE_VERIFICATION_SENT, PHONE_VERIFICATION_SUCCESS, PHONE_VERIFICATION_FAILURE, PHONE_VERIFICATION_BLOCKED,
        
        // WebAuthn Events
        WEBAUTHN_REGISTRATION_STARTED, WEBAUTHN_REGISTRATION_SUCCESS, WEBAUTHN_REGISTRATION_FAILED,
        WEBAUTHN_AUTH_STARTED, WEBAUTHN_AUTH_SUCCESS, WEBAUTHN_AUTH_FAILED,
        WEBAUTHN_CREDENTIAL_DELETED,
        
        // Account Management Events
        PASSWORD_CHANGE, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED, PASSWORD_RESET_FAILED,
        ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
        PROFILE_UPDATED, USER_DELETION,
        
        // Admin Events
        ROLE_ASSIGNED, ROLE_REMOVED, ROLE_CHANGED, STATUS_CHANGED, ADMIN_CREATED,
        ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED,
        BULK_STATUS_UPDATE, BULK_UNLOCK, RATE_LIMIT_RESET,
        
        // Security Events
        TOKEN_REFRESH, SESSION_EXPIRED, SUSPICIOUS_ACTIVITY
    }
}