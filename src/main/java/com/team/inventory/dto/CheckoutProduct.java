package com.team.inventory.dto;

import java.math.BigDecimal;

/** A product as the checkout page sees it: what it costs and how many are in stock. */
public record CheckoutProduct(long id, String name, String sku, BigDecimal price, int quantity) {
}
