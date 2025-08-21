package com.authx.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ULTRA-SIMPLE health controller for Railway
 * Multiple endpoints to ensure Railway can find at least one
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public String root() {
        return "OK";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
    
    @GetMapping("/healthz")
    public String healthz() {
        return "OK";
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping("/status")
    public String status() {
        return "UP";
    }
    
    // Also provide standard HTTP status responses
    @GetMapping("/api/health")
    public ResponseEntity<String> apiHealth() {
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}
