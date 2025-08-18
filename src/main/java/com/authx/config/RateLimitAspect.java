package com.authx.config;

import com.authx.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RateLimit {
        RateLimitService.RateLimitType type();
        int tokens() default 1;
        String keyExpression() default ""; // SpEL expression for custom key
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identifier = getIdentifier(rateLimit);
        
        if (!rateLimitService.isAllowed(identifier, rateLimit.type(), rateLimit.tokens())) {
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(identifier, rateLimit.type());
            
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", String.valueOf(info.capacity()))
                    .header("X-RateLimit-Remaining", String.valueOf(info.remainingTokens()))
                    .header("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + info.timeUntilRefill().toMillis()))
                    .body(Map.of(
                            "error", "Rate limit exceeded",
                            "message", "Too many requests. Please try again later.",
                            "retryAfter", info.timeUntilRefill().getSeconds()
                    ));
        }
        
        return joinPoint.proceed();
    }

    private String getIdentifier(RateLimit rateLimit) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        // Use custom key expression if provided
        if (!rateLimit.keyExpression().isEmpty()) {
            // For now, simple implementation - could use SpEL parser for more complex expressions
            return evaluateKeyExpression(rateLimit.keyExpression(), request);
        }
        
        // Default to IP address
        return getClientIpAddress(request);
    }

    private String evaluateKeyExpression(String expression, HttpServletRequest request) {
        // Simple implementation - could be enhanced with SpEL
        return switch (expression) {
            case "ip" -> getClientIpAddress(request);
            case "user-agent" -> request.getHeader("User-Agent");
            case "session" -> request.getSession().getId();
            default -> getClientIpAddress(request);
        };
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}