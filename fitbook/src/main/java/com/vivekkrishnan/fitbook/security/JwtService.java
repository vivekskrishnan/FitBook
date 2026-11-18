package com.vivekkrishnan.fitbook.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Checks an email and password with the same user lookup and BCrypt check as the web login,
// then issues a signed JWT carrying just what the API needs to know about the user.
@Service
public class JwtService {

    public record IssuedToken(String token, long expiresInSeconds, AppUserPrincipal user) {}

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final Duration lifetime;

    public JwtService(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                      @Value("${fitbook.jwt.expiration-minutes:60}") long expirationMinutes) {
        // Private to this class (not a bean), so the web form-login chain is unaffected.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        this.authenticationManager = new ProviderManager(provider);
        this.jwtEncoder = jwtEncoder;
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    // Throws AuthenticationException if the email or password is wrong.
    public IssuedToken login(String email, String password) throws AuthenticationException {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        AppUserPrincipal user = (AppUserPrincipal) authentication.getPrincipal();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(String.valueOf(user.getUserId()))
                .claim("email", user.getUsername())
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(token, lifetime.toSeconds(), user);
    }
}
