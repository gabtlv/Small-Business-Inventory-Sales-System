package com.team.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/users/{id}/password (reset password, Sprint 4). Example: {"password":"newsecret1"}
 */
public record PasswordResetRequest(
    @NotBlank(message = "Password is required.")
    @Size(min = 6, max = 72, message = "Password must be 6-72 characters.")
    String password
) {
}
