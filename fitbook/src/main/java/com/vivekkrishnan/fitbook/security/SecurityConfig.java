package com.vivekkrishnan.fitbook.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
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
                        .requestMatchers("/mock-notifications/**").permitAll()
                        .requestMatchers("/provider/**").hasRole("TRAINER")
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
