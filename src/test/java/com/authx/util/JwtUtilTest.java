package com.authx.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    
    private JwtUtil jwtUtil;
    
    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "testSecretKey123456789012345678901234567890");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200000L);
    }
    
    @Test
    void shouldGenerateValidAccessToken() {
        String username = "test@example.com";
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        claims.put("roles", java.util.List.of("USER"));
        
        String token = jwtUtil.generateToken(username, claims);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(username, jwtUtil.extractUsername(token));
        assertFalse(jwtUtil.isTokenExpired(token));
        assertTrue(jwtUtil.validateToken(token, username));
    }
    
    @Test
    void shouldGenerateValidRefreshToken() {
        String username = "test@example.com";
        
        String refreshToken = jwtUtil.generateRefreshToken(username);
        
        assertNotNull(refreshToken);
        assertTrue(jwtUtil.isRefreshToken(refreshToken));
        assertEquals(username, jwtUtil.extractUsername(refreshToken));
        assertTrue(jwtUtil.validateToken(refreshToken, username));
    }
    
    @Test
    void shouldGenerateValidMfaToken() {
        String username = "test@example.com";
        
        String mfaToken = jwtUtil.generateMfaToken(username);
        
        assertNotNull(mfaToken);
        assertTrue(jwtUtil.isMfaToken(mfaToken));
        assertEquals(username, jwtUtil.extractUsername(mfaToken));
        assertTrue(jwtUtil.validateToken(mfaToken, username));
    }
    
    @Test
    void shouldRejectInvalidToken() {
        String invalidToken = "invalid.jwt.token";
        
        assertThrows(Exception.class, () -> jwtUtil.extractUsername(invalidToken));
        assertFalse(jwtUtil.validateToken(invalidToken, "test@example.com"));
    }
    
    @Test
    void shouldRejectTokenWithWrongUsername() {
        String username = "test@example.com";
        Map<String, Object> claims = new HashMap<>();
        String token = jwtUtil.generateToken(username, claims);
        
        assertFalse(jwtUtil.validateToken(token, "wrong@example.com"));
    }
}