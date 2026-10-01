package com.team.inventory.config;

import com.team.inventory.model.Role;
import com.team.inventory.model.User;
import com.team.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one Admin and one Staff account on startup if they don't exist yet,
 * so everyone on the team can log in right away.
 *
 * DEV ONLY: change these passwords (or remove this class) before any real use.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "admin123", Role.ADMIN);
        createIfMissing("staff", "staff123", Role.STAFF);
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (!users.existsByUsername(username)) {
            users.save(new User(username, encoder.encode(rawPassword), role));
            log.info("Created demo {} account '{}'", role, username);
        }
    }
}
