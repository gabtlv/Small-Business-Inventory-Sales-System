package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.inventory.dto.SalesReport;
import com.team.inventory.service.SalesReportService;
import com.team.inventory.service.SalesReportService.Period;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Story 7 (TC-07): sales report by day, week and month with a date range. */
@SpringBootTest
@AutoConfigureMockMvc
class SalesReportTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SalesReportService reports;

    private long mug;
    private long pen;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Mug', 10.00, 100)");
        mug = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Mug'", Long.class);
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 100)");
        pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);

        // Mon Oct 12 2026 .. Sun Oct 25 2026 is two full weeks
        sale(LocalDateTime.of(2026, 10, 12, 9, 0), mug, 2);            // Mon wk1: 20.00
        sale(LocalDateTime.of(2026, 10, 12, 17, 30), pen, 4);          // Mon wk1: 10.00
        sale(LocalDateTime.of(2026, 10, 18, 23, 59), mug, 1);          // Sun wk1: 10.00
        sale(LocalDateTime.of(2026, 10, 19, 0, 0), pen, 2);            // Mon wk2:  5.00
        sale(LocalDateTime.of(2026, 11, 2, 12, 0), mug, 3);            // November: 30.00
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void dailyReportHasOneRowPerDayIncludingEmptyDays() {
        SalesReport r = reports.report(Period.DAILY, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19), today());

        assertThat(r.rows()).hasSize(8);
        assertThat(r.rows().get(0).saleCount()).isEqualTo(2);
        assertThat(r.rows().get(0).revenue()).isEqualByComparingTo("30.00");
        assertThat(r.rows().get(0).itemsSold()).isEqualTo(6);
        assertThat(r.rows().get(1).saleCount()).isZero();                    // Tue, no sales
        assertThat(r.rows().get(6).revenue()).isEqualByComparingTo("10.00"); // Sun 23:59 counts as Sunday
        assertThat(r.rows().get(7).revenue()).isEqualByComparingTo("5.00");  // Mon 00:00 counts as Monday

        assertThat(r.saleCount()).isEqualTo(4);
        assertThat(r.totalRevenue()).isEqualByComparingTo("45.00");
        assertThat(r.averageSale()).isEqualByComparingTo("11.25");
    }

    @Test
    void weeklyReportGroupsMondayToSunday() {
        SalesReport r = reports.report(Period.WEEKLY, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 25), today());

        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).start()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(r.rows().get(0).end()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(r.rows().get(0).revenue()).isEqualByComparingTo("40.00");
        assertThat(r.rows().get(1).revenue()).isEqualByComparingTo("5.00");
    }

    @Test
    void monthlyReportGroupsByCalendarMonth() {
        SalesReport r = reports.report(Period.MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), today());

        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).label()).isEqualTo("October 2026");
        assertThat(r.rows().get(0).revenue()).isEqualByComparingTo("45.00");
        assertThat(r.rows().get(1).revenue()).isEqualByComparingTo("30.00");
        assertThat(r.totalRevenue()).isEqualByComparingTo("75.00");
    }

    @Test
    void dateRangeFilterExcludesSalesOutsideIt() {
        SalesReport r = reports.report(Period.DAILY, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 18), today());
        assertThat(r.saleCount()).isEqualTo(1);
        assertThat(r.totalRevenue()).isEqualByComparingTo("10.00");
    }

    @Test
    void partialWeeksAreClippedToTheRange() {
        // Wed Oct 14 .. Tue Oct 20: two week buckets, both partial
        SalesReport r = reports.report(Period.WEEKLY, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20), today());
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).start()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(r.rows().get(1).end()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(r.totalRevenue()).isEqualByComparingTo("15.00");   // Mon 12 sales excluded
    }

    @Test
    void topProductsRankedByQuantity() {
        SalesReport r = reports.report(Period.MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), today());
        assertThat(r.topProducts()).hasSize(2);
        assertThat(r.topProducts().get(0).name()).isEqualTo("Mug");      // 6 mugs vs 6 pens: tie -> higher revenue first
        assertThat(r.topProducts().get(0).quantitySold()).isEqualTo(6);
    }

    @Test
    void defaultsToLastSevenDays() {
        SalesReport r = reports.report(Period.DAILY, null, null, LocalDate.of(2026, 10, 18));
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(r.rows()).hasSize(7);
    }

    @Test
    void emptyRangeReturnsZeros() {
        SalesReport r = reports.report(Period.DAILY, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3), today());
        assertThat(r.saleCount()).isZero();
        assertThat(r.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.averageSale()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.topProducts()).isEmpty();
    }

    // ---- through the API ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminGetsReportOverApi() throws Exception {
        mvc.perform(get("/api/reports/sales").param("period", "weekly")
                .param("from", "2026-10-12").param("to", "2026-10-25"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("weekly"))
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.totalRevenue").value(45.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void badReportParametersGiveReadableErrors() throws Exception {
        mvc.perform(get("/api/reports/sales").param("period", "yearly"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Period must be daily, weekly or monthly."));
        mvc.perform(get("/api/reports/sales").param("from", "2026-10-20").param("to", "2026-10-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The start date must be on or before the end date."));
        mvc.perform(get("/api/reports/sales").param("from", "not-a-date"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotSeeReports() throws Exception {
        mvc.perform(get("/api/reports/sales")).andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private static LocalDate today() {
        return LocalDate.of(2026, 12, 31);
    }

    private void sale(LocalDateTime when, long productId, int quantity) {
        BigDecimal price = jdbc.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class, productId);
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
        long admin = jdbc.queryForObject("SELECT id FROM users WHERE username = 'admin'", Long.class);
        jdbc.update("INSERT INTO sales (user_id, total, created_at) VALUES (?, ?, ?)", admin, total, Timestamp.valueOf(when));
        long saleId = jdbc.queryForObject("SELECT MAX(id) FROM sales", Long.class);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
            saleId, productId, quantity, price);
    }
}
