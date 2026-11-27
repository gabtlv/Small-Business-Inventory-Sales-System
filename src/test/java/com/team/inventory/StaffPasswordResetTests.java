package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Sprint 4: Admin resets a Staff account's password; Staff can't. */
@SpringBootTest
@AutoConfigureMockMvc
class StaffPasswordResetTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM deactivated_users");
        jdbc.update("DELETE FROM users WHERE username NOT IN ('admin', 'staff')");
        jdbc.update("UPDATE users SET password_hash = ? WHERE username = 'staff'", encoder.encode("staff123"));
    }

    // ---- Access ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotResetPasswords() throws Exception {
        long staffId = userId("staff");
        String before = hash("staff");

        resetPassword(staffId, "{\"password\":\"hacked123\"}").andExpect(status().isForbidden());

        assertThat(hash("staff")).isEqualTo(before);
    }

    @Test
    void loggedOutUsersAreRejected() throws Exception {
        long staffId = userId("staff");
        String before = hash("staff");

        resetPassword(staffId, "{\"password\":\"hacked123\"}").andExpect(status().isUnauthorized());

        assertThat(hash("staff")).isEqualTo(before);
    }

    // ---- Resetting ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminResetsPasswordAndTheNewOneWorks() throws Exception {
        long jamie = createStaff("jamie", "secret1");

        resetPassword(jamie, "{\"password\":\"newsecret1\"}")
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        // The new password logs in, the old one no longer does
        mvc.perform(formLogin("/login").user("jamie").password("newsecret1"))
            .andExpect(authenticated().withRoles("STAFF"));
        mvc.perform(formLogin("/login").user("jamie").password("secret1"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/login.html?error"));

        // Stored as a BCrypt hash, never the plain text
        String hash = hash("jamie");
        assertThat(hash).isNotEqualTo("newsecret1");
        assertThat(encoder.matches("newsecret1", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username = 'jamie'", String.class)).isEqualTo("STAFF");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void responseNeverContainsPasswordOrHash() throws Exception {
        long jamie = createStaff("jamie", "secret1");

        resetPassword(jamie, "{\"password\":\"newsecret1\"}")
            .andExpect(content().string(Matchers.not(Matchers.containsString("newsecret1"))))
            .andExpect(content().string(Matchers.not(Matchers.containsString("$2a$"))));
    }

    @Test
    void otherAccountsAreUnaffected() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        createStaff("riley", "secret2");
        String adminBefore = hash("admin");

        mvc.perform(put("/api/users/" + jamie + "/password").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"newsecret1\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(formLogin("/login").user("riley").password("secret2")).andExpect(authenticated());
        mvc.perform(formLogin("/login").user("staff").password("staff123")).andExpect(authenticated());
        assertThat(hash("admin")).isEqualTo(adminBefore);
    }

    // ---- Bad requests ----

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",                                   // no password
        "{\"password\":\"\"}",
        "{\"password\":\"      \"}",            // blank
        "{\"password\":\"12345\"}",             // too short
        "{\"password\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"   // more than 72 characters
    })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidPasswordsAreRejectedAndNothingChanges(String body) throws Exception {
        long jamie = createStaff("jamie", "secret1");
        String before = hash("jamie");

        resetPassword(jamie, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        assertThat(hash("jamie")).isEqualTo(before);
        mvc.perform(formLogin("/login").user("jamie").password("secret1")).andExpect(authenticated());
    }

    // ---- Accounts that can't be reset ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAccountsCannotBeResetHere() throws Exception {
        String before = hash("admin");

        resetPassword(userId("admin"), "{\"password\":\"newsecret1\"}").andExpect(status().isNotFound());

        assertThat(hash("admin")).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void removedOrUnknownAccountsAreNotFound() throws Exception {
        long jamie = createStaff("jamie", "secret1");
        mvc.perform(delete("/api/users/" + jamie)).andExpect(status().isNoContent());
        String before = hash("jamie");

        resetPassword(jamie, "{\"password\":\"newsecret1\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Staff account not found. It may have been removed already."));
        resetPassword(999999, "{\"password\":\"newsecret1\"}").andExpect(status().isNotFound());

        assertThat(hash("jamie")).isEqualTo(before);
    }

    // ---- Helpers ----

    private ResultActions resetPassword(long id, String json) throws Exception {
        return mvc.perform(put("/api/users/" + id + "/password").contentType(MediaType.APPLICATION_JSON).content(json));
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

    private String hash(String username) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, username);
    }
}
