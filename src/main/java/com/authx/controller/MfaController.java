package com.authx.controller;

import com.authx.dto.SendOtpRequest;
import com.authx.dto.UpdatePreferredMethodRequest;
import com.authx.model.User;
import com.authx.model.WebAuthnCredential;
import com.authx.service.AuditService;
import com.authx.service.MfaService;
import com.authx.service.UserService;
import com.authx.service.WebAuthnService;
import com.authx.model.AuditLog;
import com.authx.config.RateLimitAspect.RateLimit;
import com.authx.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mfa")
@Tag(name = "MFA Management", description = "Multi-Factor Authentication setup and management")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final WebAuthnService webAuthnService;
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/methods")
    @Operation(summary = "Get available MFA methods", description = "Retrieve user's configured MFA methods")
    @ApiResponse(responseCode = "200", description = "MFA methods retrieved successfully")
    public ResponseEntity<?> getMfaMethods(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<WebAuthnCredential> webAuthnCredentials = webAuthnService.getUserCredentials(user);
            
            return ResponseEntity.ok(Map.of(
                    "mfaEnabled", user.isMfaEnabled(),
                    "preferredMethod", user.getPreferredMfaMethod() != null ? user.getPreferredMfaMethod() : "NONE",
                    "emailConfigured", user.getEmail() != null,
                    "smsConfigured", user.getPhoneNumber() != null,
                    "webAuthnCredentials", webAuthnCredentials.stream().map(cred -> {
                        var credMap = new java.util.HashMap<String, Object>();
                        credMap.put("id", cred.getId());
                        credMap.put("credentialId", cred.getCredentialId()); // Add credential ID for debugging
                        credMap.put("nickname", cred.getNickname() != null ? cred.getNickname() : "Unnamed Device");
                        credMap.put("createdAt", cred.getCreatedAt());
                        credMap.put("lastUsedAt", cred.getLastUsedAt() != null ? cred.getLastUsedAt() : "Never");
                        credMap.put("signatureCount", cred.getSignatureCount()); // Add signature count for debugging
                        credMap.put("active", cred.isActive()); // Add active status
                        return credMap;
                    }).toList()
            ));

        } catch (Exception e) {
            log.error("Failed to get MFA methods", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve MFA methods"));
        }
    }

    @PostMapping("/enable")
    @RateLimit(type = RateLimitService.RateLimitType.MFA_SETUP)
    @Operation(summary = "Enable MFA", description = "Enable multi-factor authentication for the user")
    @ApiResponse(responseCode = "200", description = "MFA enabled successfully")
    public ResponseEntity<?> enableMfa(Authentication authentication,
                                      @RequestBody EnableMfaRequest request) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            userService.enableMfa(user.getId(), request.preferredMethod());
            
            // Note: MFA_ENABLED audit log is handled by UserService.enableMfa()
            
            return ResponseEntity.ok(Map.of(
                    "message", "MFA enabled successfully",
                    "preferredMethod", request.preferredMethod()
            ));

        } catch (Exception e) {
            log.error("Failed to enable MFA", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable MFA", description = "Disable multi-factor authentication for the user")
    @ApiResponse(responseCode = "200", description = "MFA disabled successfully")
    public ResponseEntity<?> disableMfa(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            userService.disableMfa(user.getId());
            
            // Note: MFA_DISABLED audit log is handled by UserService.disableMfa()
            
            return ResponseEntity.ok(Map.of("message", "MFA disabled successfully"));

        } catch (Exception e) {
            log.error("Failed to disable MFA", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/setup/webauthn/start")
    @RateLimit(type = RateLimitService.RateLimitType.MFA_SETUP)
    @Operation(summary = "Start WebAuthn setup", description = "Initiate WebAuthn credential registration")
    @ApiResponse(responseCode = "200", description = "WebAuthn registration challenge created")
    public ResponseEntity<?> startWebAuthnSetup(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String challenge = webAuthnService.startRegistration(user);
            
            return ResponseEntity.ok(Map.of(
                    "challenge", challenge,
                    "message", "WebAuthn registration started"
            ));

        } catch (Exception e) {
            log.error("Failed to start WebAuthn setup", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start WebAuthn setup"));
        }
    }

    @PostMapping("/setup/webauthn/finish")
    @RateLimit(type = RateLimitService.RateLimitType.MFA_SETUP)
    @Operation(summary = "Complete WebAuthn setup", description = "Complete WebAuthn credential registration")
    @ApiResponse(responseCode = "200", description = "WebAuthn credential registered successfully")
    public ResponseEntity<?> finishWebAuthnSetup(Authentication authentication,
                                                 @RequestBody FinishWebAuthnRequest request) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            webAuthnService.finishRegistration(user, request.response(), request.nickname());
            
            return ResponseEntity.ok(Map.of(
                    "message", "WebAuthn credential registered successfully",
                    "nickname", request.nickname()
            ));

        } catch (Exception e) {
            log.error("Failed to finish WebAuthn setup", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/setup/otp/send")
    @RateLimit(type = RateLimitService.RateLimitType.OTP)
    @Operation(summary = "Send OTP", description = "Send OTP to email or SMS for testing")
    @ApiResponse(responseCode = "200", description = "OTP sent successfully")
    public ResponseEntity<?> sendOtp(Authentication authentication,
                                    @RequestBody SendOtpRequest request) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if ("EMAIL".equals(request.method())) {
                mfaService.sendOtpEmail(user);
            } else if ("SMS".equals(request.method())) {
                mfaService.sendOtpSms(user);
            } else {
                throw new IllegalArgumentException("Invalid OTP method");
            }
            
            return ResponseEntity.ok(Map.of(
                    "message", "OTP sent successfully",
                    "method", request.method()
            ));

        } catch (Exception e) {
            log.error("Failed to send OTP", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/webauthn/{credentialId}")
    @Operation(summary = "Delete WebAuthn credential", description = "Remove a WebAuthn credential")
    @ApiResponse(responseCode = "200", description = "Credential deleted successfully")
    public ResponseEntity<?> deleteWebAuthnCredential(Authentication authentication,
                                                      @PathVariable Long credentialId) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            webAuthnService.deleteCredential(user, credentialId);
            
            return ResponseEntity.ok(Map.of("message", "Credential deleted successfully"));

        } catch (Exception e) {
            log.error("Failed to delete WebAuthn credential", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webauthn/challenge")
    @RateLimit(type = RateLimitService.RateLimitType.MFA_VERIFICATION)
    @Operation(summary = "Get WebAuthn challenge", description = "Get WebAuthn authentication challenge")
    @ApiResponse(responseCode = "200", description = "Challenge generated successfully")
    public ResponseEntity<?> getWebAuthnChallenge(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (!webAuthnService.hasWebAuthnCredentials(user)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No WebAuthn credentials configured"));
            }

            String challenge = mfaService.initiateWebAuthnChallenge(user);
            
            return ResponseEntity.ok(Map.of(
                    "challenge", challenge,
                    "message", "WebAuthn challenge generated"
            ));

        } catch (Exception e) {
            log.error("Failed to generate WebAuthn challenge", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate challenge"));
        }
    }

    @PutMapping("/preferred-method")
    @RateLimit(type = RateLimitService.RateLimitType.MFA_PREFERRED_METHOD)
    @Operation(summary = "Update preferred MFA method", description = "Update the user's preferred MFA method")
    @ApiResponse(responseCode = "200", description = "Preferred method updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid method or method not available")
    public ResponseEntity<?> updatePreferredMethod(@RequestBody @Valid UpdatePreferredMethodRequest request, Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            User.MfaMethod newMethod = request.preferredMethod();
            
            // Validate that the user can use this method
            if (!isMethodAvailable(user, newMethod)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Selected MFA method is not available. Please set up the method first."));
            }

            User.MfaMethod oldMethod = user.getPreferredMfaMethod();
            user.setPreferredMfaMethod(newMethod);
            userService.saveUser(user);

            // Log the change
            auditService.logEvent(user, AuditLog.EventType.MFA_METHOD_UPDATED,
                    "Preferred MFA method changed from " + oldMethod + " to " + newMethod);

            log.info("User {} changed preferred MFA method from {} to {}", user.getEmail(), oldMethod, newMethod);

            return ResponseEntity.ok(Map.of(
                    "message", "Preferred MFA method updated successfully",
                    "previousMethod", oldMethod,
                    "newMethod", newMethod
            ));

        } catch (Exception e) {
            log.error("Failed to update preferred MFA method", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to update preferred method"));
        }
    }

    /**
     * Check if a specific MFA method is available for the user
     */
    private boolean isMethodAvailable(User user, User.MfaMethod method) {
        return switch (method) {
            case OTP_EMAIL -> user.getEmail() != null && !user.getEmail().trim().isEmpty();
            case OTP_SMS -> user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isEmpty();
            case WEBAUTHN -> webAuthnService.hasWebAuthnCredentials(user);
        };
    }

    // Request DTOs
    public record EnableMfaRequest(User.MfaMethod preferredMethod) {}
    public record FinishWebAuthnRequest(String response, String nickname) {}
    public record SendOtpRequest(String method) {}
    public record UpdatePreferredMethodRequest(User.MfaMethod preferredMethod) {}
}