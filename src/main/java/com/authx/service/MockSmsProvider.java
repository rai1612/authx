package com.authx.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Mock SMS Provider for Development and Testing
 * Simulates real SMS sending with storage and retrieval capabilities
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MockSmsProvider {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // In-memory storage for development (fallback if Redis not available)
    private final Map<String, List<MockSmsMessage>> inMemoryStorage = new ConcurrentHashMap<>();
    
    // Test phone numbers that should always work
    private static final Set<String> TEST_NUMBERS = Set.of(
        "+15005550006", // Twilio test - valid
        "+15005550001", // Twilio test - invalid
        "+1234567890",  // Generic test
        "+15551234567", // Another test
        "+1555123TEST", // Obvious test number
        "+1000000000"   // Dev test number
    );
    
    public void sendMockSms(String toPhoneNumber, String message) {
        try {
            // Validate phone number format
            if (!isValidPhoneFormat(toPhoneNumber)) {
                throw new IllegalArgumentException("Invalid phone number format: " + toPhoneNumber);
            }
            
            // Create mock SMS message
            MockSmsMessage sms = MockSmsMessage.builder()
                .phoneNumber(toPhoneNumber)
                .message(message)
                .timestamp(LocalDateTime.now())
                .messageId(generateMessageId())
                .status("delivered")
                .provider("mock")
                .build();
            
            // Store in Redis with expiration (10 minutes)
            storeInRedis(sms);
            
            // Store in memory as fallback
            storeInMemory(sms);
            
            // Log with realistic SMS formatting
            logMockSms(sms);
            
            // Simulate processing delay
            simulateDelay();
            
            log.info("📱 [MOCK SMS] Successfully sent to {} - ID: {}", 
                maskPhoneNumber(toPhoneNumber), sms.getMessageId());
                
        } catch (Exception e) {
            log.error("❌ [MOCK SMS] Failed to send to {}: {}", 
                maskPhoneNumber(toPhoneNumber), e.getMessage());
            throw new RuntimeException("Mock SMS sending failed", e);
        }
    }
    
    public List<MockSmsMessage> getRecentSms(String phoneNumber, Duration within) {
        List<MockSmsMessage> messages = new ArrayList<>();
        
        try {
            // Try Redis first
            messages.addAll(getFromRedis(phoneNumber, within));
        } catch (Exception e) {
            log.warn("Failed to retrieve from Redis, using memory storage: {}", e.getMessage());
            // Fallback to memory storage
            messages.addAll(getFromMemory(phoneNumber, within));
        }
        
        return messages.stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .toList();
    }
    
    public List<MockSmsMessage> getAllRecentSms(Duration within) {
        List<MockSmsMessage> allMessages = new ArrayList<>();
        
        // Get all phone numbers from memory storage
        inMemoryStorage.keySet().forEach(phoneNumber -> {
            allMessages.addAll(getRecentSms(phoneNumber, within));
        });
        
        return allMessages.stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .toList();
    }
    
    public boolean isTestNumber(String phoneNumber) {
        return TEST_NUMBERS.contains(phoneNumber) || 
               phoneNumber.contains("TEST") || 
               phoneNumber.startsWith("+1555") ||
               phoneNumber.startsWith("+1000");
    }
    
    private void storeInRedis(MockSmsMessage sms) {
        try {
            String key = "mock_sms:" + sms.getPhoneNumber();
            String value = sms.toJson();
            
            // Store with 10-minute expiration
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.expire(key, Duration.ofMinutes(10));
            
        } catch (Exception e) {
            log.warn("Failed to store SMS in Redis: {}", e.getMessage());
        }
    }
    
    private List<MockSmsMessage> getFromRedis(String phoneNumber, Duration within) {
        try {
            String key = "mock_sms:" + phoneNumber;
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            
            LocalDateTime cutoff = LocalDateTime.now().minus(within);
            
            return values.stream()
                .map(MockSmsMessage::fromJson)
                .filter(sms -> sms.getTimestamp().isAfter(cutoff))
                .toList();
                
        } catch (Exception e) {
            log.warn("Failed to retrieve SMS from Redis: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private void storeInMemory(MockSmsMessage sms) {
        inMemoryStorage.computeIfAbsent(sms.getPhoneNumber(), k -> new ArrayList<>())
            .add(sms);
            
        // Clean up old messages (keep only last 10 minutes)
        cleanupOldMessages(sms.getPhoneNumber());
    }
    
    private List<MockSmsMessage> getFromMemory(String phoneNumber, Duration within) {
        List<MockSmsMessage> messages = inMemoryStorage.getOrDefault(phoneNumber, new ArrayList<>());
        LocalDateTime cutoff = LocalDateTime.now().minus(within);
        
        return messages.stream()
            .filter(sms -> sms.getTimestamp().isAfter(cutoff))
            .toList();
    }
    
    private void cleanupOldMessages(String phoneNumber) {
        List<MockSmsMessage> messages = inMemoryStorage.get(phoneNumber);
        if (messages == null) return;
        
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        messages.removeIf(sms -> sms.getTimestamp().isBefore(cutoff));
    }
    
    private void logMockSms(MockSmsMessage sms) {
        String timestamp = sms.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String maskedPhone = maskPhoneNumber(sms.getPhoneNumber());
        
        log.info("📱💬 ═══════════════════════════════════════");
        log.info("📱 MOCK SMS SENT");
        log.info("📱 To: {}", maskedPhone);
        log.info("📱 Time: {}", timestamp);
        log.info("📱 ID: {}", sms.getMessageId());
        log.info("📱 Message: {}", sms.getMessage());
        log.info("📱💬 ═══════════════════════════════════════");
    }
    
    private boolean isValidPhoneFormat(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        // Allow test numbers
        if (isTestNumber(phoneNumber)) {
            return true;
        }
        
        // Basic E.164 format validation
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return phoneNumber;
        }
        
        String visible = phoneNumber.substring(phoneNumber.length() - 4);
        String masked = "*".repeat(phoneNumber.length() - 4);
        return masked + visible;
    }
    
    private String generateMessageId() {
        return "mock_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private void simulateDelay() {
        try {
            Thread.sleep(100 + new Random().nextInt(200)); // 100-300ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Mock SMS Message Data Class
    public static class MockSmsMessage {
        private String phoneNumber;
        private String message;
        private LocalDateTime timestamp;
        private String messageId;
        private String status;
        private String provider;
        
        // Constructors, getters, setters, builder pattern
        public static MockSmsMessageBuilder builder() {
            return new MockSmsMessageBuilder();
        }
        
        public String toJson() {
            return String.format(
                "{\"phoneNumber\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"messageId\":\"%s\",\"status\":\"%s\",\"provider\":\"%s\"}",
                phoneNumber, message, timestamp, messageId, status, provider
            );
        }
        
        public static MockSmsMessage fromJson(String json) {
            // Simple JSON parsing - in production use ObjectMapper
            // This is a simplified implementation for the mock service
            return MockSmsMessage.builder()
                .phoneNumber(extractJsonValue(json, "phoneNumber"))
                .message(extractJsonValue(json, "message"))
                .timestamp(LocalDateTime.parse(extractJsonValue(json, "timestamp")))
                .messageId(extractJsonValue(json, "messageId"))
                .status(extractJsonValue(json, "status"))
                .provider(extractJsonValue(json, "provider"))
                .build();
        }
        
        private static String extractJsonValue(String json, String key) {
            int start = json.indexOf("\"" + key + "\":\"") + key.length() + 4;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        
        // Getters and setters
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        // Builder class
        public static class MockSmsMessageBuilder {
            private MockSmsMessage message = new MockSmsMessage();
            
            public MockSmsMessageBuilder phoneNumber(String phoneNumber) {
                message.phoneNumber = phoneNumber;
                return this;
            }
            
            public MockSmsMessageBuilder message(String messageText) {
                message.message = messageText;
                return this;
            }
            
            public MockSmsMessageBuilder timestamp(LocalDateTime timestamp) {
                message.timestamp = timestamp;
                return this;
            }
            
            public MockSmsMessageBuilder messageId(String messageId) {
                message.messageId = messageId;
                return this;
            }
            
            public MockSmsMessageBuilder status(String status) {
                message.status = status;
                return this;
            }
            
            public MockSmsMessageBuilder provider(String provider) {
                message.provider = provider;
                return this;
            }
            
            public MockSmsMessage build() {
                return message;
            }
        }
    }
}
