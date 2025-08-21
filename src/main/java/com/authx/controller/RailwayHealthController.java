package com.authx.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Railway-specific health check controller
 * Returns simple 200 OK response for Railway health checks
 */
@RestController
@Profile("railway")
public class RailwayHealthController {

    /**
     * Simple health endpoint for Railway
     * Returns 200 OK without any dependencies
     */
    @GetMapping("/health")
    public ResponseEntity<String> railwayHealth() {
        return ResponseEntity.ok("OK");
    }
    
    /**
     * Alternative health endpoint at root level
     */
    @GetMapping("/")
    public ResponseEntity<String> rootHealth() {
        return ResponseEntity.ok("AuthX MFA System - Railway Deployment");
    }
    
    /**
     * Railway health check endpoint (simple response)
     */
    @GetMapping("/api/v1/health")
    public ResponseEntity<String> apiHealth() {
        return ResponseEntity.ok("UP");
    }
}
