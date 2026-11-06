package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
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

/** Story 10 (TC-10): Admin exports the sales report as a CSV file. */
@SpringBootTest
@AutoConfigureMockMvc
class ReportExportTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Mug', 10.00, 100)");
        long mug = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Mug'", Long.class);
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 100)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);

        sale(LocalDateTime.of(2026, 10, 12, 9, 0), mug, 2);     // 20.00
        sale(LocalDateTime.of(2026, 10, 12, 17, 30), pen, 4);   // 10.00
        sale(LocalDateTime.of(2026, 10, 14, 11, 0), mug, 1);    // 10.00
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exportMatchesTheReportOnScreen() throws Exception {
        String csv = mvc.perform(get("/api/reports/sales/export").param("period", "daily")
                .param("from", "2026-10-12").param("to", "2026-10-14"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sales-report-2026-10-12-to-2026-10-14.csv\""))
            .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(5);   // header + 3 days + total
        assertThat(lines[0]).isEqualTo("Period,Start,End,Sales,Items sold,Revenue");
        // Labels contain a comma, so they are quoted
        assertThat(lines[1]).isEqualTo("\"Mon Oct 12, 2026\",2026-10-12,2026-10-12,2,6,30.00");
        assertThat(lines[2]).isEqualTo("\"Tue Oct 13, 2026\",2026-10-13,2026-10-13,0,0,0.00");   // empty day still listed
        assertThat(lines[3]).isEqualTo("\"Wed Oct 14, 2026\",2026-10-14,2026-10-14,1,1,10.00");
        assertThat(lines[4]).isEqualTo("Total,2026-10-12,2026-10-14,3,7,40.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exportOfAnEmptyRangeHasOnlyHeaderAndTotal() throws Exception {
        String csv = mvc.perform(get("/api/reports/sales/export").param("period", "monthly")
                .param("from", "2025-01-01").param("to", "2025-01-31"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);   // header + January + total
        assertThat(lines[2]).isEqualTo("Total,2025-01-01,2025-01-31,0,0,0.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exportRejectsBadParametersWithAReadableError() throws Exception {
        mvc.perform(get("/api/reports/sales/export").param("period", "yearly"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Period must be daily, weekly or monthly."));
        mvc.perform(get("/api/reports/sales/export").param("from", "2026-10-20").param("to", "2026-10-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The start date must be on or before the end date."));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotExport() throws Exception {
        mvc.perform(get("/api/reports/sales/export")).andExpect(status().isForbidden());
    }

    @Test
    void loggedOutGets401() throws Exception {
        mvc.perform(get("/api/reports/sales/export")).andExpect(status().isUnauthorized());
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
