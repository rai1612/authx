package com.authx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private boolean mfaRequired;
    private String mfaToken;
    
    public AuthResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.mfaRequired = false;
    }
    
    public AuthResponse(String mfaToken, boolean mfaRequired) {
        this.mfaToken = mfaToken;
        this.mfaRequired = mfaRequired;
    }
}