package com.banka1.stock_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Security configuration for {@code stock-service}.
 * Registers the JWT decoder bean backed by the shared HMAC secret
 * so the Spring Security resource server can validate incoming tokens.
 */
@Configuration
@EnableMethodSecurity
public class SecurityBeans {

    /**
     * Creates a JWT decoder backed by the HMAC secret from configuration.
     *
     * @param secret shared JWT secret
     * @return configured {@link JwtDecoder}
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
