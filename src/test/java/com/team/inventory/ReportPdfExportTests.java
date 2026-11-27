package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Story 10 (TC-10): Admin exports the sales report as a PDF file.
 *
 * The PDF's text isn't compressed, so each value drawn on the page appears in the file as
 * "(text) Tj". That lets these tests check the numbers without a PDF library.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportPdfExportTests {

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
    void pdfHasTheSameNumbersAsTheReportOnScreen() throws Exception {
        byte[] bytes = mvc.perform(get("/api/reports/sales/export/pdf").param("period", "daily")
                .param("from", "2026-10-12").param("to", "2026-10-14"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sales-report-2026-10-12-to-2026-10-14.pdf\""))
            .andReturn().getResponse().getContentAsByteArray();

        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF-").endsWith("%%EOF\n");
        assertThat(pdf).contains("/Count 1 ");                               // one page
        assertThat(pdf).contains("(Sales report) Tj", "(Daily totals, 2026-10-12 to 2026-10-14");
        assertThat(pdf).contains("(Mon Oct 12, 2026) Tj", "(Tue Oct 13, 2026) Tj", "(Wed Oct 14, 2026) Tj");
        assertThat(pdf).contains("($30.00) Tj", "($0.00) Tj");               // day rows (empty day still listed)
        assertThat(pdf).contains("(Total) Tj", "($40.00) Tj", "(7) Tj");     // total revenue and items
        assertThat(pdf).contains("($13.33) Tj");                             // average sale: 40 / 3
        assertThat(pdf).contains("(Mug) Tj", "(Pen) Tj");                    // top products
        assertThat(pdf).contains("(Page 1 of 1) Tj");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void longReportsContinueOnMorePages() throws Exception {
        byte[] bytes = mvc.perform(get("/api/reports/sales/export/pdf").param("period", "daily")
                .param("from", "2026-07-01").param("to", "2026-10-31"))   // 123 days
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        // 123 rows don't fit on one page; the table carries on with its header repeated
        assertThat(pdf).doesNotContain("(Page 1 of 1) Tj");
        assertThat(pdf).contains("(Page 2 of ");
        assertThat(pdf.split("\\(Period\\) Tj", -1).length - 1).isGreaterThan(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void emptyRangeSaysThereWereNoSales() throws Exception {
        byte[] bytes = mvc.perform(get("/api/reports/sales/export/pdf").param("period", "monthly")
                .param("from", "2025-01-01").param("to", "2025-01-31"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(pdf).contains("(Monthly totals, 2025-01-01 to 2025-01-31", "(No sales in this range.) Tj");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void pdfRejectsBadParametersWithAReadableError() throws Exception {
        mvc.perform(get("/api/reports/sales/export/pdf").param("period", "yearly"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Period must be daily, weekly or monthly."));
        mvc.perform(get("/api/reports/sales/export/pdf").param("from", "2026-10-20").param("to", "2026-10-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("The start date must be on or before the end date."));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotExportPdf() throws Exception {
        mvc.perform(get("/api/reports/sales/export/pdf")).andExpect(status().isForbidden());
    }

    @Test
    void loggedOutGets401() throws Exception {
        mvc.perform(get("/api/reports/sales/export/pdf")).andExpect(status().isUnauthorized());
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
