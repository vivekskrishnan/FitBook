package com.vivekkrishnan.fitbook.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isTrainer = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_TRAINER"));
            response.sendRedirect(isTrainer ? "/provider/dashboard" : "/my-appointments");
        };
    }

    // REST API for the Next.js frontend (and any other client): stateless, authenticated with a
    // Bearer JWT instead of a session cookie. Checked first (Order 1), and only for /api/**; the
    // Thymeleaf pages keep the session-cookie chain below. CSRF protection is off here because
    // the API never uses cookies for authentication, so there is no ambient credential to forge.
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/slots", "/api/slots/options").permitAll()
                        .requestMatchers("/api/appointments", "/api/appointments/**", "/api/ai/chat").hasRole("CUSTOMER")
                        .requestMatchers("/api/provider/**", "/api/ai/reminders/**").hasRole("TRAINER")
                        .anyRequest().authenticated()   // /api/auth/me, /api/ai/ask, ...
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtToPrincipal())));
        return http.build();
    }

    // Turns a verified token back into the same AppUserPrincipal the session login produces, so
    // controllers can keep using @AuthenticationPrincipal AppUserPrincipal and the role checks
    // (ROLE_CUSTOMER / ROLE_TRAINER) work identically for both kinds of login.
    private static Converter<Jwt, AbstractAuthenticationToken> jwtToPrincipal() {
        return jwt -> {
            AppUserPrincipal principal = new AppUserPrincipal(
                    Long.valueOf(jwt.getSubject()),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("email"),
                    "",   // the password hash is never in a token
                    jwt.getClaimAsString("role"));
            return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
        };
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler roleBasedSuccessHandler
    ) throws Exception {
        http
                // The mock notification service stands in for an external system called
                // server-to-server by NotificationGateway: there is no browser session or
                // CSRF token, so it is exempt from CSRF (and permitted below).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mock-notifications/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/slots", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/health", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/mock-notifications/**").permitAll()
                        .requestMatchers("/provider/**").hasRole("TRAINER")
                        .requestMatchers("/ai/reminders/**").hasRole("TRAINER")
                        .requestMatchers("/ai/chat", "/ai/chat/**").hasRole("CUSTOMER")
                        .requestMatchers("/book/**", "/my-appointments", "/my-appointments/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(roleBasedSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .permitAll()
                );

        return http.build();
    }
}
