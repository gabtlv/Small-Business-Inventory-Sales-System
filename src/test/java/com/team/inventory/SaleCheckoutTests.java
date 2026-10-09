package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.inventory.dto.SaleRequest;
import com.team.inventory.dto.SaleResponse;
import com.team.inventory.service.SaleException;
import com.team.inventory.service.SaleService;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Tests for story 5: recording a sale updates inventory automatically. */
@SpringBootTest
@AutoConfigureMockMvc
class SaleCheckoutTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SaleService saleService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_request_keys");
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
    }

    // ---- Recording a sale ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffRecordsSaleWithCorrectTotalsAndStockGoesDown() throws Exception {
        long pen = product("Pen", "2.50", 10);
        long mug = product("Mug", "19.99", 3);

        sell("""
            {"items":[{"productId":%d,"quantity":4},{"productId":%d,"quantity":2}]}
            """.formatted(pen, mug))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cashier").value("staff"))
            .andExpect(jsonPath("$.total").value(49.98))          // 4 x 2.50 + 2 x 19.99
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].lineTotal").value(10.00))
            .andExpect(jsonPath("$.items[1].lineTotal").value(39.98));

        assertThat(stock(pen)).isEqualTo(6);
        assertThat(stock(mug)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT total FROM sales", BigDecimal.class)).isEqualByComparingTo("49.98");
        assertThat(jdbc.queryForObject("SELECT user_id FROM sales", Long.class)).isEqualTo(userId("staff"));
        assertThat(jdbc.queryForObject(
            "SELECT unit_price FROM sale_items WHERE product_id = ?", BigDecimal.class, mug)).isEqualByComparingTo("19.99");
        assertThat(jdbc.queryForObject(
            "SELECT quantity FROM sale_items WHERE product_id = ?", Integer.class, pen)).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanAlsoRecordSales() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell(oneItem(pen, "1")).andExpect(status().isCreated());

        assertThat(stock(pen)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT user_id FROM sales", Long.class)).isEqualTo(userId("admin"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void moneyIsCalculatedExactlyWithoutRoundingErrors() throws Exception {
        long gum = product("Gum", "0.10", 100);

        // 0.10 x 3 is 0.30000000000000004 in floating point; must be exactly 0.30
        sell(oneItem(gum, "3"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(0.30));
        assertThat(jdbc.queryForObject("SELECT total FROM sales", BigDecimal.class)).isEqualByComparingTo("0.30");
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void cashierComesFromSessionNotFromRequest() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell("""
            {"userId":%d,"cashier":"admin","items":[{"productId":%d,"quantity":1}]}
            """.formatted(userId("admin"), pen))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cashier").value("staff"));

        assertThat(jdbc.queryForObject("SELECT user_id FROM sales", Long.class)).isEqualTo(userId("staff"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void pricesComeFromDatabaseNotFromRequest() throws Exception {
        long mug = product("Mug", "19.99", 3);

        sell("""
            {"total":0.01,"items":[{"productId":%d,"quantity":1,"unitPrice":0.01,"price":0.01}]}
            """.formatted(mug))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(19.99));
    }

    // ---- Rejected sales ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void emptySaleIsRejected() throws Exception {
        sell("{\"items\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("A sale needs at least one item."));
        sell("{}").andExpect(status().isBadRequest());
        assertNoSales();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "\"abc\"", "null", "99999999999999"})
    @WithMockUser(username = "staff", roles = "STAFF")
    void invalidQuantitiesAreRejected(String quantity) throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell(oneItem(pen, quantity))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        assertThat(stock(pen)).isEqualTo(10);
        assertNoSales();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void missingProductIdIsRejected() throws Exception {
        sell("{\"items\":[{\"quantity\":1}]}").andExpect(status().isBadRequest());
        assertNoSales();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void nonexistentProductIsRejected() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell("""
            {"items":[{"productId":%d,"quantity":1},{"productId":999999,"quantity":1}]}
            """.formatted(pen))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Product #999999 does not exist."));

        assertThat(stock(pen)).isEqualTo(10);
        assertNoSales();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void insufficientStockIsRejected() throws Exception {
        long mug = product("Mug", "19.99", 3);

        sell(oneItem(mug, "4"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Not enough stock for Mug: 4 requested, 3 available."));

        assertThat(stock(mug)).isEqualTo(3);
        assertNoSales();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void sellingExactlyTheRemainingStockLeavesZero() throws Exception {
        long mug = product("Mug", "19.99", 3);

        sell(oneItem(mug, "3")).andExpect(status().isCreated());

        assertThat(stock(mug)).isZero();
    }

    // ---- Same product listed more than once ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void repeatedProductCannotBypassStockCheck() throws Exception {
        long mug = product("Mug", "19.99", 5);

        // Each line alone (3) fits in stock (5), but together (6) they don't
        sell("""
            {"items":[{"productId":%d,"quantity":3},{"productId":%d,"quantity":3}]}
            """.formatted(mug, mug))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Not enough stock for Mug: 6 requested, 5 available."));

        assertThat(stock(mug)).isEqualTo(5);
        assertNoSales();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void repeatedProductIsCombinedIntoOneLine() throws Exception {
        long mug = product("Mug", "19.99", 5);

        sell("""
            {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}
            """.formatted(mug, mug))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.total").value(99.95))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].quantity").value(5));

        assertThat(stock(mug)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_items", Integer.class)).isEqualTo(1);
    }

    // ---- All or nothing ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void failureOnLaterItemRollsBackEarlierStockChanges() throws Exception {
        // Products are processed in id order, so the pen's stock is already decremented
        // in the transaction when the mug fails. The rollback must undo it.
        long pen = product("Pen", "2.50", 10);
        long mug = product("Mug", "19.99", 1);
        assertThat(pen).isLessThan(mug);

        sell("""
            {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":5}]}
            """.formatted(pen, mug))
            .andExpect(status().isConflict());

        assertThat(stock(pen)).isEqualTo(10);
        assertThat(stock(mug)).isEqualTo(1);
        assertNoSales();
    }

    // ---- Concurrency ----

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        long mug = product("Mug", "19.99", 5);
        int cashiers = 12;

        ExecutorService pool = Executors.newFixedThreadPool(cashiers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < cashiers; i++) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    saleService.recordSale("staff", new SaleRequest(List.of(new SaleRequest.Item(mug, 1))));
                    return true;
                } catch (SaleException e) {
                    return false;   // "Not enough stock"
                }
            }));
        }
        go.countDown();   // all cashiers press "Complete sale" at once

        int succeeded = 0;
        for (Future<Boolean> r : results) {
            if (r.get(30, TimeUnit.SECONDS)) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(5);
        assertThat(stock(mug)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM sale_items", Integer.class)).isEqualTo(5);
    }

    // ---- Retries with an Idempotency-Key (response lost after the sale was saved) ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void retryWithSameKeyReturnsOriginalSaleInsteadOfRecordingAgain() throws Exception {
        long mug = product("Mug", "19.99", 5);
        String body = oneItem(mug, "2");

        String first = sellWithKey(body, "retry-key-0001")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alreadyRecorded").value(false))
            .andReturn().getResponse().getContentAsString();
        long saleId = Long.parseLong(first.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // The browser never saw that response (connection dropped) and retries with the same key
        sellWithKey(body, "retry-key-0001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alreadyRecorded").value(true))
            .andExpect(jsonPath("$.id").value(saleId))
            .andExpect(jsonPath("$.total").value(39.98))
            .andExpect(jsonPath("$.items[0].name").value("Mug"))
            .andExpect(jsonPath("$.items[0].quantity").value(2));

        assertThat(stock(mug)).isEqualTo(3);   // taken out once, not twice
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void differentKeysRecordSeparateSales() throws Exception {
        long mug = product("Mug", "19.99", 5);

        sellWithKey(oneItem(mug, "1"), "sale-key-aaaa").andExpect(status().isCreated());
        sellWithKey(oneItem(mug, "1"), "sale-key-bbbb").andExpect(status().isCreated());

        assertThat(stock(mug)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void rejectedSaleDoesNotUseUpItsKey() throws Exception {
        long mug = product("Mug", "19.99", 1);

        sellWithKey(oneItem(mug, "2"), "fix-and-retry-1").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_request_keys", Integer.class)).isZero();

        // Nothing was saved, so the same key can still be used once the cashier fixes the quantity
        sellWithKey(oneItem(mug, "1"), "fix-and-retry-1")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alreadyRecorded").value(false));
        assertThat(stock(mug)).isZero();
    }

    @Test
    void keyUsedByAnotherAccountIsRejected() throws Exception {
        long mug = product("Mug", "19.99", 5);

        mvc.perform(post("/api/sales").with(user("staff").roles("STAFF")).header("Idempotency-Key", "shared-key-123")
                .contentType(MediaType.APPLICATION_JSON).content(oneItem(mug, "1")))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/sales").with(user("admin").roles("ADMIN")).header("Idempotency-Key", "shared-key-123")
                .contentType(MediaType.APPLICATION_JSON).content(oneItem(mug, "1")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("This sale request was already used by another account."));

        assertThat(stock(mug)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void malformedKeyIsRejected() throws Exception {
        long mug = product("Mug", "19.99", 5);

        sellWithKey(oneItem(mug, "1"), "bad key!").andExpect(status().isBadRequest());
        sellWithKey(oneItem(mug, "1"), "short").andExpect(status().isBadRequest());
        assertThat(stock(mug)).isEqualTo(5);
        assertNoSales();
    }

    @Test
    void concurrentRetriesWithSameKeyRecordTheSaleOnce() throws Exception {
        long mug = product("Mug", "19.99", 10);
        int attempts = 8;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    SaleResponse r = saleService.recordSale("staff",
                        new SaleRequest(List.of(new SaleRequest.Item(mug, 1))), "double-click-key");
                    return r.alreadyRecorded() ? "replayed" : "recorded";
                } catch (SaleException e) {
                    return e.getCode();   // SALE_IN_PROGRESS: outcome not known yet, safe to check again
                }
            }));
        }
        go.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> r : results) outcomes.add(r.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(outcomes).isSubsetOf("recorded", "replayed", SaleException.SALE_IN_PROGRESS);
        assertThat(outcomes).filteredOn("recorded"::equals).hasSize(1);
        assertThat(stock(mug)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    // ---- Access control ----

    @Test
    void loggedOutUsersCannotRecordSales() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell(oneItem(pen, "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sales/products")).andExpect(status().isUnauthorized());

        assertThat(stock(pen)).isEqualTo(10);
        assertNoSales();
    }

    @Test
    void checkoutPageRedirectsToLoginWhenLoggedOut() throws Exception {
        mvc.perform(get("/checkout.html").accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanOpenCheckoutAndSeeProducts() throws Exception {
        long pen = product("Pen", "2.50", 10);

        mvc.perform(get("/checkout.html")).andExpect(status().isOk());
        mvc.perform(get("/api/sales/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(pen))
            .andExpect(jsonPath("$[0].price").value(2.50))
            .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    @WithMockUser(username = "someone", roles = "MANAGER")
    void otherRolesCannotRecordSales() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell(oneItem(pen, "1")).andExpect(status().isForbidden());
        assertThat(stock(pen)).isEqualTo(10);
    }

    @Test
    @WithMockUser(username = "deleted-user", roles = "STAFF")
    void sessionForMissingAccountIsRejected() throws Exception {
        long pen = product("Pen", "2.50", 10);

        sell(oneItem(pen, "1")).andExpect(status().isUnauthorized());
        assertThat(stock(pen)).isEqualTo(10);
        assertNoSales();
    }

    // ---- Helpers ----

    private ResultActions sell(String json) throws Exception {
        return mvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions sellWithKey(String json, String key) throws Exception {
        return mvc.perform(post("/api/sales").header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String oneItem(long productId, String quantity) {
        return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + "}]}";
    }

    private long product(String name, String price, int quantity) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO products (name, price, quantity) VALUES (?, ?, ?)", new String[] {"id"});
            ps.setString(1, name);
            ps.setBigDecimal(2, new BigDecimal(price));
            ps.setInt(3, quantity);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, productId);
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private void assertNoSales() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_items", Integer.class)).isZero();
    }
}
