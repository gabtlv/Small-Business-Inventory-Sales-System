package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.inventory.dto.SaleRequest;
import com.team.inventory.service.SaleService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Sprint 3: Admin manages Staff accounts (list, add, remove); Staff can't. */
@SpringBootTest
@AutoConfigureMockMvc
class StaffManagementTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private SaleService saleService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_request_keys");
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM deactivated_users");
        jdbc.update("DELETE FROM users WHERE username NOT IN ('admin', 'staff')");
    }

    // ---- Access ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanOpenPageAndSeeStaffWithoutPasswords() throws Exception {
        mvc.perform(get("/staff.html")).andExpect(status().isOk());

        mvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username").value(hasItem("staff")))
            .andExpect(jsonPath("$[*].username").value(not(hasItem("admin"))))   // Staff only
            .andExpect(jsonPath("$[0].password").doesNotExist())
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andExpect(content().string(Matchers.not(Matchers.containsString("$2a$"))));   // no BCrypt hash anywhere
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotOpenPageOrUseEndpoints() throws Exception {
        long staffId = userId("staff");

        mvc.perform(get("/staff.html")).andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
        addStaff("{\"username\":\"sneaky\",\"password\":\"secret1\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + staffId)).andExpect(status().isForbidden());

        assertThat(count("SELECT COUNT(*) FROM users WHERE username = 'sneaky'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM deactivated_users")).isZero();
    }

    @Test
    void loggedOutUsersAreRejected() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/staff.html").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
    }

    // ---- Adding staff ----

    @Test
    void adminAddsStaffAccountThatCanLogIn() throws Exception {
        mvc.perform(post("/api/users").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"  jamie \",\"password\":\"secret1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.username").value("jamie"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'jamie'", String.class)).isEqualTo("STAFF");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'jamie'", String.class);
        assertThat(hash).isNotEqualTo("secret1");
        assertThat(encoder.matches("secret1", hash)).isTrue();

        mvc.perform(get("/api/users").with(user("admin").roles("ADMIN")))
            .andExpect(jsonPath("$[*].username").value(hasItem("jamie")));
        mvc.perform(formLogin("/login").user("jamie").password("secret1"))
            .andExpect(authenticated().withRoles("STAFF"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void newAccountsAreAlwaysStaffEvenIfRequestAsksForAdmin() throws Exception {
        addStaff("{\"username\":\"wannabe\",\"password\":\"secret1\",\"role\":\"ADMIN\"}")
            .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'wannabe'", String.class)).isEqualTo("STAFF");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"password\":\"secret1\"}",                          // no username
        "{\"username\":\"  \",\"password\":\"secret1\"}",      // blank username
        "{\"username\":\"has space\",\"password\":\"secret1\"}",
        "{\"username\":\"ab\",\"password\":\"secret1\"}",      // too short
        "{\"username\":\"jamie\"}",                            // no password
        "{\"username\":\"jamie\",\"password\":\"\"}",
        "{\"username\":\"jamie\",\"password\":\"12345\"}"      // too short
    })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void missingOrInvalidFieldsAreRejected(String body) throws Exception {
        addStaff(body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        assertThat(count("SELECT COUNT(*) FROM users WHERE username NOT IN ('admin', 'staff')")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"staff", "STAFF", "admin"})
    @WithMockUser(username = "admin", roles = "ADMIN")
    void duplicateUsernamesAreRejected(String username) throws Exception {
        int before = count("SELECT COUNT(*) FROM users");

        addStaff("{\"username\":\"" + username + "\",\"password\":\"secret1\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Username \"" + username + "\" is already taken."));

        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'admin'", String.class)).isEqualTo("ADMIN");
    }

    // ---- Removing staff ----

    @Test
    void removedStaffLosesAccessButTheirSalesAreKept() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 10)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);
        saleService.recordSale("jamie", new SaleRequest(List.of(new SaleRequest.Item(pen, 2))));

        // Jamie is logged in (a real session) before being removed
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("jamie").password("secret1"))
            .andExpect(authenticated())
            .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        mvc.perform(delete("/api/users/" + jamie).with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());

        // The existing session no longer works
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/api/sales").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + pen + ",\"quantity\":1}]}"))
            .andExpect(status().isUnauthorized());

        // And they can't log in again
        mvc.perform(formLogin("/login").user("jamie").password("secret1"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login.html?error"));

        // Their account row and sales history are untouched; no extra sale was recorded
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = " + jamie)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sales WHERE user_id = " + jamie)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sale_items")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, pen)).isEqualTo(8);

        // And they're gone from the staff list
        mvc.perform(get("/api/users").with(user("admin").roles("ADMIN")))
            .andExpect(jsonPath("$[*].username").value(not(hasItem("jamie"))));
    }

    @Test
    void removedSessionIsSentToLoginPage() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/login").user("jamie").password("secret1"))
            .andReturn().getRequest().getSession(false);

        mvc.perform(delete("/api/users/" + jamie).with(user("admin").roles("ADMIN"))).andExpect(status().isNoContent());

        mvc.perform(get("/checkout.html").session(session).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    void otherStaffAreUnaffectedByARemoval() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        createStaff("riley", "secret2");

        mvc.perform(delete("/api/users/" + jamie).with(user("admin").roles("ADMIN"))).andExpect(status().isNoContent());

        mvc.perform(formLogin("/login").user("riley").password("secret2")).andExpect(authenticated());
        mvc.perform(formLogin("/login").user("staff").password("staff123")).andExpect(authenticated());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAccountsCannotBeRemoved() throws Exception {
        mvc.perform(delete("/api/users/" + userId("admin"))).andExpect(status().isNotFound());
        assertThat(count("SELECT COUNT(*) FROM deactivated_users")).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void removingTwiceOrUnknownIdIsNotFound() throws Exception {
        long jamie = createStaff("jamie", "secret1");

        mvc.perform(delete("/api/users/" + jamie)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/" + jamie))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Staff account not found. It may have been removed already."));
        mvc.perform(delete("/api/users/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void removedUsernameCannotBeReusedSoSalesStayAttributed() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        mvc.perform(delete("/api/users/" + jamie)).andExpect(status().isNoContent());

        addStaff("{\"username\":\"jamie\",\"password\":\"another1\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Username \"jamie\" belongs to a removed account. Choose another username."));
    }

    // ---- Helpers ----

    private ResultActions addStaff(String json) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Creates a Staff account through the API, as Admin; returns its id. */
    private long createStaff(String username, String password) throws Exception {
        mvc.perform(post("/api/users").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isCreated());
        return userId(username);
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
