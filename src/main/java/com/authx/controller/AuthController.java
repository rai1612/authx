package com.authx.controller;

import com.authx.config.RateLimitAspect;
import com.authx.dto.AuthRequest;
import com.authx.dto.AuthResponse;
import com.authx.dto.MfaVerificationRequest;
import com.authx.dto.RegisterRequest;
import com.authx.model.User;
import com.authx.service.AuthService;
import com.authx.service.AuditService;
import com.authx.service.EmailService;
import com.authx.service.RateLimitService;
import com.authx.service.UserService;
import com.authx.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.authx.model.AuditLog;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
    
    private final AuthService authService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    
    public AuthController(AuthService authService, AuditService auditService, EmailService emailService, UserService userService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }
    
    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Create a new user account")
    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid registration data")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);
            
            // Log successful registration AFTER transaction completes
            auditService.logEventSync(user, AuditLog.EventType.USER_REGISTRATION, 
                "User registered successfully: " + user.getEmail());
            
            emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
            
            return ResponseEntity.ok().body(new ApiSuccessResponse(
                "User registered successfully", 
                user.getId()
            ));
            
        } catch (IllegalArgumentException e) {
            // Log registration failure
            auditService.logEventSync(null, AuditLog.EventType.USER_REGISTRATION, 
                "Registration failed: " + e.getMessage() + " for email: " + request.getEmail());
            return ResponseEntity.badRequest().body(new ApiErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Registration failed", e);
            // Log registration failure
            auditService.logEventSync(null, AuditLog.EventType.USER_REGISTRATION, 
                "Registration failed with error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Registration failed")
            );
        }
    }
    
    @PostMapping("/login")
    @RateLimitAspect.RateLimit(type = RateLimitService.RateLimitType.LOGIN)
    @Operation(summary = "User login", description = "Authenticate user and return JWT token or MFA challenge")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "400", description = "Invalid credentials")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.authenticate(request);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Login failed")
            );
        }
    }
    
    @PostMapping("/mfa/verify")
    @RateLimitAspect.RateLimit(type = RateLimitService.RateLimitType.MFA_VERIFICATION)
    @Operation(summary = "Verify MFA", description = "Complete MFA verification and return JWT tokens")
    @ApiResponse(responseCode = "200", description = "MFA verification successful")
    @ApiResponse(responseCode = "400", description = "Invalid MFA verification")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody MfaVerificationRequest request) {
        try {
            AuthResponse response = authService.verifyMfa(request);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("MFA verification failed", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("MFA verification failed")
            );
        }
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    @ApiResponse(responseCode = "200", description = "Token refreshed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid refresh token")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.refreshToken());
            
            // CRITICAL: Log successful token refresh (SYNC)
            try {
                String email = jwtUtil.extractUsername(request.refreshToken());
                User user = userService.findByEmail(email).orElse(null);
                auditService.logEventSync(user, AuditLog.EventType.TOKEN_REFRESH, "Access token refreshed successfully");
            } catch (Exception e) {
                log.debug("Could not find user for audit logging during token refresh");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            // CRITICAL: Log failed token refresh attempt (SYNC)
            auditService.logEventSync(null, AuditLog.EventType.TOKEN_REFRESH, "Token refresh failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(new ApiErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            // CRITICAL: Log failed token refresh attempt (SYNC)
            auditService.logEventSync(null, AuditLog.EventType.TOKEN_REFRESH, "Token refresh failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Token refresh failed")
            );
        }
    }
    
    @PostMapping("/forgot-password")
    @RateLimitAspect.RateLimit(type = RateLimitService.RateLimitType.PASSWORD_RESET)
    @Operation(summary = "Forgot password", description = "Send password reset link to user email")
    @ApiResponse(responseCode = "200", description = "Reset link sent successfully")
    @ApiResponse(responseCode = "400", description = "Invalid email")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            authService.initiatePasswordReset(request.email());
            
            // Always return success to prevent email enumeration
            return ResponseEntity.ok().body(new ApiSuccessResponse(
                "If the email exists, a password reset link has been sent.", 
                null
            ));
            
        } catch (Exception e) {
            log.error("Password reset initiation failed", e);
            // Still return success to prevent enumeration
            return ResponseEntity.ok().body(new ApiSuccessResponse(
                "If the email exists, a password reset link has been sent.", 
                null
            ));
        }
    }
    
    @PostMapping("/reset-password")
    @RateLimitAspect.RateLimit(type = RateLimitService.RateLimitType.PASSWORD_RESET)
    @Operation(summary = "Reset password", description = "Reset password using reset token")
    @ApiResponse(responseCode = "200", description = "Password reset successful")
    @ApiResponse(responseCode = "400", description = "Invalid reset token or password")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.token(), request.newPassword());
            
            return ResponseEntity.ok().body(new ApiSuccessResponse(
                "Password reset successfully", 
                null
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Password reset failed", e);
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Password reset failed")
            );
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logout user and invalidate session")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    public ResponseEntity<?> logout() {
        try {
            // Get current user for audit logging
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            
            if (authentication != null && authentication.getName() != null) {
                try {
                    currentUser = userService.findByEmail(authentication.getName()).orElse(null);
                } catch (Exception e) {
                    log.debug("Could not find user for audit logging: {}", authentication.getName());
                }
            }
            
            // Log logout event before clearing context (SYNC to ensure it's saved)
            auditService.logEventSync(currentUser, AuditLog.EventType.LOGOUT, "User logged out successfully");
            
            // Clear the security context
            SecurityContextHolder.clearContext();
            
            // In a more sophisticated setup, you might:
            // 1. Add the JWT token to a blacklist
            // 2. Clear server-side session data
            // 3. Invalidate refresh tokens
            
            String userEmail = (currentUser != null) ? currentUser.getEmail() : "unknown";
            log.info("User logged out successfully: {}", userEmail);
            
            return ResponseEntity.ok().body(new ApiSuccessResponse(
                "Logout successful", 
                null
            ));
            
        } catch (Exception e) {
            log.error("Logout error", e);
            
            // CRITICAL: Still log failed logout attempt (SYNC)
            auditService.logEventSync(null, AuditLog.EventType.LOGOUT_FAILURE, "Logout failed: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("Logout failed")
            );
        }
    }
    
    // Response DTOs
    public record ApiSuccessResponse(String message, Object data) {}
    public record ApiErrorResponse(String error) {}
    public record RefreshTokenRequest(String refreshToken) {}
    public record ForgotPasswordRequest(String email) {}
    public record ResetPasswordRequest(String token, String newPassword) {}
}