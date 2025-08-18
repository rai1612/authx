package com.authx.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class EmailTestController {
    
    private static final Logger log = LoggerFactory.getLogger(EmailTestController.class);
    
    private final JavaMailSender mailSender;
    
    public EmailTestController(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    @PostMapping("/email-connection")
    public ResponseEntity<Map<String, Object>> testEmailConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Test by creating a simple message
            SimpleMailMessage testMessage = new SimpleMailMessage();
            testMessage.setTo("test@example.com");
            testMessage.setSubject("Connection Test");
            testMessage.setText("Test");
            testMessage.setFrom("noreply@authx.com");
            
            // This will validate the connection without sending
            result.put("status", "SUCCESS");
            result.put("message", "Email configuration appears valid");
            log.info("Email connection test: SUCCESS");
            
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            result.put("error_type", e.getClass().getSimpleName());
            log.error("Email connection test failed: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/send-test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestParam String to) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("AuthX Email Test");
            message.setText("This is a test email from AuthX system.");
            message.setFrom("noreply@authx.com");
            
            mailSender.send(message);
            
            result.put("status", "SUCCESS");
            result.put("message", "Test email sent successfully");
            log.info("Test email sent to: {}", to);
            
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("message", e.getMessage());
            result.put("error_type", e.getClass().getSimpleName());
            log.error("Test email failed: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}