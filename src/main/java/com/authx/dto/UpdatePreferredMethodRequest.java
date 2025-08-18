package com.authx.dto;

import com.authx.model.User;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating user's preferred MFA method
 */
public record UpdatePreferredMethodRequest(
    @NotNull(message = "Preferred method is required")
    User.MfaMethod preferredMethod
) {}
