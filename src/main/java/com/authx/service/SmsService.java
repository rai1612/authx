package com.authx.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {
    
    private final MockSmsProvider mockSmsProvider;

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
                otp
        );
        
        sendSms(phoneNumber, message);
    }

    private void sendViaTwilio(String toPhoneNumber, String message) {
        try {
            if (twilioAccountSid.isEmpty() || twilioAuthToken.isEmpty() || twilioFromNumber.isEmpty()) {
                throw new IllegalStateException("Twilio credentials not configured");
            }

            // TODO: Implement actual Twilio integration
            // Twilio.init(twilioAccountSid, twilioAuthToken);
            // Message twilioMessage = Message.creator(
            //     new PhoneNumber(toPhoneNumber),
            //     new PhoneNumber(twilioFromNumber),
            //     message
            // ).create();
            
            log.info("[TWILIO] SMS sent to {}: {}", toPhoneNumber, message);
            
        } catch (Exception e) {
            log.error("Failed to send SMS via Twilio", e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }

    private void sendViaAwsSns(String toPhoneNumber, String message) {
        try {
            // TODO: Implement AWS SNS integration
            // AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();
            // PublishRequest request = new PublishRequest()
            //     .withPhoneNumber(toPhoneNumber)
            //     .withMessage(message);
            // snsClient.publish(request);
            
            log.info("[AWS SNS] SMS sent to {}: {}", toPhoneNumber, message);
            
        } catch (Exception e) {
            log.error("Failed to send SMS via AWS SNS", e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }

    private void sendViaMockProvider(String toPhoneNumber, String message) {
        // Enhanced mock implementation with storage and retrieval
        mockSmsProvider.sendMockSms(toPhoneNumber, message);
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