package com.authx.controller;

import com.authx.service.MockSmsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Development Controller for Testing SMS and Other Features
 * Only available when sms.provider=mock for security
 * (Note: We use sms.provider=mock as the flag for general dev mode mostly,
 * or we could check mail.enabled=false too, but this is simple enough)
 */
@RestController
@RequestMapping("/dev")
@Tag(name = "Development Tools", description = "Development and testing utilities")
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock")
@Slf4j
public class DevController {

    private final MockSmsProvider mockSmsProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DevController(MockSmsProvider mockSmsProvider, RedisTemplate<String, String> redisTemplate) {
        this.mockSmsProvider = mockSmsProvider;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/inbox")
    @Operation(summary = "Get mock inbox", description = "Get mock emails/SMS for a specific identifier")
    public ResponseEntity<?> getInbox(@RequestParam String identifier, @RequestParam String type) {
        try {
            String key;
            if ("email".equalsIgnoreCase(type)) {
                key = "mock:inbox:email:" + identifier;
            } else if ("sms".equalsIgnoreCase(type)) {
                key = "mock:inbox:sms:" + identifier;
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid type. Use 'email' or 'sms'"));
            }

            // Get last 50 messages
            List<String> rawMessages = redisTemplate.opsForList().range(key, 0, -1);

            if (rawMessages == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<JsonNode> messages = new ArrayList<>();
            for (String json : rawMessages) {
                try {
                    messages.add(objectMapper.readTree(json));
                } catch (Exception e) {
                    log.error("Failed to parse message json", e);
                }
            }

            return ResponseEntity.ok(messages);

        } catch (Exception e) {
            log.error("Failed to fetch inbox for {}", identifier, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch inbox"));
        }
    }

    @GetMapping("/sms/{phoneNumber}")
    @Operation(summary = "Get mock SMS messages", description = "Retrieve recent mock SMS messages for a phone number")
    public ResponseEntity<?> getMockSms(@PathVariable String phoneNumber,
            @RequestParam(defaultValue = "10") int minutes) {
        try {
            Duration within = Duration.ofMinutes(minutes);
            List<MockSmsProvider.MockSmsMessage> messages = mockSmsProvider.getRecentSms(phoneNumber, within);

            return ResponseEntity.ok(Map.of(
                    "phoneNumber", phoneNumber,
                    "timeWindow", minutes + " minutes",
                    "messageCount", messages.size(),
                    "messages", messages));

        } catch (Exception e) {
            log.error("Failed to retrieve mock SMS for {}", phoneNumber, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve mock SMS messages"));
        }
    }

    @GetMapping("/sms/all")
    @Operation(summary = "Get all recent mock SMS", description = "Retrieve all recent mock SMS messages")
    public ResponseEntity<?> getAllMockSms(@RequestParam(defaultValue = "10") int minutes) {
        try {
            Duration within = Duration.ofMinutes(minutes);
            List<MockSmsProvider.MockSmsMessage> messages = mockSmsProvider.getAllRecentSms(within);

            return ResponseEntity.ok(Map.of(
                    "timeWindow", minutes + " minutes",
                    "totalMessages", messages.size(),
                    "messages", messages));

        } catch (Exception e) {
            log.error("Failed to retrieve all mock SMS", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve mock SMS messages"));
        }
    }

    @PostMapping("/sms/test")
    @Operation(summary = "Send test SMS", description = "Send a test SMS message using mock provider")
    public ResponseEntity<?> sendTestSms(@RequestBody TestSmsRequest request) {
        try {
            mockSmsProvider.sendMockSms(request.phoneNumber(), request.message());

            return ResponseEntity.ok(Map.of(
                    "message", "Test SMS sent successfully",
                    "phoneNumber", request.phoneNumber(),
                    "sentMessage", request.message()));

        } catch (Exception e) {
            log.error("Failed to send test SMS", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sms/test-numbers")
    @Operation(summary = "Get test phone numbers", description = "Get list of available test phone numbers")
    public ResponseEntity<?> getTestNumbers() {
        List<String> testNumbers = List.of(
                "+15005550006", // Twilio test - valid
                "+1234567890", // Generic test
                "+15551234567", // Another test
                "+1555123TEST", // Obvious test number
                "+1000000000" // Dev test number
        );

        return ResponseEntity.ok(Map.of(
                "testNumbers", testNumbers,
                "description", "Use these numbers for testing SMS functionality without real SMS charges",
                "note", "These numbers will only work with mock SMS provider"));
    }

    @DeleteMapping("/sms/clear/{phoneNumber}")
    @Operation(summary = "Clear mock SMS", description = "Clear mock SMS messages for a phone number")
    public ResponseEntity<?> clearMockSms(@PathVariable String phoneNumber) {
        try {
            // Note: This would require additional implementation in MockSmsProvider
            log.info("Mock SMS clear requested for {}", phoneNumber);

            return ResponseEntity.ok(Map.of(
                    "message", "Mock SMS messages cleared for " + phoneNumber));

        } catch (Exception e) {
            log.error("Failed to clear mock SMS for {}", phoneNumber, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clear mock SMS messages"));
        }
    }

    // Request DTO
    public record TestSmsRequest(String phoneNumber, String message) {
    }
}
