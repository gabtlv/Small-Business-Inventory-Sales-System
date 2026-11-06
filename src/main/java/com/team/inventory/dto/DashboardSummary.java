package com.team.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/dashboard/summary (story 12): the numbers on the Admin dashboard for the last 30 days
 * (from and to are inclusive), plus how many products are low on stock right now.
 */
public record DashboardSummary(
    LocalDate from,
    LocalDate to,
    BigDecimal totalSales,
    long saleCount,
    List<SalesReport.TopProduct> topProducts,
    long lowStockCount) {
}
