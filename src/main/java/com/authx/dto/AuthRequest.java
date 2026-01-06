package com.authx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AuthRequest {

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @NotBlank(message = "Email or Username is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}