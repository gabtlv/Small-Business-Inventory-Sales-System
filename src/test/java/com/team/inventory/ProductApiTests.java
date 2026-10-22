package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.jayway.jsonpath.JsonPath;

/**
 * Stories 2-3 (product CRUD), 6 (low-stock alerts), 8 (categories) and 9 (search).
 * Test case IDs from the traceability matrix are noted on each group.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_request_keys");
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    // ---- TC-02 / TC-03: add, edit, delete products (stories 2, 3) ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddsProduct() throws Exception {
        create("""
            {"name":"Coffee Mug","sku":"MUG-01","price":12.50,"quantity":20,"lowStockThreshold":4}
            """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Coffee Mug"))
            .andExpect(jsonPath("$.sku").value("MUG-01"))
            .andExpect(jsonPath("$.price").value(12.50))
            .andExpect(jsonPath("$.quantity").value(20))
            .andExpect(jsonPath("$.lowStockThreshold").value(4))
            .andExpect(jsonPath("$.lowStock").value(false));

        assertThat(jdbc.queryForObject("SELECT price FROM products WHERE sku = 'MUG-01'", BigDecimal.class))
            .isEqualByComparingTo("12.50");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void thresholdDefaultsToFiveAndBlankSkuIsStoredAsNull() throws Exception {
        create("""
            {"name":"Pen","sku":"  ","price":1,"quantity":3}
            """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sku").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.lowStockThreshold").value(5))
            .andExpect(jsonPath("$.lowStock").value(true))
            .andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidProductsAreRejectedWithReadableMessage() throws Exception {
        create("""
            {"name":"","price":1,"quantity":1}""").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Name is required."));
        create("""
            {"name":"Pen","price":-1,"quantity":1}""").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Price can't be negative."));
        create("""
            {"name":"Pen","price":1.999,"quantity":1}""").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Price can have at most 2 decimal places."));
        create("""
            {"name":"Pen","price":1,"quantity":-2}""").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Quantity can't be negative."));
        create("""
            {"name":"Pen","price":1}""").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Quantity is required."));
        assertThat(count("products")).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void duplicateSkuIsRejected() throws Exception {
        create("""
            {"name":"Mug","sku":"MUG-01","price":5,"quantity":1}""").andExpect(status().isCreated());
        create("""
            {"name":"Other mug","sku":"mug-01","price":5,"quantity":1}""")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", containsString("already uses SKU")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminEditsProduct() throws Exception {
        long id = createId("""
            {"name":"Mug","sku":"MUG-01","price":5,"quantity":1}""");

        mvc.perform(put("/api/products/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Big Mug","sku":"MUG-01","price":7.25,"quantity":30,"lowStockThreshold":10}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Big Mug"))
            .andExpect(jsonPath("$.price").value(7.25))
            .andExpect(jsonPath("$.quantity").value(30));

        // Keeping its own SKU is not a duplicate
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.lowStockThreshold").value(10));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void editOrDeleteMissingProductIs404() throws Exception {
        mvc.perform(put("/api/products/999999").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"X","price":1,"quantity":1}"""))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Product not found."));
        mvc.perform(delete("/api/products/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDeletesProduct() throws Exception {
        long id = createId("""
            {"name":"Mug","price":5,"quantity":1}""");
        mvc.perform(delete("/api/products/" + id)).andExpect(status().isNoContent());
        assertThat(count("products")).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void productWithSalesCannotBeDeleted() throws Exception {
        long id = createId("""
            {"name":"Mug","price":5,"quantity":10}""");
        mvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}]}"))
            .andExpect(status().isCreated());

        mvc.perform(delete("/api/products/" + id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", containsString("sales recorded")));
        assertThat(count("products")).isEqualTo(1);
    }

    // ---- TC-04: Staff can view but not change (story 4) ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanListButNotChangeProducts() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        create("""
            {"name":"Mug","price":5,"quantity":1}""").andExpect(status().isForbidden());
        mvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/1")).andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isForbidden());
    }

    // ---- TC-06: low-stock alerts (story 6) ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void lowStockListsProductsAtOrBelowTheirThreshold() throws Exception {
        createId("""
            {"name":"Plenty","price":1,"quantity":50,"lowStockThreshold":5}""");
        createId("""
            {"name":"Exactly at threshold","price":1,"quantity":5,"lowStockThreshold":5}""");
        createId("""
            {"name":"Empty","price":1,"quantity":0,"lowStockThreshold":2}""");
        createId("""
            {"name":"Custom threshold","price":1,"quantity":15,"lowStockThreshold":20}""");

        mvc.perform(get("/api/products/low-stock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].name").value("Empty"))                 // emptiest first
            .andExpect(jsonPath("$[1].name").value("Exactly at threshold"))
            .andExpect(jsonPath("$[2].name").value("Custom threshold"))
            .andExpect(jsonPath("$[*].lowStock").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void sellingBelowThresholdTriggersAlert() throws Exception {
        long id = createId("""
            {"name":"Mug","price":5,"quantity":6,"lowStockThreshold":5}""");
        mvc.perform(get("/api/products/low-stock")).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":2}]}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/products/low-stock"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].quantity").value(4));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotSeeLowStockAlerts() throws Exception {
        mvc.perform(get("/api/products/low-stock")).andExpect(status().isForbidden());
    }

    // ---- TC-08: categories (story 8) ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreatesCategoryAndAssignsProducts() throws Exception {
        long drinkware = createCategory("Drinkware");
        long stationery = createCategory("Stationery");
        createId("{\"name\":\"Mug\",\"price\":5,\"quantity\":1,\"categoryId\":" + drinkware + "}");
        createId("{\"name\":\"Pen\",\"price\":1,\"quantity\":1,\"categoryId\":" + stationery + "}");
        createId("{\"name\":\"Loose item\",\"price\":1,\"quantity\":1}");

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(drinkware)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Mug"))
            .andExpect(jsonPath("$[0].categoryName").value("Drinkware"));

        mvc.perform(get("/api/categories"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Drinkware"))
            .andExpect(jsonPath("$[0].productCount").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void categoryRules() throws Exception {
        createCategory("Drinkware");
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"drinkware\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Category name is required."));
        create("{\"name\":\"Mug\",\"price\":5,\"quantity\":1,\"categoryId\":999999}")
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deletingCategoryKeepsItsProducts() throws Exception {
        long drinkware = createCategory("Drinkware");
        long mug = createId("{\"name\":\"Mug\",\"price\":5,\"quantity\":1,\"categoryId\":" + drinkware + "}");

        mvc.perform(delete("/api/categories/" + drinkware)).andExpect(status().isNoContent());

        mvc.perform(get("/api/products/" + mug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.nullValue()));
        assertThat(count("categories")).isZero();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanListCategories() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    // ---- TC-09: search by name or SKU (story 9) ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffSearchesByNameOrSkuCaseInsensitive() throws Exception {
        insert("Coffee Mug", "MUG-01");
        insert("Travel Mug", "MUG-02");
        insert("Pen", "PEN-01");
        insert("Notebook", null);

        mvc.perform(get("/api/products").param("q", "mug"))
            .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/products").param("q", "pen-01"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Pen"));
        mvc.perform(get("/api/products").param("q", "  NOTE "))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products").param("q", "nothing like this"))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/products"))
            .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void exactSkuMatchIsListedFirst() throws Exception {
        insert("A mug with MUG-1 in the name", "MUG-10");
        insert("Zebra mug", "MUG-1");

        mvc.perform(get("/api/products").param("q", "MUG-1"))
            .andExpect(jsonPath("$[0].sku").value("MUG-1"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        insert("50% off bin", null);
        insert("Regular", null);

        mvc.perform(get("/api/products").param("q", "%"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("50% off bin"));
        mvc.perform(get("/api/products").param("q", "_"))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void searchAndCategoryFilterCombine() throws Exception {
        jdbc.update("INSERT INTO categories (name) VALUES ('Drinkware')");
        long cat = jdbc.queryForObject("SELECT id FROM categories", Long.class);
        jdbc.update("INSERT INTO products (name, price, quantity, category_id) VALUES ('Mug', 5, 1, ?)", cat);
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Mug stand', 5, 1)");

        mvc.perform(get("/api/products").param("q", "mug").param("categoryId", String.valueOf(cat)))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Mug"));
    }

    // ---- helpers ----

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createId(String body) throws Exception {
        String response = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createCategory(String name) throws Exception {
        String response = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void insert(String name, String sku) {
        jdbc.update("INSERT INTO products (name, sku, price, quantity) VALUES (?, ?, 1.00, 10)", name, sku);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
