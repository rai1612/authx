package com.authx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for sending OTP via email or SMS
 */
public record SendOtpRequest(
    @NotBlank(message = "Method is required")
    @Pattern(regexp = "^(EMAIL|SMS)$", message = "Method must be either EMAIL or SMS")
    String method
) {}
