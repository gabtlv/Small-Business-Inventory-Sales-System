package com.team.inventory.controller;

import com.team.inventory.dto.SalesReport;
import com.team.inventory.service.SalesReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales report (story 7). Admin only (SecurityConfig: /api/reports/**).
 *
 * GET /api/reports/sales?period=daily|weekly|monthly&from=2026-10-01&to=2026-10-31
 * All parameters are optional; dates are inclusive (yyyy-MM-dd).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final SalesReportService reports;

    public ReportController(SalesReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/sales")
    public SalesReport sales(@RequestParam(required = false) String period,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.report(SalesReportService.parsePeriod(period), from, to, LocalDate.now());
    }
}
