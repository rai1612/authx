package com.authx.service;

import com.authx.model.AuditLog;
import com.authx.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class MfaService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaService.class);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;
    private final AuditService auditService;
    private final WebAuthnService webAuthnService;
    private final SmsService smsService;
    
    public MfaService(RedisTemplate<String, String> redisTemplate, EmailService emailService, AuditService auditService, WebAuthnService webAuthnService, SmsService smsService) {
        this.redisTemplate = redisTemplate;
        this.emailService = emailService;
        this.auditService = auditService;
        this.webAuthnService = webAuthnService;
        this.smsService = smsService;
    }
    
    @Value("${mfa.otp.expiration:300}")
    private int otpExpirationSeconds;
    
    @Value("${mfa.otp.max-attempts:3}")
    private int maxOtpAttempts;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    public void initiateMfaChallenge(User user) {
        if (user.getPreferredMfaMethod() == User.MfaMethod.OTP_EMAIL) {
            sendOtpEmail(user);
        } else if (user.getPreferredMfaMethod() == User.MfaMethod.OTP_SMS) {
            sendOtpSms(user);
        }
        // WebAuthn challenges are handled client-side
    }
    
    public void sendOtpEmail(User user) {
        String otp = generateOtp();
        String key = "otp:email:" + user.getId();
        
        // Store OTP in Redis with expiration
        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpExpirationSeconds));
        
        // Reset attempt counter
        String attemptKey = "otp:attempts:" + user.getId();
        redisTemplate.delete(attemptKey);
        
        // Send email
        emailService.sendOtpEmail(user.getEmail(), otp);
        
        auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_SENT, "OTP sent via email");
        log.info("OTP sent to email: {}", user.getEmail());
    }
    
    public void sendOtpSms(User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number not configured for user");
        }
        
        String otp = generateOtp();
        String key = "otp:sms:" + user.getId();
        
        // Store OTP in Redis with expiration
        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpExpirationSeconds));
        
        // Reset attempt counter
        String attemptKey = "otp:attempts:" + user.getId();
        redisTemplate.delete(attemptKey);
        
        // Send SMS using SMS service
        smsService.sendOtpSms(user.getPhoneNumber(), otp);
        
        // Enhanced audit logging with masked phone number
        String maskedPhone = maskPhoneNumber(user.getPhoneNumber());
        auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_SENT, 
            "SMS OTP sent to " + maskedPhone + " (expires in " + otpExpirationSeconds + "s)");
        log.info("SMS OTP sent to {} for user {}", maskedPhone, user.getEmail());
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return phoneNumber;
        }
        String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
        return "*".repeat(phoneNumber.length() - 4) + lastFour;
    }
    
    public boolean verifyOtp(User user, String providedOtp) {
        String attemptKey = "otp:attempts:" + user.getId();
        String attempts = redisTemplate.opsForValue().get(attemptKey);
        int currentAttempts = attempts != null ? Integer.parseInt(attempts) : 0;
        
        if (currentAttempts >= maxOtpAttempts) {
            auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_BLOCKED, "OTP verification blocked due to max attempts");
            return false;
        }
        
        String emailKey = "otp:email:" + user.getId();
        String smsKey = "otp:sms:" + user.getId();
        
        String storedEmailOtp = redisTemplate.opsForValue().get(emailKey);
        String storedSmsOtp = redisTemplate.opsForValue().get(smsKey);
        
        boolean isValid = (storedEmailOtp != null && storedEmailOtp.equals(providedOtp)) ||
                         (storedSmsOtp != null && storedSmsOtp.equals(providedOtp));
        
        if (isValid) {
            // Clean up OTP and attempts
            redisTemplate.delete(emailKey);
            redisTemplate.delete(smsKey);
            redisTemplate.delete(attemptKey);
            
            auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_SUCCESS, "OTP verification successful");
            return true;
        } else {
            // Increment attempt counter
            redisTemplate.opsForValue().set(attemptKey, String.valueOf(currentAttempts + 1), 
                    Duration.ofSeconds(otpExpirationSeconds));
            
            auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_FAILURE, "OTP verification failed");
            return false;
        }
    }
    
    public boolean verifyWebAuthn(User user, String webAuthnResponse) {
        try {
            return webAuthnService.finishAuthentication(user, webAuthnResponse);
        } catch (Exception e) {
            log.error("WebAuthn verification failed for user: {}", user.getEmail(), e);
            auditService.logEventSync(user, AuditLog.EventType.MFA_WEBAUTHN_ERROR, "WebAuthn verification error: " + e.getMessage());
            return false;
        }
    }
    
    public String initiateWebAuthnChallenge(User user) {
        return webAuthnService.startAuthentication(user);
    }
    
    /**
     * Verify MFA code based on the specified method
     */
    public boolean verifyMfaCode(User user, String code, User.MfaMethod method) {
        switch (method) {
            case OTP_EMAIL:
            case OTP_SMS:
                return verifyOtp(user, code);
            case WEBAUTHN:
                return verifyWebAuthn(user, code);
            default:
                log.error("Unsupported MFA method: {}", method);
                auditService.logEventSync(user, AuditLog.EventType.MFA_OTP_FAILURE, "Unsupported MFA method: " + method);
                return false;
        }
    }
    
    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }
}