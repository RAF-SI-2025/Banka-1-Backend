package com.banka1.account_service.security.implementation;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JWTServiceImplementationTest {

    @Test
    void generateJwtTokenBuildsSignedServiceTokenWithExpectedClaims() throws Exception {
        String secret = "12345678901234567890123456789012";
        JWTServiceImplementation service = new JWTServiceImplementation(secret);
        ReflectionTestUtils.setField(service, "role", "roles");
        ReflectionTestUtils.setField(service, "permission", "permissions");
        ReflectionTestUtils.setField(service, "issuer", "banka1");
        ReflectionTestUtils.setField(service, "expirationTime", 60_000L);

        String token = service.generateJwtToken();
        SignedJWT jwt = SignedJWT.parse(token);

        boolean verified = jwt.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)));

        assertThat(verified).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("account-service");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("banka1");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("roles")).isEqualTo("SERVICE");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("permissions")).isEqualTo(List.of());
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new Date());
    }
}

