package com.banka1.account_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Spring Security konfiguracija koja registruje bean-ove za JWT dekodiranje
 * i verifikaciju potpisa tokena zasnovanu na deljenoj HMAC tajni.
 */
@Configuration
@EnableMethodSecurity
public class SecurityBeans {

    /**
     * Kreira JWT dekoder zasnovan na deljenoj HMAC tajni.
     * Koristi se od strane Spring Security OAuth2 Resource Server-a za validaciju dolaznih JWT tokena.
     *
     * @param secret tajna za verifikaciju JWT potpisa ucitana iz konfiguracije
     * @return konfigurisan JWT dekoder
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
