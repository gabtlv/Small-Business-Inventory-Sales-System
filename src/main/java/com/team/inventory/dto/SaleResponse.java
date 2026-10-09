package com.team.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What POST /api/sales returns: the saved sale, like a receipt.
 * alreadyRecorded is true when this was a retry of a sale that had already been saved
 * (same Idempotency-Key), so nothing new was recorded.
 */
public record SaleResponse(long id, String cashier, BigDecimal total, List<Line> items, boolean alreadyRecorded) {

    public record Line(long productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
