package com.team.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/categories (story 8). */
public record CategoryRequest(
    @NotBlank(message = "Category name is required.")
    @Size(max = 100, message = "Category name can be at most 100 characters.")
    String name) {
}
