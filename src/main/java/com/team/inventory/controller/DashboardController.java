package com.team.inventory.controller;

import com.team.inventory.dto.DashboardSummary;
import com.team.inventory.service.DashboardService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard summary (story 12). Admin only (SecurityConfig: /api/dashboard/**).
 *
 * GET /api/dashboard/summary
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboard.summary(LocalDate.now());
    }
}
