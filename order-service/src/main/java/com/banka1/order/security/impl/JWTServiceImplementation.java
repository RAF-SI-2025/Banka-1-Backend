package com.banka1.order.security.impl;

import com.banka1.order.security.JWTService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Nimbus-JOSE-JWT implementation of {@link JWTService}.
 * Generates HS256-signed service tokens used for inter-service communication.
 * The token identifies the caller as "order-service" with the SERVICE role.
 */
@Service
@Getter
public class JWTServiceImplementation implements JWTService {

    private final JWSSigner signer;

    @Value("${banka.security.roles-claim:roles}")
    private String roleClaim;

    @Value("${banka.security.permissions-claim:permissions}")
    private String permissionClaim;

    @Value("${banka.security.issuer:banka1}")
    private String issuer;

    @Value("${banka.security.expiration-time:3600000}")
    private Long expirationTime;

    /**
     * Initializes the HMAC signer with the shared JWT secret.
     *
     * @param secret the shared secret from {@code jwt.secret} property
     * @throws KeyLengthException if the secret is too short for HS256
     */
    public JWTServiceImplementation(@Value("${jwt.secret}") String secret) throws KeyLengthException {
        this.signer = new MACSigner(secret);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The token uses subject "order-service", role "SERVICE", and an empty permissions list.
     * Expiration is controlled by {@code banka.security.expiration-time}.
     */
    @Override
    public String generateJwtToken() {
        List<String> permissions = new ArrayList<>();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("order-service")
                .issuer(issuer)
                .claim(roleClaim, "SERVICE")
                .claim(permissionClaim, permissions)
                .expirationTime(new Date(System.currentTimeMillis() + expirationTime))
                .build();

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign service JWT", e);
        }
        return jwt.serialize();
    }
}
