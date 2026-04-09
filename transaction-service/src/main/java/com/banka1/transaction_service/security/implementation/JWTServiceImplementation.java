package com.banka1.transaction_service.security.implementation;

import com.banka1.transaction_service.security.JWTService;
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
 * Implementation of the JWT service for generating and signing JWT tokens.
 * Uses the HMAC-SHA256 algorithm for token signing.
 */
@Service
@Getter
public class JWTServiceImplementation implements JWTService {

    /**
     * Signer that signs JWT tokens using the HMAC-SHA256 algorithm.
     */
    private final JWSSigner signer;

    /**
     * Name of the claim in the JWT that carries the user's role.
     */
    @Value("${banka.security.roles-claim}")
    private String role;

    /**
     * Name of the claim in the JWT that carries the user's permissions.
     */
    @Value("${banka.security.permissions-claim}")
    private String permission;

    /**
     * Name of the claim in the JWT that carries the identifier of the user/service.
     */
    @Value("${banka.security.id}")
    private String id;

    /**
     * Issuer value written into the JWT token.
     */
    @Value("${banka.security.issuer}")
    private String issuer;

    /**
     * Duration of the JWT token in milliseconds.
     */
    @Value("${banka.security.expiration-time}")
    private Long expirationTime;

    /**
     * Initializes the service for signing JWT tokens by loading the HMAC secret.
     *
     * @param secret HMAC secret for signing tokens (minimum 32 characters for HS256)
     * @throws KeyLengthException if the secret length is insufficient for HS256
     */
    public JWTServiceImplementation(@Value("${jwt.secret}") String secret) throws KeyLengthException {
        this.signer = new MACSigner(secret);
    }

    /**
     * Generates a JWT access token with standard claims for the service.
     * <p>
     * The token contains:
     * <ul>
     *   <li>Subject: "account-service"</li>
     *   <li>Issuer: configured in properties</li>
     *   <li>Role: "SERVICE"</li>
     *   <li>Permissions: empty list</li>
     *   <li>Expiration: current timestamp + configured duration</li>
     * </ul>
     *
     * @return serialized signed JWT token
     * @throws IllegalStateException if an error occurs during signing
     */
    @Override
    public String generateJwtToken() {
        List<String> permissions = new ArrayList<>();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("account-service")
                .issuer(issuer)
                .claim(role, "SERVICE")
                .claim(permission, permissions)
                .expirationTime(new Date(System.currentTimeMillis() + expirationTime))
                .build();

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (Exception e) {
            throw new IllegalStateException("Greska sa generisanjem JWT-a");
        }
        return jwt.serialize();
    }


}
