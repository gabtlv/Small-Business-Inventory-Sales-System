package com.team.inventory.service;

import com.team.inventory.dto.StaffDto;
import com.team.inventory.dto.StaffRequest;
import com.team.inventory.model.DeactivatedUser;
import com.team.inventory.model.Role;
import com.team.inventory.model.User;
import com.team.inventory.repository.DeactivatedUserRepository;
import com.team.inventory.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff management (Sprint 3): Admin lists, adds and removes Staff accounts.
 *
 * Removing doesn't delete the user (their sales point at them); it marks the account as
 * deactivated, which blocks login and ends any session it still has (see SecurityConfig).
 */
@Service
public class StaffService {

    private final UserRepository users;
    private final DeactivatedUserRepository deactivated;
    private final PasswordEncoder encoder;

    public StaffService(UserRepository users, DeactivatedUserRepository deactivated, PasswordEncoder encoder) {
        this.users = users;
        this.deactivated = deactivated;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<StaffDto> list() {
        return users.findActiveByRole(Role.STAFF).stream().map(StaffService::toDto).toList();
    }

    @Transactional
    public StaffDto create(StaffRequest request) {
        String username = request.username().trim();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password is too long.");   // BCrypt only uses 72 bytes
        }
        users.findByUsernameIgnoreCase(username).ifPresent(existing -> {
            throw ApiException.conflict(deactivated.existsById(existing.getId())
                ? "Username \"" + username + "\" belongs to a removed account. Choose another username."
                : "Username \"" + username + "\" is already taken.");
        });
        try {
            return toDto(users.saveAndFlush(new User(username, encoder.encode(request.password()), Role.STAFF)));
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.conflict("Username \"" + username + "\" is already taken.");
        }
    }

    @Transactional
    public void remove(long id) {
        User user = users.findById(id)
            .filter(u -> u.getRole() == Role.STAFF && !deactivated.existsById(u.getId()))
            .orElseThrow(() -> ApiException.notFound("Staff account not found. It may have been removed already."));
        deactivated.save(new DeactivatedUser(user.getId()));
    }

    private static StaffDto toDto(User u) {
        return new StaffDto(u.getId(), u.getUsername());
    }
}
