package com.authx.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();

    @Value("${rate-limit.login.capacity:50}")
    private int loginCapacity;

    @Value("${rate-limit.login.refill-tokens:50}")
    private int loginRefillTokens;

    @Value("${rate-limit.login.refill-period:60}")
    private int loginRefillPeriod;

    @Value("${rate-limit.otp.capacity:50}")
    private int otpCapacity;

    @Value("${rate-limit.otp.refill-tokens:1}")
    private int otpRefillTokens;

    @Value("${rate-limit.otp.refill-period:300}")
    private int otpRefillPeriod;

    // MFA Verification Rate Limiting
    @Value("${rate-limit.mfa.verification.capacity:50}")
    private int mfaVerificationCapacity;

    @Value("${rate-limit.mfa.verification.refill-tokens:1}")
    private int mfaVerificationRefillTokens;

    @Value("${rate-limit.mfa.verification.refill-period:600}")
    private int mfaVerificationRefillPeriod;

    // MFA Setup Rate Limiting
    @Value("${rate-limit.mfa.setup.capacity:50}")
    private int mfaSetupCapacity;

    @Value("${rate-limit.mfa.setup.refill-tokens:1}")
    private int mfaSetupRefillTokens;

    @Value("${rate-limit.mfa.setup.refill-period:900}")
    private int mfaSetupRefillPeriod;

    // MFA Preferred Method Rate Limiting
    @Value("${rate-limit.mfa.preferred-method.capacity:50}")
    private int mfaPreferredCapacity;

    @Value("${rate-limit.mfa.preferred-method.refill-tokens:2}")
    private int mfaPreferredRefillTokens;

    @Value("${rate-limit.mfa.preferred-method.refill-period:3600}")
    private int mfaPreferredRefillPeriod;

    // Password Reset Rate Limiting
    @Value("${rate-limit.password-reset.capacity:50}")
    private int passwordResetCapacity;

    @Value("${rate-limit.password-reset.refill-tokens:1}")
    private int passwordResetRefillTokens;

    @Value("${rate-limit.password-reset.refill-period:900}")
    private int passwordResetRefillPeriod;

    public enum RateLimitType {
        LOGIN, OTP, MFA, MFA_VERIFICATION, MFA_SETUP, MFA_PREFERRED_METHOD, PASSWORD_RESET, API
    }

    public boolean isAllowed(String identifier, RateLimitType type) {
        String key = type.name().toLowerCase() + ":" + identifier;
        Bucket bucket = getBucket(key, type);
        
        boolean allowed = bucket.tryConsume(1);
        
        if (!allowed) {
            log.warn("Rate limit exceeded for {}: {}", type, identifier);
            // Store violation in Redis for monitoring
            String violationKey = "rate_limit_violation:" + key;
            redisTemplate.opsForValue().increment(violationKey);
            redisTemplate.expire(violationKey, Duration.ofHours(1));
        }
        
        return allowed;
    }

    public boolean isAllowed(String identifier, RateLimitType type, int tokens) {
        String key = type.name().toLowerCase() + ":" + identifier;
        Bucket bucket = getBucket(key, type);
        
        boolean allowed = bucket.tryConsume(tokens);
        
        if (!allowed) {
            log.warn("Rate limit exceeded for {}: {} (requested {} tokens)", type, identifier, tokens);
            String violationKey = "rate_limit_violation:" + key;
            redisTemplate.opsForValue().increment(violationKey);
            redisTemplate.expire(violationKey, Duration.ofHours(1));
        }
        
        return allowed;
    }

    public long getRemainingTokens(String identifier, RateLimitType type) {
        String key = type.name().toLowerCase() + ":" + identifier;
        Bucket bucket = getBucket(key, type);
        return bucket.getAvailableTokens();
    }

    public Duration getTimeUntilRefill(String identifier, RateLimitType type) {
        String key = type.name().toLowerCase() + ":" + identifier;
        Bucket bucket = getBucket(key, type);
        
        // Estimate time until next token is available
        long tokens = bucket.getAvailableTokens();
        if (tokens > 0) {
            return Duration.ZERO;
        }
        
        // Calculate based on refill rate
        return switch (type) {
            case LOGIN -> Duration.ofSeconds(loginRefillPeriod);
            case OTP, MFA -> Duration.ofSeconds(otpRefillPeriod);
            case MFA_VERIFICATION -> Duration.ofSeconds(mfaVerificationRefillPeriod);
            case MFA_SETUP -> Duration.ofSeconds(mfaSetupRefillPeriod);
            case MFA_PREFERRED_METHOD -> Duration.ofSeconds(mfaPreferredRefillPeriod);
            case PASSWORD_RESET -> Duration.ofSeconds(passwordResetRefillPeriod);
            case API -> Duration.ofSeconds(60); // Default 1 minute
        };
    }

    private Bucket getBucket(String key, RateLimitType type) {
        return cache.computeIfAbsent(key, k -> createBucket(type));
    }

    private Bucket createBucket(RateLimitType type) {
        return switch (type) {
            case LOGIN -> Bucket.builder()
                    .addLimit(Bandwidth.classic(loginCapacity, 
                            Refill.intervally(loginRefillTokens, Duration.ofSeconds(loginRefillPeriod))))
                    .build();
                    
            case OTP, MFA -> Bucket.builder()
                    .addLimit(Bandwidth.classic(otpCapacity, 
                            Refill.intervally(otpRefillTokens, Duration.ofSeconds(otpRefillPeriod))))
                    .build();
                    
            case MFA_VERIFICATION -> Bucket.builder()
                    .addLimit(Bandwidth.classic(mfaVerificationCapacity, 
                            Refill.intervally(mfaVerificationRefillTokens, Duration.ofSeconds(mfaVerificationRefillPeriod))))
                    .build();
                    
            case MFA_SETUP -> Bucket.builder()
                    .addLimit(Bandwidth.classic(mfaSetupCapacity, 
                            Refill.intervally(mfaSetupRefillTokens, Duration.ofSeconds(mfaSetupRefillPeriod))))
                    .build();
                    
            case MFA_PREFERRED_METHOD -> Bucket.builder()
                    .addLimit(Bandwidth.classic(mfaPreferredCapacity, 
                            Refill.intervally(mfaPreferredRefillTokens, Duration.ofSeconds(mfaPreferredRefillPeriod))))
                    .build();
                    
            case PASSWORD_RESET -> Bucket.builder()
                    .addLimit(Bandwidth.classic(passwordResetCapacity, 
                            Refill.intervally(passwordResetRefillTokens, Duration.ofSeconds(passwordResetRefillPeriod))))
                    .build();
                    
            case API -> Bucket.builder()
                    .addLimit(Bandwidth.classic(100, 
                            Refill.intervally(10, Duration.ofMinutes(1))))
                    .build();
        };
    }

    public void resetLimit(String identifier, RateLimitType type) {
        String key = type.name().toLowerCase() + ":" + identifier;
        cache.remove(key);
        log.info("Rate limit reset for {}: {}", type, identifier);
    }
    
    public void clearAllLimits() {
        cache.clear();
        log.info("All rate limit buckets cleared from cache");
    }
    
    public void clearLimitsByType(RateLimitType type) {
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(type.name().toLowerCase() + ":"));
        log.info("All {} rate limit buckets cleared from cache", type);
    }

    public RateLimitInfo getRateLimitInfo(String identifier, RateLimitType type) {
        String key = type.name().toLowerCase() + ":" + identifier;
        Bucket bucket = getBucket(key, type);
        
        return new RateLimitInfo(
                bucket.getAvailableTokens(),
                getCapacity(type),
                getTimeUntilRefill(identifier, type)
        );
    }

    private long getCapacity(RateLimitType type) {
        return switch (type) {
            case LOGIN -> loginCapacity;
            case OTP, MFA -> otpCapacity;
            case MFA_VERIFICATION -> mfaVerificationCapacity;
            case MFA_SETUP -> mfaSetupCapacity;
            case MFA_PREFERRED_METHOD -> mfaPreferredCapacity;
            case PASSWORD_RESET -> passwordResetCapacity;
            case API -> 100;
        };
    }

    public record RateLimitInfo(
            long remainingTokens,
            long capacity,
            Duration timeUntilRefill
    ) {}
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready - clearing rate limit cache to ensure fresh configuration");
        clearAllLimits();
        log.info("Rate limit configuration:");
        log.info("  LOGIN: capacity={}, refill={} tokens per {} seconds", 
                loginCapacity, loginRefillTokens, loginRefillPeriod);
        log.info("  OTP: capacity={}, refill={} tokens per {} seconds", 
                otpCapacity, otpRefillTokens, otpRefillPeriod);
        log.info("  MFA_VERIFICATION: capacity={}, refill={} tokens per {} seconds", 
                mfaVerificationCapacity, mfaVerificationRefillTokens, mfaVerificationRefillPeriod);
        log.info("  MFA_SETUP: capacity={}, refill={} tokens per {} seconds", 
                mfaSetupCapacity, mfaSetupRefillTokens, mfaSetupRefillPeriod);
        log.info("  MFA_PREFERRED_METHOD: capacity={}, refill={} tokens per {} seconds", 
                mfaPreferredCapacity, mfaPreferredRefillTokens, mfaPreferredRefillPeriod);
    }
}