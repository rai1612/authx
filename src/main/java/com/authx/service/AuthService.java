package com.authx.service;

import com.authx.dto.AuthRequest;
import com.authx.dto.AuthResponse;
import com.authx.dto.MfaVerificationRequest;
import com.authx.dto.RegisterRequest;
import com.authx.model.AuditLog;
import com.authx.model.User;
import com.authx.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MfaService mfaService;
    private final AuditService auditService;
    private final EmailService emailService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, MfaService mfaService,
            AuditService auditService, EmailService emailService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mfaService = mfaService;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        return userService.createUser(request);
    }

    public AuthResponse authenticate(AuthRequest request) {
        User user = userService.findByIdentifier(request.getIdentifier()).orElse(null);

        if (user == null) {
            // Log failed login for non-existent user
            auditService.logEventSync(null, AuditLog.EventType.LOGIN_FAILURE,
                    "Login attempt with non-existent identifier: " + request.getIdentifier());
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Check if user is locked by timestamp and automatically unlock if time expired
        if (userService.isUserLocked(user)) {
            auditService.logEventSync(user, AuditLog.EventType.LOGIN_FAILURE, "Login attempt on locked account");
            throw new IllegalArgumentException("Account is locked");
        } else if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())
                && user.getStatus() == User.UserStatus.LOCKED) {
            // Auto-unlock if lock time has expired but status is still LOCKED
            userService.autoUnlockExpiredUser(user.getId());
            // Refresh user data after unlocking
            user = userService.findByIdentifier(request.getIdentifier()).orElse(user);
        }

        // Check if user status allows login
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            auditService.logEventSync(user, AuditLog.EventType.LOGIN_FAILURE,
                    "Login attempt on " + user.getStatus().toString().toLowerCase() + " account");
            throw new IllegalArgumentException("Account is " + user.getStatus().toString().toLowerCase());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Log before doing any database operations to ensure audit logging happens
            auditService.logEventSync(user, AuditLog.EventType.LOGIN_FAILURE,
                    "Invalid password for user: " + user.getEmail());

            // Use separate method with transaction for database operations
            handleFailedLoginAttempt(user.getId());
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Use separate method with transaction for database operations
        handleSuccessfulLogin(user.getId());

        if (user.isMfaEnabled()) {
            // Generate MFA token and require MFA verification
            String mfaToken = jwtUtil.generateMfaToken(user.getEmail());
            // We do NOT automatically initiate challenge here.
            // The client must request the OTP via /mfa/setup/otp/send endpoint or WebAuthn
            // flow.

            auditService.logEventSync(user, AuditLog.EventType.LOGIN_SUCCESS, "Login successful, MFA required");
            return new AuthResponse(mfaToken, true);
        }

        // Generate access and refresh tokens
        Map<String, Object> claims = createUserClaims(user);
        String accessToken = jwtUtil.generateToken(user.getEmail(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        auditService.logEventSync(user, AuditLog.EventType.LOGIN_SUCCESS, "Login successful");

        return new AuthResponse(accessToken, refreshToken, 86400000L); // 24 hours
    }

    @Transactional
    public AuthResponse verifyMfa(MfaVerificationRequest request) {
        if (!jwtUtil.isMfaToken(request.getMfaToken())) {
            throw new IllegalArgumentException("Invalid MFA token");
        }

        String email = jwtUtil.extractUsername(request.getMfaToken());
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean mfaValid = false;

        if (request.getOtpCode() != null) {
            mfaValid = mfaService.verifyOtp(user, request.getOtpCode());
        } else if (request.getWebAuthnResponse() != null) {
            mfaValid = mfaService.verifyWebAuthn(user, request.getWebAuthnResponse());
        }

        if (!mfaValid) {
            auditService.logEventSync(user, AuditLog.EventType.MFA_FAILURE, "MFA verification failed");
            throw new IllegalArgumentException("Invalid MFA verification");
        }

        // Generate access and refresh tokens
        Map<String, Object> claims = createUserClaims(user);
        String accessToken = jwtUtil.generateToken(user.getEmail(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // Update last login
        userService.updateLastLogin(user.getId());
        auditService.logEventSync(user, AuditLog.EventType.MFA_SUCCESS, "MFA verification successful");

        return new AuthResponse(accessToken, refreshToken, 86400000L);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String email = jwtUtil.extractUsername(refreshToken);
        if (!jwtUtil.validateToken(refreshToken, email)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Map<String, Object> claims = createUserClaims(user);
        String newAccessToken = jwtUtil.generateToken(email, claims);
        String newRefreshToken = jwtUtil.generateRefreshToken(email);

        return new AuthResponse(newAccessToken, newRefreshToken, 86400000L);
    }

    @Transactional
    private void handleFailedLoginAttempt(Long userId) {
        userService.incrementFailedAttempts(userId);
    }

    @Transactional
    private void handleSuccessfulLogin(Long userId) {
        userService.resetFailedAttempts(userId);
        userService.updateLastLogin(userId);
    }

    private Map<String, Object> createUserClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("roles", user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .toList());
        return claims;
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userService.findByEmail(email).orElse(null);

        if (user == null) {
            // Log attempt for non-existent email but don't reveal it
            auditService.logEventSync(null, AuditLog.EventType.PASSWORD_RESET_REQUESTED,
                    "Password reset requested for non-existent email: " + email);
            return; // Still return normally to prevent email enumeration
        }

        // Generate secure random token
        String token = generateSecureToken();
        LocalDateTime expiry = LocalDateTime.now().plusHours(1); // 1-hour expiry

        // Store token in user record
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(expiry);
        userService.saveUser(user);

        // Send reset email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), token);

            // Log successful password reset initiation
            auditService.logEventSync(user, AuditLog.EventType.PASSWORD_RESET_REQUESTED,
                    "Password reset email sent to: " + email);

        } catch (Exception e) {
            // Log email sending failure
            auditService.logEventSync(user, AuditLog.EventType.PASSWORD_RESET_REQUESTED,
                    "Password reset requested but email failed to send for: " + email);
            throw new RuntimeException("Failed to send password reset email", e);
        }

    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Sanitize token - remove trailing slash if present
        if (token != null && token.endsWith("/")) {
            token = token.substring(0, token.length() - 1);
        }

        log.info("Resetting password with token: {}", token);

        String finalToken = token;
        User user = userService.findByPasswordResetToken(token)
                .orElseThrow(() -> {
                    log.error("Invalid password reset token: {}", finalToken);
                    return new RuntimeException("Invalid or expired password reset token");
                });

        if (user.getPasswordResetTokenExpiry() == null ||
                user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            log.error("Expired password reset token for user: {}", user.getEmail());
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiry(null);
            userService.saveUser(user);
            throw new RuntimeException("Invalid or expired password reset token");
        }

        userService.updatePassword(user.getId(), newPassword);

        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userService.saveUser(user);

        if (user.getStatus() == User.UserStatus.LOCKED) {
            user.setStatus(User.UserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userService.saveUser(user);
            auditService.logEventSync(user, AuditLog.EventType.ACCOUNT_UNLOCKED, "Account unlocked via password reset");
        }

        auditService.logEventSync(user, AuditLog.EventType.PASSWORD_RESET_COMPLETED, "Password reset successfully");
        log.info("Password reset successfully for user: {}", user.getEmail());
    }
}