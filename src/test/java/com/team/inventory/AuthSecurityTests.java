package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for story 1 (login/logout), story 4 (Staff has limited access) and the role rules in
 * SecurityConfig. If someone reorders or edits a rule and opens an Admin-only endpoint to Staff
 * (or to logged-out users), one of these fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    // ---- Story 1: login ----

    @Test
    void adminCanLogInWithSeededAccount() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("admin123"))
            .andExpect(authenticated().withRoles("ADMIN"))
            .andExpect(redirectedUrl("/index.html"));
    }

    @Test
    void staffCanLogInWithSeededAccount() throws Exception {
        mvc.perform(formLogin("/login").user("staff").password("staff123"))
            .andExpect(authenticated().withRoles("STAFF"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("wrong"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login.html?error"));
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user("nobody").password("admin123"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login.html?error"));
    }

    // ---- Story 1: logout ----

    @Test
    void logoutEndsTheSession() throws Exception {
        // A real login, so there is a real session to end
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("staff").password("staff123"))
            .andExpect(authenticated())
            .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").session(session))
            .andExpect(redirectedUrl("/login.html?logout"));

        // The old session is gone, so the same cookie no longer gets you in
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }

    // ---- GET /api/me ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void meReturnsCurrentUserAndRole() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void meReturnsStaffRoleForStaff() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("staff"))
            .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void meNeverReturnsThePasswordOrHash() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("admin").password("admin123"))
            .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ---- Passwords are stored as BCrypt hashes, never plain text ----

    @ParameterizedTest
    @CsvSource({"admin, admin123", "staff, staff123"})
    void passwordsAreStoredAsBcryptHashes(String username, String plainPassword) {
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, username);

        assertThat(stored).isNotEqualTo(plainPassword);
        assertThat(stored).doesNotContain(plainPassword);
        assertThat(stored).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");   // BCrypt format: $2a$10$ + 53 characters
        assertThat(encoder.matches(plainPassword, stored)).isTrue();
        assertThat(encoder.matches("wrong-password", stored)).isFalse();
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        // BCrypt adds a random salt, so two users with the same password don't have the same hash
        String first = encoder.encode("secret1");
        String second = encoder.encode("secret1");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("secret1", first)).isTrue();
        assertThat(encoder.matches("secret1", second)).isTrue();
    }

    // ---- Logged out: APIs get 401, pages redirect to the login page ----

    @ParameterizedTest
    @CsvSource({
        "GET,    /api/me",
        "GET,    /api/products",
        "GET,    /api/products/1",
        "GET,    /api/products/low-stock",
        "POST,   /api/products",
        "PUT,    /api/products/1",
        "DELETE, /api/products/1",
        "GET,    /api/categories",
        "POST,   /api/categories",
        "DELETE, /api/categories/1",
        "GET,    /api/sales/products",
        "POST,   /api/sales",
        "GET,    /api/reports/sales",
        "GET,    /api/reports/sales/export",
        "GET,    /api/dashboard/summary",
        "GET,    /api/users",
        "POST,   /api/users",
        "DELETE, /api/users/1"
    })
    void apiReturns401WhenLoggedOut(String method, String url) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), url))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/products.html", "/checkout.html", "/product-form.html",
        "/reports.html", "/staff.html", "/dashboard.html"})
    void pagesRedirectToLoginWhenLoggedOut(String page) throws Exception {
        // A browser asks for HTML, so it gets redirected to the login page
        mvc.perform(get(page).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login.html", "/css/styles.css", "/js/app.js"})
    void loginPageAndItsFilesArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    // ---- Story 4: Staff gets 403 on everything Admin-only ----

    @ParameterizedTest
    @CsvSource({
        "GET,    /api/products/low-stock",
        "POST,   /api/products",
        "PUT,    /api/products/1",
        "DELETE, /api/products/1",
        "POST,   /api/categories",
        "DELETE, /api/categories/1",
        "GET,    /api/reports/sales",
        "GET,    /api/reports/sales/export",
        "GET,    /api/dashboard/summary",
        "GET,    /api/users",
        "POST,   /api/users",
        "DELETE, /api/users/1"
    })
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffGets403OnAdminOnlyEndpoints(String method, String url) throws Exception {
        // Blocked by SecurityConfig before the controller runs, so nothing is read or changed
        mvc.perform(request(HttpMethod.valueOf(method), url))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/product-form.html", "/reports.html", "/staff.html", "/dashboard.html"})
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotOpenAdminPages(String page) throws Exception {
        mvc.perform(get(page))
            .andExpect(status().isForbidden());
    }

    /** The other side of the 403 tests: Staff isn't simply blocked from everything. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/me", "/api/products", "/api/categories", "/api/sales/products",
        "/index.html", "/products.html", "/checkout.html"})
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanUseThePagesAndApisMeantForStaff(String url) throws Exception {
        mvc.perform(get(url))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/products"))
            .andExpect(status().isForbidden());
    }

    // ---- Story 6: low-stock alerts are Admin only ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanSeeLowStockAlerts() throws Exception {
        mvc.perform(get("/api/products/low-stock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotSeeLowStockAlerts() throws Exception {
        // This rule must come before the general "GET /api/products/** is open to Staff" rule in
        // SecurityConfig; if the order is swapped, this test fails.
        mvc.perform(get("/api/products/low-stock"))
            .andExpect(status().isForbidden());
    }

    @Test
    void loggedOutUsersCannotSeeLowStockAlerts() throws Exception {
        mvc.perform(get("/api/products/low-stock"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "someone", roles = "GUEST")
    void unknownRolesGet403OnLowStockAlerts() throws Exception {
        mvc.perform(get("/api/products/low-stock"))
            .andExpect(status().isForbidden());
    }
}
