package com.team.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/reports/sales (story 7): sales totals for a date range, grouped by day, week or month.
 * Every period in the range gets a row, including ones with no sales (so charts have no gaps).
 */
public record SalesReport(
    String period,
    LocalDate from,
    LocalDate to,
    BigDecimal totalRevenue,
    long saleCount,
    long itemsSold,
    BigDecimal averageSale,
    List<Row> rows,
    List<TopProduct> topProducts) {

    /** One day / week / month. start and end are clipped to the requested range. */
    public record Row(String label, LocalDate start, LocalDate end, long saleCount, long itemsSold, BigDecimal revenue) {
    }

    /** Best sellers in the range, by quantity sold. */
    public record TopProduct(long productId, String name, long quantitySold, BigDecimal revenue) {
    }
}
