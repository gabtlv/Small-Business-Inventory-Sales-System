package com.team.inventory.controller;

import com.team.inventory.dto.SalesReport;
import com.team.inventory.service.SalesReportService;
import com.team.inventory.service.SalesReportCsv;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales report (story 7). Admin only (SecurityConfig: /api/reports/**).
 *
 * GET /api/reports/sales?period=daily|weekly|monthly&from=2026-10-01&to=2026-10-31
 * All parameters are optional; dates are inclusive (yyyy-MM-dd).
 *
 * GET /api/reports/sales/export takes the same parameters and returns the report as a CSV file
 * download (story 10).
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

    @GetMapping("/sales/export")
    public ResponseEntity<String> exportSales(@RequestParam(required = false) String period,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SalesReport report = reports.report(SalesReportService.parsePeriod(period), from, to, LocalDate.now());
        String filename = "sales-report-" + report.from() + "-to-" + report.to() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(SalesReportCsv.toCsv(report));
    }
}
