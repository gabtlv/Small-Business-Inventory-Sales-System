package com.team.inventory.dto;

import java.math.BigDecimal;

/**
 * A product as the API returns it (stories 2, 3, 6, 8, 9).
 * lowStock is true when quantity is at or below the product's lowStockThreshold (story 6).
 */
public record ProductDto(
    long id,
    String name,
    String sku,
    BigDecimal price,
    int quantity,
    Long categoryId,
    String categoryName,
    int lowStockThreshold,
    boolean lowStock) {
}
