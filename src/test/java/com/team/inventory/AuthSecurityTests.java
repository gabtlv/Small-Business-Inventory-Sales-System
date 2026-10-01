package com.team.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for story 1 (login) and story 4 (Staff has limited access). */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityTests {

    @Autowired
    private MockMvc mvc;

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
    void pagesRedirectToLoginWhenLoggedOut() throws Exception {
        // A browser asks for HTML, so it gets redirected to the login page
        mvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void apiReturns401WhenLoggedOut() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void meReturnsCurrentUserAndRole() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---- Story 4: Staff has limited access ----

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/products"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotOpenAdminPages() throws Exception {
        mvc.perform(get("/product-form.html"))
            .andExpect(status().isForbidden());
    }
}
