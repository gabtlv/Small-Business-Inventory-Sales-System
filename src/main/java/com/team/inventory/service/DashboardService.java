package com.team.inventory.service;

import com.team.inventory.dto.DashboardSummary;
import com.team.inventory.dto.SalesReport;
import com.team.inventory.repository.ProductRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Story 12: dashboard summary (total sales, top products, low stock count).
 *
 * Reuses the sales report (story 7) for the sales numbers and the low-stock query (story 6),
 * so the dashboard always agrees with those pages.
 */
@Service
public class DashboardService {

    /** The dashboard shows the last 30 days, including today. */
    static final int DAYS = 30;

    private final SalesReportService reports;
    private final ProductRepository products;

    public DashboardService(SalesReportService reports, ProductRepository products) {
        this.reports = reports;
        this.products = products;
    }

    public DashboardSummary summary(LocalDate today) {
        SalesReport sales = reports.report(SalesReportService.Period.DAILY, today.minusDays(DAYS - 1L), today, today);
        return new DashboardSummary(sales.from(), sales.to(), sales.totalRevenue(), sales.saleCount(),
            sales.topProducts(), products.findLowStock().size());
    }
}
