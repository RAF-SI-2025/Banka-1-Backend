package com.banka1.transaction_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Configuration class for security-related beans.
 * Provides beans for authentication and authorization mechanisms.
 */
@Configuration
@EnableMethodSecurity
public class SecurityBeans {

    /**
     * Creates a JWT decoder bean based on a shared HMAC secret.
     * Used by the Spring Security OAuth2 Resource Server to validate incoming JWT tokens.
     *
     * @param secret the secret for verifying JWT signatures loaded from configuration
     * @return configured JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
