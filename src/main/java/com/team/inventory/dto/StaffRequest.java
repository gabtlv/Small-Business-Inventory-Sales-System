package com.team.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/users (staff management, Sprint 3). Example: {"username":"jamie","password":"secret1"}
 * There is no role field: new accounts are always STAFF.
 */
public record StaffRequest(
    @NotBlank(message = "Username is required.")
    @Pattern(regexp = "\\s*[A-Za-z0-9._-]{3,50}\\s*",
        message = "Username must be 3-50 letters, numbers, dots, dashes or underscores.")
    String username,

    @NotBlank(message = "Password is required.")
    @Size(min = 6, max = 72, message = "Password must be 6-72 characters.")
    String password
) {
}
