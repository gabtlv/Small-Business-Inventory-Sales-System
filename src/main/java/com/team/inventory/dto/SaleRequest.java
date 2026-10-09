package com.team.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Body of POST /api/sales. Example: {"items":[{"productId":1,"quantity":2},{"productId":3,"quantity":1}]}
 *
 * Only product ids and quantities come from the browser. The cashier comes from the login session
 * and prices/totals come from the database.
 */
public record SaleRequest(
    @NotEmpty(message = "A sale needs at least one item.")
    List<@NotNull(message = "Sale items can't be empty.") @Valid Item> items
) {

    /**
     * Quantity is read as a BigDecimal (not Integer) on purpose: with Integer, the JSON parser
     * would silently turn 1.5 into 1. This way 1.5 is rejected instead.
     */
    public record Item(
        @NotNull(message = "Each item needs a product.")
        Long productId,

        @NotNull(message = "Each item needs a quantity.")
        @DecimalMin(value = "1", message = "Quantity must be a whole number of at least 1.")
        @Digits(integer = 9, fraction = 0, message = "Quantity must be a whole number of at least 1.")
        BigDecimal quantity
    ) {
        public Item(Long productId, int quantity) {
            this(productId, BigDecimal.valueOf(quantity));
        }
    }
}
