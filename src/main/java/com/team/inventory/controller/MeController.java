package com.team.inventory.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/me -> who is logged in and their role.
 * The frontend uses this to show/hide Admin-only buttons.
 * Example response: {"username":"admin","role":"ADMIN"}
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, String> me(Authentication auth) {
        String role = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .findFirst()
            .orElse("UNKNOWN");
        return Map.of("username", auth.getName(), "role", role);
    }
}
