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

/**
 * Phone Number Verification Service
 * Handles phone number verification before enabling SMS OTP
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PhoneVerificationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SmsService smsService;
    private final AuditService auditService;
    private final PhoneValidationService phoneValidationService;
    
    @Value("${mfa.phone-verification.expiration:300}")
    private int verificationExpirationSeconds = 300; // 5 minutes
    
    @Value("${mfa.phone-verification.max-attempts:3}")
    private int maxVerificationAttempts = 3;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Send phone verification code
     * @param user User to verify
     * @param phoneNumber Phone number to verify
     */
    public void sendVerificationCode(User user, String phoneNumber) {
        // Validate phone number format
        PhoneValidationService.ValidationResult validation = phoneValidationService.validateWithDetails(phoneNumber);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid phone number: " + validation.getMessage());
        }
        
        String normalizedPhone = validation.getNormalizedNumber();
        String verificationCode = generateVerificationCode();
        String key = "phone_verification:" + user.getId() + ":" + normalizedPhone;
        
        // Store verification code in Redis with expiration
        redisTemplate.opsForValue().set(key, verificationCode, Duration.ofSeconds(verificationExpirationSeconds));
        
        // Reset attempt counter
        String attemptKey = "phone_verification_attempts:" + user.getId() + ":" + normalizedPhone;
        redisTemplate.delete(attemptKey);
        
        // Send verification SMS
        String message = String.format(
            "Your AuthX phone verification code is: %s\n\n" +
            "This code will expire in %d minutes. Do not share this code with anyone.\n\n" +
            "If you didn't request this verification, please ignore this message.",
            verificationCode, verificationExpirationSeconds / 60
        );
        
        smsService.sendSms(normalizedPhone, message);
        
        // Audit logging
        String maskedPhone = phoneValidationService.maskPhoneNumber(normalizedPhone);
        auditService.logEventSync(user, AuditLog.EventType.PHONE_VERIFICATION_SENT, 
            "Phone verification code sent to " + maskedPhone);
        
        log.info("Phone verification code sent to {} for user {}", maskedPhone, user.getEmail());
    }
    
    /**
     * Verify phone verification code
     * @param user User verifying
     * @param phoneNumber Phone number being verified
     * @param verificationCode Code to verify
     * @return true if verification successful
     */
    public boolean verifyCode(User user, String phoneNumber, String verificationCode) {
        // Normalize phone number
        String normalizedPhone = phoneValidationService.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return false;
        }
        
        String key = "phone_verification:" + user.getId() + ":" + normalizedPhone;
        String attemptKey = "phone_verification_attempts:" + user.getId() + ":" + normalizedPhone;
        
        // Check attempt count
        String attempts = redisTemplate.opsForValue().get(attemptKey);
        int currentAttempts = attempts != null ? Integer.parseInt(attempts) : 0;
        
        if (currentAttempts >= maxVerificationAttempts) {
            auditService.logEventSync(user, AuditLog.EventType.PHONE_VERIFICATION_BLOCKED, 
                "Phone verification blocked due to max attempts for " + phoneValidationService.maskPhoneNumber(normalizedPhone));
            return false;
        }
        
        // Get stored verification code
        String storedCode = redisTemplate.opsForValue().get(key);
        
        if (storedCode != null && storedCode.equals(verificationCode)) {
            // Verification successful
            redisTemplate.delete(key);
            redisTemplate.delete(attemptKey);
            
            // Mark phone as verified
            String verifiedKey = "phone_verified:" + user.getId() + ":" + normalizedPhone;
            redisTemplate.opsForValue().set(verifiedKey, "true", Duration.ofDays(30)); // 30 days
            
            auditService.logEventSync(user, AuditLog.EventType.PHONE_VERIFICATION_SUCCESS, 
                "Phone verification successful for " + phoneValidationService.maskPhoneNumber(normalizedPhone));
            
            log.info("Phone verification successful for {} (user: {})", 
                phoneValidationService.maskPhoneNumber(normalizedPhone), user.getEmail());
            
            return true;
        } else {
            // Verification failed - increment attempt counter
            redisTemplate.opsForValue().set(attemptKey, String.valueOf(currentAttempts + 1), 
                Duration.ofSeconds(verificationExpirationSeconds));
            
            auditService.logEventSync(user, AuditLog.EventType.PHONE_VERIFICATION_FAILURE, 
                "Phone verification failed for " + phoneValidationService.maskPhoneNumber(normalizedPhone) + 
                " (attempt " + (currentAttempts + 1) + "/" + maxVerificationAttempts + ")");
            
            return false;
        }
    }
    
    /**
     * Check if phone number is verified for user
     * @param user User to check
     * @param phoneNumber Phone number to check
     * @return true if verified
     */
    public boolean isPhoneVerified(User user, String phoneNumber) {
        String normalizedPhone = phoneValidationService.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return false;
        }
        
        String verifiedKey = "phone_verified:" + user.getId() + ":" + normalizedPhone;
        String verified = redisTemplate.opsForValue().get(verifiedKey);
        return "true".equals(verified);
    }
    
    /**
     * Check if user can enable SMS OTP
     * @param user User to check
     * @return true if user has verified phone number
     */
    public boolean canEnableSmsOtp(User user) {
        return user.getPhoneNumber() != null && 
               !user.getPhoneNumber().trim().isEmpty() &&
               isPhoneVerified(user, user.getPhoneNumber());
    }
    
    /**
     * Get verification status for user's phone
     * @param user User to check
     * @return VerificationStatus
     */
    public VerificationStatus getVerificationStatus(User user) {
        if (user.getPhoneNumber() == null || user.getPhoneNumber().trim().isEmpty()) {
            return new VerificationStatus(false, false, "No phone number configured");
        }
        
        boolean isValid = phoneValidationService.isValidPhoneNumber(user.getPhoneNumber());
        if (!isValid) {
            return new VerificationStatus(false, false, "Invalid phone number format");
        }
        
        boolean isVerified = isPhoneVerified(user, user.getPhoneNumber());
        String message = isVerified ? "Phone number verified" : "Phone number not verified";
        
        return new VerificationStatus(isValid, isVerified, message);
    }
    
    private String generateVerificationCode() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }
    
    /**
     * Verification status result
     */
    public static class VerificationStatus {
        private final boolean validFormat;
        private final boolean verified;
        private final String message;
        
        public VerificationStatus(boolean validFormat, boolean verified, String message) {
            this.validFormat = validFormat;
            this.verified = verified;
            this.message = message;
        }
        
        public boolean isValidFormat() { return validFormat; }
        public boolean isVerified() { return verified; }
        public String getMessage() { return message; }
        public boolean canEnableSms() { return validFormat && verified; }
    }
}
