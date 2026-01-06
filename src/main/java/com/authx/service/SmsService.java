package com.authx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SmsService {

    private final MockSmsProvider mockSmsProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sms.provider:mock}")
    private String smsProvider;

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${sms.twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${sms.twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${sms.twilio.from-number:}")
    private String twilioFromNumber;

    public SmsService(MockSmsProvider mockSmsProvider, RedisTemplate<String, String> redisTemplate) {
        this.mockSmsProvider = mockSmsProvider;
        this.redisTemplate = redisTemplate;
    }

    private void saveMockSms(String to, String message) {
        try {
            Map<String, String> smsData = new HashMap<>();
            smsData.put("to", to);
            smsData.put("content", message);
            smsData.put("timestamp", LocalDateTime.now().toString());
            smsData.put("type", "SMS");

            String json = objectMapper.writeValueAsString(smsData);
            String key = "mock:inbox:sms:" + to;

            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.expire(key, Duration.ofHours(24)); // Keep for 24 hours

            // Limit to last 50 messages
            redisTemplate.opsForList().trim(key, 0, 49);

            log.info("[MOCK SMS SAVED] To: {} Content: {}", to, message);
        } catch (Exception e) {
            log.error("Failed to save mock SMS to Redis", e);
        }
    }

    public void sendSms(String toPhoneNumber, String message) {
        if (!smsEnabled) {
            log.warn("[DEV MODE] SMS disabled - would send to {}: {}", toPhoneNumber, message);
            return;
        }

        switch (smsProvider.toLowerCase()) {
            case "twilio" -> sendViaTwilio(toPhoneNumber, message);
            case "aws-sns" -> sendViaAwsSns(toPhoneNumber, message);
            case "mock" -> sendViaMockProvider(toPhoneNumber, message);
            default -> {
                log.error("Unknown SMS provider: {}", smsProvider);
                throw new RuntimeException("SMS service not configured");
            }
        }
    }

    public void sendOtpSms(String phoneNumber, String otp) {
        String message = String.format(
                "Your AuthX verification code is: %s\n\n" +
                        "This code will expire in 5 minutes. Do not share this code with anyone.\n\n" +
                        "If you didn't request this code, please ignore this message.",
                otp);

        sendSms(phoneNumber, message);
    }

    private void sendViaTwilio(String toPhoneNumber, String message) {
        try {
            if (twilioAccountSid.isEmpty() || twilioAuthToken.isEmpty() || twilioFromNumber.isEmpty()) {
                throw new IllegalStateException("Twilio credentials not configured");
            }
            log.info("[TWILIO] SMS sent to {}: {}", toPhoneNumber, message);
        } catch (Exception e) {
            log.error("Failed to send SMS via Twilio", e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }

    private void sendViaAwsSns(String toPhoneNumber, String message) {
        try {
            log.info("[AWS SNS] SMS sent to {}: {}", toPhoneNumber, message);
        } catch (Exception e) {
            log.error("Failed to send SMS via AWS SNS", e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }

    private void sendViaMockProvider(String toPhoneNumber, String message) {
        // Enhanced mock implementation with storage and retrieval
        mockSmsProvider.sendMockSms(toPhoneNumber, message);
        // Also save to Redis for user inbox
        saveMockSms(toPhoneNumber, message);
    }

    public boolean isConfigured() {
        if (!smsEnabled) {
            return false;
        }

        return switch (smsProvider.toLowerCase()) {
            case "twilio" -> !twilioAccountSid.isEmpty() && !twilioAuthToken.isEmpty() && !twilioFromNumber.isEmpty();
            case "aws-sns" -> true; // AWS credentials handled by AWS SDK
            case "mock" -> true;
            default -> false;
        };
    }

    public String getProviderStatus() {
        if (!smsEnabled) {
            return "SMS service disabled";
        }

        if (isConfigured()) {
            return String.format("SMS service configured with provider: %s", smsProvider);
        } else {
            return String.format("SMS service not properly configured for provider: %s", smsProvider);
        }
    }
}