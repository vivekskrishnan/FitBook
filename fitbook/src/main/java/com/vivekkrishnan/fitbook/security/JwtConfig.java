package com.vivekkrishnan.fitbook.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

// JWT signing setup (HS256, one shared secret). The same key signs tokens at login (JwtEncoder)
// and verifies them on every API request (JwtDecoder).
//
// The secret comes from JWT_SECRET (at least 32 characters). If it is not set, a random one is
// generated at startup so no default secret is ever committed; the only cost is that tokens stop
// working after a restart (users log in again). Set JWT_SECRET in production and in Docker.
@Configuration
public class JwtConfig {

    public static final String ISSUER = "fitbook";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final SecretKey key;

    public JwtConfig(@Value("${fitbook.jwt.secret:}") String configuredSecret) {
        byte[] bytes;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET is not set: using a random secret for this run. Tokens will be invalid after a restart.");
        } else {
            bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters long");
            }
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Checks the signature (done by the decoder), expiry/not-before, and that we issued the token.
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), JwtValidators.createDefaultWithIssuer(ISSUER));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
