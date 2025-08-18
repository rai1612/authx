package com.authx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class MfaVerificationRequest {
    
    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
    
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    
    public String getWebAuthnResponse() { return webAuthnResponse; }
    public void setWebAuthnResponse(String webAuthnResponse) { this.webAuthnResponse = webAuthnResponse; }
    
    @NotBlank(message = "MFA token is required")
    private String mfaToken;
    
    private String otpCode;
    
    private String webAuthnResponse;
}