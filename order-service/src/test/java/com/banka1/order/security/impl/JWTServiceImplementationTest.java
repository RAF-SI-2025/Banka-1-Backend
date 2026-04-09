package com.banka1.order.security.impl;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JWTServiceImplementationTest {

    @Test
    void generateJwtToken_marksInternalCallsWithServiceRole() throws Exception {
        JWTServiceImplementation jwtService = new JWTServiceImplementation("01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "roleClaim", "roles");
        ReflectionTestUtils.setField(jwtService, "permissionClaim", "permissions");
        ReflectionTestUtils.setField(jwtService, "issuer", "banka1");
        ReflectionTestUtils.setField(jwtService, "expirationTime", 60_000L);

        SignedJWT jwt = SignedJWT.parse(jwtService.generateJwtToken());

        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("order-service");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("roles")).isEqualTo("SERVICE");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("permissions")).isEmpty();
    }
}
