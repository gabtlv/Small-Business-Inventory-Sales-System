package com.team.inventory.config;

import com.team.inventory.repository.DeactivatedUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends the session of a staff account that an Admin has removed (Sprint 3).
 *
 * Login already refuses removed accounts, but someone who was logged in before being removed
 * still has a session. On each signed-in request this checks the account; if it was removed,
 * the session is invalidated and the request continues as logged out, so the normal rules apply
 * (API calls get 401, pages redirect to the login page).
 *
 * Not a @Component on purpose: it's added to the security filter chain in SecurityConfig only.
 */
public class DeactivatedAccountFilter extends OncePerRequestFilter {

    private final DeactivatedUserRepository deactivated;

    public DeactivatedAccountFilter(DeactivatedUserRepository deactivated) {
        this.deactivated = deactivated;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && deactivated.isDeactivated(auth.getName())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
