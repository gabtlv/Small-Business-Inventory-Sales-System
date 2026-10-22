package com.team.inventory.config;

import com.team.inventory.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Login + role rules for the whole app (stories 1 and 4).
 *
 * Rules are checked top to bottom; the first match wins.
 * When you add a new page or endpoint, add its rule here.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LoginUrlAuthenticationEntryPoint loginPage = new LoginUrlAuthenticationEntryPoint("/login.html");

        http
            .authorizeHttpRequests(auth -> auth
                // Public: the login page and its styling/scripts
                .requestMatchers("/login.html", "/css/**", "/js/**", "/error").permitAll()

                // Admin-only pages
                .requestMatchers("/product-form.html", "/reports.html").hasRole("ADMIN")

                // Low-stock alerts (story 6): Admin only. Must come before the general products GET rule.
                .requestMatchers("/api/products/low-stock").hasRole("ADMIN")

                // Products API (stories 2, 3, 9): everyone logged in can view/search, only Admin can change
                .requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/api/products/**").hasRole("ADMIN")

                // Categories API (story 8): same pattern
                .requestMatchers(HttpMethod.GET, "/api/categories/**").hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/api/categories/**").hasRole("ADMIN")

                // Checkout page + Sales API (story 5): Admin and Staff can record sales
                .requestMatchers("/checkout.html").hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/api/sales/**").hasAnyRole("ADMIN", "STAFF")

                // Reports + staff management (Sprints 3-4): Admin only
                .requestMatchers("/api/reports/**", "/api/users/**").hasRole("ADMIN")

                // Everything else just needs a login
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")          // our own HTML page
                .loginProcessingUrl("/login")      // the form POSTs here; Spring checks the password
                .defaultSuccessUrl("/index.html", true)
                .failureUrl("/login.html?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?logout")
                .permitAll()
            )
            // Not logged in: API calls get a 401 (so JS can handle it), pages redirect to the login page
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                } else {
                    loginPage.commence(request, response, authException);
                }
            }))
            // CSRF protection is off to keep the plain HTML/JS frontend simple (fine for a local
            // course project). Turn it back on before deploying anywhere public.
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /** Loads a user from the database when someone logs in. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
            .map(u -> org.springframework.security.core.userdetails.User
                .withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole().name())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("No user " + username));
    }

    /** Passwords are stored as BCrypt hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
