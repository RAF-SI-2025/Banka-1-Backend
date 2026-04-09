package com.banka1.order.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Security bean configuration for JWT token validation.
 * Provides a {@link JwtDecoder} that verifies incoming tokens using the shared HMAC secret,
 * enabling OAuth2 Resource Server support across the service.
 */
@Configuration
@EnableMethodSecurity
public class SecurityBeans {

    /**
     * Creates a {@link JwtDecoder} that validates HMAC-SHA256 signed tokens.
     *
     * @param secret the shared secret loaded from {@code jwt.secret} property
     * @return configured NimbusJwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
