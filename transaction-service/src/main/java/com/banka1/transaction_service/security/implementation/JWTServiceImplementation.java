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

@Service
@Getter
public class JWTServiceImplementation implements JWTService {

    /** Signer koji potpisuje JWT tokene HMAC-SHA256 algoritmom. */
    private final JWSSigner signer;




    /** Naziv claim-a u JWT-u koji nosi ime uloge korisnika. */
    @Value("${banka.security.roles-claim}")
    private String role;

    /** Naziv claim-a u JWT-u koji nosi listu permisija korisnika. */
    @Value("${banka.security.permissions-claim}")
    private String permission;

    /** Naziv claim-a u JWT-u koji nosi identifikator korisnika. */
    @Value("${banka.security.id}")
    private String id;

    /** Issuer vrednost koja se upisuje u JWT token. */
    @Value("${banka.security.issuer}")
    private String issuer;

    /** Vreme trajanja JWT tokena u milisekundama. */
    @Value("${banka.security.expiration-time}")
    private Long expirationTime;

    /**
     * Inicijalizuje servis za potpisivanje JWT tokena ucitavanjem HMAC tajne.
     *
     * @param secret HMAC tajna za potpisivanje tokena
     * @throws KeyLengthException ako je tajna neodgovarajuce duzine za HS256
     */
    public JWTServiceImplementation(@Value("${jwt.secret}") String secret) throws KeyLengthException {
        this.signer = new MACSigner(secret);
    }

    /**
     * Generise JWT pristupni token za zadatog zaposlenog.
     * Token sadrzi email (subject), identifikator, ulogu, permisije i vreme isteka.
     *
     * @param
     * @return serijalizovan potpisani JWT token
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
