package com.authx.security;

import com.authx.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.authx.service.AuditService;
import com.authx.service.UserService;
import com.authx.model.AuditLog;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final UserService userService;
    
    public JwtAuthenticationFilter(JwtUtil jwtUtil, AuditService auditService, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.userService = userService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            final String jwt = authHeader.substring(7);
            final String userEmail = jwtUtil.extractUsername(jwt);
            
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // Handle MFA tokens for MFA-specific endpoints
                if (jwtUtil.isMfaToken(jwt)) {
                    String requestURI = request.getRequestURI();
                    // Allow MFA tokens for MFA-related endpoints and MFA verification
                    if (requestURI.contains("/mfa/") || requestURI.contains("/auth/mfa/verify")) {
                        if (jwtUtil.validateToken(jwt, userEmail)) {
                            // Create authentication for MFA token
                            UsernamePasswordAuthenticationToken authToken = 
                                new UsernamePasswordAuthenticationToken(userEmail, null, List.of());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            log.debug("MFA token authentication successful for user: {}", userEmail);
                        }
                    }
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Skip refresh tokens for regular authentication
                if (jwtUtil.isRefreshToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                
                try {
                    if (jwtUtil.validateToken(jwt, userEmail)) {
                        // Extract roles from JWT claims
                        @SuppressWarnings("unchecked")
                        List<String> roles = jwtUtil.extractClaim(jwt, claims -> 
                            (List<String>) claims.get("roles"));
                        
                        List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .toList();
                        
                        UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(userEmail, null, authorities);
                        
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        log.debug("Authentication successful for user: {}", userEmail);
                    }
                } catch (Exception tokenException) {
                    // Token is expired or invalid - log SESSION_EXPIRED
                    try {
                        var user = userService.findByEmail(userEmail).orElse(null);
                        auditService.logEventSync(user, AuditLog.EventType.SESSION_EXPIRED, 
                            "Session expired for user: " + userEmail);
                    } catch (Exception ex) {
                        auditService.logEventSync(null, AuditLog.EventType.SESSION_EXPIRED, 
                            "Session expired for unknown user");
                    }
                    log.debug("JWT token expired or invalid for user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}