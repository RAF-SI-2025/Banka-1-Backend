package com.banka1.account_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityBeansTest {

    @Test
    void jwtDecoderCreatesDecoderForValidSecret() {
        SecurityBeans securityBeans = new SecurityBeans();

        JwtDecoder jwtDecoder = securityBeans.jwtDecoder("12345678901234567890123456789012");

        assertThat(jwtDecoder).isNotNull();
    }
}

