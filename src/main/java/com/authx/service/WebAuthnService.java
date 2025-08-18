package com.authx.service;

import com.authx.model.AuditLog;
import com.authx.model.User;
import com.authx.model.WebAuthnCredential;
import com.authx.repository.WebAuthnCredentialRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebAuthnService {

    private final WebAuthnCredentialRepository credentialRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Value("${mfa.webauthn.rp-id}")
    private String rpId;

    @Value("${mfa.webauthn.rp-name}")
    private String rpName;

    @Value("${mfa.webauthn.origin}")
    private String origin;

    public String startRegistration(User user) {
        try {
            // Generate a simple challenge for WebAuthn registration
            String challenge = UUID.randomUUID().toString();
            
            // Create a basic WebAuthn credential creation options
            Map<String, Object> request = Map.of(
                    "challenge", challenge,
                    "rp", Map.of(
                            "name", rpName,
                            "id", rpId
                    ),
                    "user", Map.of(
                            "id", user.getId().toString(),
                            "name", user.getEmail(),
                            "displayName", user.getUsername()
                    ),
                    "pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", -7)),
                    "timeout", 300000,
                    "attestation", "direct"
            );

            // Store the challenge in Redis for later verification
            String challengeKey = "webauthn:reg:" + user.getId();
            String challengeData = objectMapper.writeValueAsString(Map.of("challenge", challenge));
            redisTemplate.opsForValue().set(challengeKey, challengeData, Duration.ofMinutes(5));

            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_REGISTRATION_STARTED, "WebAuthn registration initiated");
            
            return objectMapper.writeValueAsString(request);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebAuthn registration request", e);
            throw new RuntimeException("Failed to start WebAuthn registration", e);
        }
    }

    @Transactional
    public void finishRegistration(User user, String responseJson, String nickname) {
        try {
            String challengeKey = "webauthn:reg:" + user.getId();
            String storedChallenge = redisTemplate.opsForValue().get(challengeKey);
            
            if (storedChallenge == null) {
                throw new IllegalArgumentException("Registration challenge not found or expired");
            }

            // Parse the response (simplified validation for demo purposes)
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            
            // Extract the actual credential ID from the WebAuthn response
            String credentialId = (String) response.get("id");
            if (credentialId == null || credentialId.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid credential ID in WebAuthn response");
            }
            
            // Extract raw ID for additional verification
            String rawId = (String) response.get("rawId");
            
            WebAuthnCredential webAuthnCredential = new WebAuthnCredential();
            webAuthnCredential.setUser(user);
            webAuthnCredential.setCredentialId(credentialId);  // Use ACTUAL credential ID
            webAuthnCredential.setPublicKey(rawId != null ? rawId : credentialId); // Store rawId as public key reference
            webAuthnCredential.setSignatureCount(0L);
            webAuthnCredential.setNickname(nickname != null ? nickname : "WebAuthn Key");
            webAuthnCredential.setActive(true);

            credentialRepository.save(webAuthnCredential);

            // Clean up the challenge
            redisTemplate.delete(challengeKey);

            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_REGISTRATION_SUCCESS, 
                    "WebAuthn credential registered: " + nickname);
            
            log.info("WebAuthn credential registered for user: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to complete WebAuthn registration", e);
            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_REGISTRATION_FAILED, e.getMessage());
            throw new RuntimeException("WebAuthn registration failed: " + e.getMessage(), e);
        }
    }

    public String startAuthentication(User user) {
        try {
            // Generate a simple challenge for WebAuthn authentication
            String challenge = UUID.randomUUID().toString();
            
            // Get user's credentials
            List<WebAuthnCredential> credentials = credentialRepository.findByUserIdAndActiveTrue(user.getId());
            
            log.info("Found {} active WebAuthn credentials for user {}", credentials.size(), user.getEmail());
            
            // If no credentials found, log this important information
            if (credentials.isEmpty()) {
                log.warn("No WebAuthn credentials found for user {} during authentication", user.getEmail());
                auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, 
                    "No WebAuthn credentials found for user");
            }
            
            Map<String, Object> request = Map.of(
                    "challenge", challenge,
                    "timeout", 300000,
                    "rpId", rpId,
                    "allowCredentials", credentials.stream()
                            .map(cred -> {
                                log.debug("Including credential ID: {} for user: {}", cred.getCredentialId(), user.getEmail());
                                return Map.of(
                                    "type", "public-key",
                                    "id", cred.getCredentialId()
                                );
                            })
                            .toList()
            );

            // Store the challenge in Redis for later verification
            String challengeKey = "webauthn:auth:" + user.getId();
            String challengeData = objectMapper.writeValueAsString(Map.of("challenge", challenge));
            redisTemplate.opsForValue().set(challengeKey, challengeData, Duration.ofMinutes(5));

            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_STARTED, "WebAuthn authentication initiated");
            
            return objectMapper.writeValueAsString(request);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebAuthn authentication request", e);
            throw new RuntimeException("Failed to start WebAuthn authentication", e);
        }
    }

    @Transactional
    public boolean finishAuthentication(User user, String responseJson) {
        try {
            String challengeKey = "webauthn:auth:" + user.getId();
            String storedChallenge = redisTemplate.opsForValue().get(challengeKey);
            
            if (storedChallenge == null) {
                auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, 
                        "Authentication challenge not found or expired");
                return false;
            }

            // Parse the response (simplified validation for demo purposes)
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            
            // Extract credential ID from the authentication response
            String credentialId = (String) response.get("id");
            if (credentialId == null || credentialId.trim().isEmpty()) {
                auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, 
                    "Invalid credential ID in authentication response");
                return false;
            }
            
            log.info("Attempting to authenticate with credential ID: {} for user: {}", credentialId, user.getEmail());
            
            // Find the specific credential used for authentication
            List<WebAuthnCredential> userCredentials = credentialRepository.findByUserIdAndActiveTrue(user.getId());
            
            if (userCredentials.isEmpty()) {
                log.warn("No active WebAuthn credentials found for user {} during verification", user.getEmail());
                auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, "No active WebAuthn credentials");
                return false;
            }
            
            // Find the credential that matches the ID used in authentication
            WebAuthnCredential matchingCredential = userCredentials.stream()
                .filter(cred -> credentialId.equals(cred.getCredentialId()))
                .findFirst()
                .orElse(null);
            
            if (matchingCredential == null) {
                log.warn("No matching credential found for ID: {} for user: {}", credentialId, user.getEmail());
                auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, 
                    "No matching credential found for ID: " + credentialId);
                return false;
            }
            
            log.info("Found matching credential: {} for user: {}", matchingCredential.getNickname(), user.getEmail());

            // Update signature count and usage timestamp
            matchingCredential.setSignatureCount(matchingCredential.getSignatureCount() + 1);
            matchingCredential.setLastUsedAt(LocalDateTime.now());
            credentialRepository.save(matchingCredential);

            // Clean up the challenge
            redisTemplate.delete(challengeKey);

            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_SUCCESS, "WebAuthn authentication successful");
            
            log.info("WebAuthn authentication successful for user: {}", user.getEmail());
            return true;

        } catch (Exception e) {
            log.error("Failed to complete WebAuthn authentication", e);
            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_AUTH_FAILED, e.getMessage());
            return false;
        }
    }

    public List<WebAuthnCredential> getUserCredentials(User user) {
        return credentialRepository.findByUserIdAndActiveTrue(user.getId());
    }

    @Transactional
    public void deleteCredential(User user, Long credentialId) {
        Optional<WebAuthnCredential> credential = credentialRepository.findById(credentialId);
        
        if (credential.isPresent() && credential.get().getUser().getId().equals(user.getId())) {
            String credentialName = credential.get().getNickname();
            
            // Deactivate the credential
            credential.get().setActive(false);
            credentialRepository.save(credential.get());
            
            // Check if this was the last WebAuthn credential
            List<WebAuthnCredential> remainingCredentials = credentialRepository.findByUserIdAndActiveTrue(user.getId());
            
            if (remainingCredentials.isEmpty()) {
                log.info("Last WebAuthn credential deleted for user {}, updating preferred MFA method", user.getEmail());
                
                // If user's preferred method was WebAuthn, switch to email
                if (user.getPreferredMfaMethod() == User.MfaMethod.WEBAUTHN) {
                    user.setPreferredMfaMethod(User.MfaMethod.OTP_EMAIL);
                    userService.saveUser(user);
                    
                    auditService.logEvent(user, AuditLog.EventType.MFA_METHOD_UPDATED, 
                        "Preferred MFA method automatically changed from WEBAUTHN to OTP_EMAIL (no WebAuthn credentials remaining)");
                    
                    log.info("Updated preferred MFA method to OTP_EMAIL for user {} (no WebAuthn credentials remaining)", user.getEmail());
                }
            }
            
            auditService.logEvent(user, AuditLog.EventType.WEBAUTHN_CREDENTIAL_DELETED, 
                    "WebAuthn credential deleted: " + credentialName);
                    
            log.info("WebAuthn credential '{}' deleted for user {}", credentialName, user.getEmail());
        } else {
            throw new IllegalArgumentException("Credential not found or not owned by user");
        }
    }

    public boolean hasWebAuthnCredentials(User user) {
        return !credentialRepository.findByUserIdAndActiveTrue(user.getId()).isEmpty();
    }
}