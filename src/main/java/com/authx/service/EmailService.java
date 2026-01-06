package com.authx.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Value("${spring.mail.enabled:true}")
    private boolean mailEnabled;

    public EmailService(JavaMailSender mailSender, RedisTemplate<String, String> redisTemplate) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    private void saveMockEmail(String to, String subject, String content) {
        try {
            Map<String, String> emailData = new HashMap<>();
            emailData.put("to", to);
            emailData.put("subject", subject);
            emailData.put("content", content);
            emailData.put("timestamp", LocalDateTime.now().toString());
            emailData.put("type", "EMAIL");

            String json = objectMapper.writeValueAsString(emailData);
            String key = "mock:inbox:email:" + to;

            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.expire(key, Duration.ofHours(24)); // Keep for 24 hours

            // Limit to last 50 messages
            redisTemplate.opsForList().trim(key, 0, 49);

            log.info("[MOCK EMAIL SAVED] To: {} Subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to save mock email to Redis", e);
        }
    }

    @Async
    public void sendOtpEmail(String to, String otp) {
        if (!mailEnabled) {
            log.warn("[MOCK EMAIL] To: {}, Subject: AuthX - Your Verification Code", to);
            log.warn("[MOCK CONTENT] OTP: {}", otp);
            saveMockEmail(to, "AuthX - Your Verification Code", buildOtpEmailContent(otp));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("AuthX - Your Verification Code");
            message.setText(buildOtpEmailContent(otp));
            message.setFrom("noreply@authx.com");

            log.debug("Attempting to send OTP email to: {}", to);
            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", to);

        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("Email authentication failed: {}. Check MAIL_USERNAME and MAIL_PASSWORD", e.getMessage());
            log.warn("[DEV MODE] Email auth failed - showing OTP in logs: {}", otp);
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Email send failed: {}. Check SMTP settings", e.getMessage());
            log.warn("[DEV MODE] Email send failed - showing OTP in logs: {}", otp);
        } catch (Exception e) {
            log.error("Unexpected email error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            log.warn("[DEV MODE] Email failed - showing OTP in logs: {}", otp);
        }
    }

    @Async
    public void sendWelcomeEmail(String to, String username) {
        if (!mailEnabled) {
            log.warn("[MOCK EMAIL] To: {}, Subject: Welcome to AuthX!", to);
            saveMockEmail(to, "Welcome to AuthX!", buildWelcomeEmailContent(username));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Welcome to AuthX!");
            message.setText(buildWelcomeEmailContent(username));
            message.setFrom("noreply@authx.com");

            log.debug("Attempting to send welcome email to: {}", to);
            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", to);

        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("Email authentication failed: {}. Check MAIL_USERNAME and MAIL_PASSWORD", e.getMessage());
            log.warn("[DEV MODE] Welcome email auth failed - registration completed anyway");
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Email send failed: {}. Check SMTP settings", e.getMessage());
            log.warn("[DEV MODE] Welcome email send failed - registration completed anyway");
        } catch (Exception e) {
            log.error("Unexpected email error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            log.warn("[DEV MODE] Welcome email failed - registration completed anyway");
        }
    }

    private String buildOtpEmailContent(String otp) {
        return String.format("""
                Hello,

                Your AuthX verification code is: %s

                This code will expire in 5 minutes. Please do not share this code with anyone.

                If you didn't request this code, please ignore this email.

                Best regards,
                AuthX Security Team
                """, otp);
    }

    private String buildWelcomeEmailContent(String username) {
        return String.format("""
                Hello %s,

                Welcome to AuthX! Your account has been successfully created.

                You can now log in and set up multi-factor authentication for enhanced security.

                If you have any questions, please contact our support team.

                Best regards,
                AuthX Team
                """, username);
    }

    @Async
    public void sendPasswordResetEmail(String to, String username, String token) {
        if (!mailEnabled) {
            log.warn("[MOCK EMAIL] To: {}, Subject: AuthX - Password Reset Request", to);
            log.warn("[MOCK CONTENT] Token: {}", token);
            saveMockEmail(to, "AuthX - Password Reset Request", buildPasswordResetEmailContent(username, token));
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("AuthX - Password Reset Request");
            message.setText(buildPasswordResetEmailContent(username, token));
            message.setFrom("noreply@authx.com");

            log.debug("Attempting to send password reset email to: {}", to);
            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", to);

        } catch (org.springframework.mail.MailAuthenticationException e) {
            log.error("Email authentication failed: {}. Check MAIL_USERNAME and MAIL_PASSWORD", e.getMessage());
            log.warn("[DEV MODE] Email auth failed - password reset token: {}", token);
        } catch (org.springframework.mail.MailSendException e) {
            log.error("Email send failed: {}. Check SMTP settings", e.getMessage());
            log.warn("[DEV MODE] Email send failed - password reset token: {}", token);
        } catch (Exception e) {
            log.error("Unexpected email error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            log.warn("[DEV MODE] Email failed - password reset token: {}", token);
        }
    }

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private String buildPasswordResetEmailContent(String username, String token) {
        // In production, this should be a proper HTML template with a frontend URL
        // We use /?token=... so index.html loads and auth.js logic picks it up
        String resetUrl = frontendUrl + "/?token=" + token;

        return String.format("""
                Hello %s,

                We received a request to reset your password for your AuthX account.

                To reset your password, click the link below:
                %s

                This link will expire in 1 hour for security reasons.

                If you didn't request this password reset, please ignore this email.
                Your password will remain unchanged.

                For security reasons, please do not share this link with anyone.

                Best regards,
                AuthX Security Team
                """, username, resetUrl);
    }
}