package com.authx.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class ConfigTestController {
    
    @Value("${spring.mail.host:NOT_SET}")
    private String mailHost;
    
    @Value("${spring.mail.port:NOT_SET}")
    private String mailPort;
    
    @Value("${spring.mail.username:NOT_SET}")
    private String mailUsername;
    
    @Value("${spring.mail.password:NOT_SET}")
    private String mailPassword;
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mail_host", mailHost);
        config.put("mail_port", mailPort);
        config.put("mail_username", mailUsername);
        config.put("mail_password", mailPassword.length() > 0 ? "***SET***" : "NOT_SET");
        
        return ResponseEntity.ok(config);
    }
}