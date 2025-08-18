package com.authx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Phone Number Validation Service
 * Handles phone number normalization, validation, and formatting
 */
@Service
@Slf4j
public class PhoneValidationService {
    
    // E.164 international phone number format regex
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    
    // US phone number patterns (for more flexible validation)
    private static final Pattern US_PHONE_PATTERN = Pattern.compile("^(\\+1|1)?[\\s\\-\\.]?\\(?([0-9]{3})\\)?[\\s\\-\\.]?([0-9]{3})[\\s\\-\\.]?([0-9]{4})$");
    
    // Common international patterns
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^(\\+\\d{1,3})?[\\s\\-\\.]?\\(?\\d{1,4}\\)?[\\s\\-\\.\\d]{4,14}$");
    
    /**
     * Normalize phone number to E.164 format
     * @param phoneNumber Raw phone number input
     * @return Normalized phone number in E.164 format
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        
        // Remove all non-digit characters except +
        String cleaned = phoneNumber.replaceAll("[^+\\d]", "");
        
        // Handle empty result
        if (cleaned.isEmpty()) {
            return null;
        }
        
        // If it already starts with +, validate and return
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        
        // Handle US numbers (assume US if no country code)
        if (cleaned.length() == 10) {
            // US number without country code
            return "+1" + cleaned;
        } else if (cleaned.length() == 11 && cleaned.startsWith("1")) {
            // US number with country code but no +
            return "+" + cleaned;
        } else if (cleaned.length() >= 7 && cleaned.length() <= 15) {
            // International number without +, assume it's valid
            return "+" + cleaned;
        }
        
        // Return as-is if we can't determine format
        return "+" + cleaned;
    }
    
    /**
     * Validate phone number format
     * @param phoneNumber Phone number to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null) {
            return false;
        }
        
        // Check E.164 format
        if (E164_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        
        // For development/testing, allow test numbers
        if (isTestNumber(normalized)) {
            return true;
        }
        
        log.warn("Invalid phone number format: {} (normalized: {})", phoneNumber, normalized);
        return false;
    }
    
    /**
     * Check if phone number is a test number
     * @param phoneNumber Phone number to check
     * @return true if it's a test number
     */
    public boolean isTestNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }
        
        return phoneNumber.startsWith("+1555") ||
               phoneNumber.startsWith("+1000") ||
               phoneNumber.contains("TEST") ||
               phoneNumber.equals("+15005550006") || // Twilio test numbers
               phoneNumber.equals("+15005550001") ||
               phoneNumber.equals("+1234567890");    // Generic test
    }
    
    /**
     * Format phone number for display (masking for privacy)
     * @param phoneNumber Phone number to format
     * @return Masked phone number for display
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return phoneNumber;
        }
        
        // Show country code and last 4 digits
        if (phoneNumber.startsWith("+1") && phoneNumber.length() >= 6) {
            String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
            return "+1***-***-" + lastFour;
        } else if (phoneNumber.startsWith("+") && phoneNumber.length() >= 6) {
            String countryCode = phoneNumber.substring(0, Math.min(4, phoneNumber.length() - 4));
            String lastFour = phoneNumber.substring(phoneNumber.length() - 4);
            return countryCode + "***" + lastFour;
        }
        
        // Fallback: show last 4 digits
        String visible = phoneNumber.substring(phoneNumber.length() - 4);
        String masked = "*".repeat(phoneNumber.length() - 4);
        return masked + visible;
    }
    
    /**
     * Validate phone number with detailed error message
     * @param phoneNumber Phone number to validate
     * @return ValidationResult with details
     */
    public ValidationResult validateWithDetails(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return new ValidationResult(false, "Phone number is required");
        }
        
        String original = phoneNumber;
        String normalized = normalizePhoneNumber(phoneNumber);
        
        if (normalized == null) {
            return new ValidationResult(false, "Invalid phone number format - contains no valid digits");
        }
        
        if (!E164_PATTERN.matcher(normalized).matches() && !isTestNumber(normalized)) {
            return new ValidationResult(false, 
                String.format("Invalid phone number format. Expected international format (+1234567890). Got: %s", 
                    maskPhoneNumber(original)));
        }
        
        if (normalized.length() > 16) {
            return new ValidationResult(false, "Phone number too long (max 15 digits including country code)");
        }
        
        return new ValidationResult(true, "Valid phone number", normalized);
    }
    
    /**
     * Get country code from phone number
     * @param phoneNumber Phone number
     * @return Country code or null if not determinable
     */
    public String getCountryCode(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null || !normalized.startsWith("+")) {
            return null;
        }
        
        // Simple country code extraction (can be enhanced)
        if (normalized.startsWith("+1")) return "US";
        if (normalized.startsWith("+44")) return "GB";
        if (normalized.startsWith("+33")) return "FR";
        if (normalized.startsWith("+49")) return "DE";
        if (normalized.startsWith("+91")) return "IN";
        
        // Return first 1-4 digits as country code
        for (int i = 2; i <= Math.min(5, normalized.length()); i++) {
            String code = normalized.substring(0, i);
            // This could be enhanced with a proper country code database
            return code;
        }
        
        return null;
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String normalizedNumber;
        
        public ValidationResult(boolean valid, String message) {
            this(valid, message, null);
        }
        
        public ValidationResult(boolean valid, String message, String normalizedNumber) {
            this.valid = valid;
            this.message = message;
            this.normalizedNumber = normalizedNumber;
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getNormalizedNumber() { return normalizedNumber; }
    }
}
