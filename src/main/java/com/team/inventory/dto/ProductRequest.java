package com.team.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of POST /api/products and PUT /api/products/{id} (stories 2, 3). */
public record ProductRequest(
    @NotBlank(message = "Name is required.")
    @Size(max = 150, message = "Name can be at most 150 characters.")
    String name,

    @Size(max = 50, message = "SKU can be at most 50 characters.")
    String sku,

    @NotNull(message = "Price is required.")
    @DecimalMin(value = "0.00", message = "Price can't be negative.")
    @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places.")
    BigDecimal price,

    @NotNull(message = "Quantity is required.")
    @Min(value = 0, message = "Quantity can't be negative.")
    Integer quantity,

    /** Optional (story 8). */
    Long categoryId,

    /** Optional, defaults to 5 (story 6). */
    @Min(value = 0, message = "Low stock threshold can't be negative.")
    Integer lowStockThreshold) {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    /** Trimmed SKU, or null when left blank (SKU is optional, but unique when given). */
    public String cleanSku() {
        return sku == null || sku.isBlank() ? null : sku.trim();
    }

    public int thresholdOrDefault() {
        return lowStockThreshold == null ? DEFAULT_LOW_STOCK_THRESHOLD : lowStockThreshold;
    }
}
