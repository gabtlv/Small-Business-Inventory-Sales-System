package com.team.inventory.config;

import com.team.inventory.model.Role;
import com.team.inventory.model.User;
import com.team.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one Admin and one Staff account on startup if they don't exist yet,
 * so everyone on the team can log in right away.
 *
 * Passwords default to admin123 / staff123 for local development and tests. Anywhere public
 * (e.g. the Kubernetes deployment, see DEPLOY.md) they MUST be overridden with the environment
 * variables APP_SEED_ADMIN_PASSWORD and APP_SEED_STAFF_PASSWORD (Spring maps them to the
 * app.seed.* properties below). Only used when the account is first created.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String staffPassword;

    public DataSeeder(UserRepository users, PasswordEncoder encoder,
                      @Value("${app.seed.admin-password:admin123}") String adminPassword,
                      @Value("${app.seed.staff-password:staff123}") String staffPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.staffPassword = staffPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("admin", adminPassword, Role.ADMIN);
        createIfMissing("staff", staffPassword, Role.STAFF);
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (!users.existsByUsername(username)) {
            users.save(new User(username, encoder.encode(rawPassword), role));
            log.info("Created demo {} account '{}'", role, username);
        }
    }
}
