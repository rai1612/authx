package com.authx.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple health controller for Railway
 * Provides health endpoints at root level (outside of /api/v1 context)
 */
@RestController
public class HealthController {

    /**
     * Root health endpoint that Railway can access directly
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
    
    /**
     * Alternative root endpoint
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ResponseEntity.ok("AuthX MFA System - Railway");
    }
}
