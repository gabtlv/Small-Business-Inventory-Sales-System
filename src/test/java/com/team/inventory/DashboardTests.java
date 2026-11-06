package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.inventory.dto.DashboardSummary;
import com.team.inventory.service.DashboardService;
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

/** Story 12 (TC-12): dashboard shows total sales, top products and the low-stock count. */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);   // window is Oct 2 .. Oct 31

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DashboardService dashboard;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Mug', 10.00, 100)");
        long mug = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Mug'", Long.class);
        // Pen has 3 left and the default alert level is 5, so it counts as low stock
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 3)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);

        sale(LocalDateTime.of(2026, 10, 12, 9, 0), mug, 2);      // 20.00, inside the window
        sale(LocalDateTime.of(2026, 10, 31, 23, 59), pen, 4);    // 10.00, last day counts
        sale(LocalDateTime.of(2026, 10, 2, 0, 0), mug, 1);       // 10.00, first day counts
        sale(LocalDateTime.of(2026, 10, 1, 23, 59), mug, 50);    // 500.00, too old
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void summaryCoversTheLast30DaysOnly() {
        DashboardSummary s = dashboard.summary(TODAY);

        assertThat(s.from()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(s.to()).isEqualTo(TODAY);
        assertThat(s.saleCount()).isEqualTo(3);
        assertThat(s.totalSales()).isEqualByComparingTo("40.00");
    }

    @Test
    void summaryListsTopProductsByQuantity() {
        DashboardSummary s = dashboard.summary(TODAY);

        assertThat(s.topProducts()).hasSize(2);
        assertThat(s.topProducts().get(0).name()).isEqualTo("Pen");    // 4 pens vs 3 mugs
        assertThat(s.topProducts().get(0).quantitySold()).isEqualTo(4);
        assertThat(s.topProducts().get(1).name()).isEqualTo("Mug");
    }

    @Test
    void summaryCountsLowStockProducts() {
        assertThat(dashboard.summary(TODAY).lowStockCount()).isEqualTo(1);   // only the Pen

        jdbc.update("UPDATE products SET quantity = 0 WHERE name = 'Mug'");
        assertThat(dashboard.summary(TODAY).lowStockCount()).isEqualTo(2);
    }

    @Test
    void summaryWithNoSalesIsAllZeros() {
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");

        DashboardSummary s = dashboard.summary(TODAY);
        assertThat(s.saleCount()).isZero();
        assertThat(s.totalSales()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.topProducts()).isEmpty();
    }

    // ---- through the API ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminGetsSummaryOverApi() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSales").exists())
            .andExpect(jsonPath("$.saleCount").exists())
            .andExpect(jsonPath("$.topProducts").isArray())
            .andExpect(jsonPath("$.lowStockCount").value(1));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotSeeTheDashboard() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard.html")).andExpect(status().isForbidden());
    }

    @Test
    void loggedOutGets401() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
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
