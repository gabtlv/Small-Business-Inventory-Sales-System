package com.team.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The deployed app must not use the well-known dev passwords. DataSeeder reads them from
 * app.seed.* (environment variables APP_SEED_ADMIN_PASSWORD / APP_SEED_STAFF_PASSWORD in
 * Kubernetes), and these tests prove the override works and the defaults stop working.
 *
 * Uses its own empty in-memory database, so the seeder creates the accounts fresh with these
 * passwords instead of finding the ones other test classes already created.
 */
@SpringBootTest(properties = {
    "app.seed.admin-password=Deployed-Admin-Pw1",
    "app.seed.staff-password=Deployed-Staff-Pw1",
    "spring.datasource.url=jdbc:h2:mem:seedtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class SeededPasswordTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void adminLogsInWithConfiguredPassword() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("Deployed-Admin-Pw1"))
            .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    void staffLogsInWithConfiguredPassword() throws Exception {
        mvc.perform(formLogin("/login").user("staff").password("Deployed-Staff-Pw1"))
            .andExpect(authenticated().withRoles("STAFF"));
    }

    @Test
    void devDefaultPasswordsNoLongerWork() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("admin123"))
            .andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user("staff").password("staff123"))
            .andExpect(unauthenticated());
    }
}
