package com.banka1.userService.security.implementation;

import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.security.JWTService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Getter
public class JWTServiceImplementation implements JWTService {

    private final JWSSigner signer;
    private final SecureRandom random=new SecureRandom();
    private final byte[] bytes = new byte[32];
    @Value("${banka.security.roles-claim}")
    private String role;
    @Value("${banka.security.permissions-claim}")
    private String permission;

    /**
     * Inicijalizuje servis za potpisivanje JWT tokena.
     *
     * @param secret HMAC tajna za potpisivanje tokena
     * @throws KeyLengthException ako je tajna neodgovarajuce duzine
     */
    public JWTServiceImplementation(@Value("${jwt.secret}") String secret) throws KeyLengthException {
        this.signer = new MACSigner(secret);
    }


    /**
     * Generise JWT pristupni token za zadatog zaposlenog.
     *
     * @param zaposlen zaposleni za kog se token izdaje
     * @return serijalizovan JWT token
     */
    @Override
    public String generateJwtToken(Zaposlen zaposlen) {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(zaposlen.getIme())
                .issuer("banka1")
                .claim("id",zaposlen.getId())
                .claim(role, zaposlen.getRole().name())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                .build();

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Greska sa generisanjem JWT-a");
        }
        return jwt.serialize();
    }

    /**
     * Hesira prosledjenu vrednost koristeci SHA-256 algoritam.
     *
     * @param value vrednost koja se hesira
     * @return SHA-256 hash u hex formatu
     */
    @Override
    public String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 nije dostupan", e);
        }
    }

    /**
     * Pretvara niz bajtova u heksadecimalni zapis.
     *
     * @param bytes niz bajtova za konverziju
     * @return string u hex formatu
     */
    private  String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Generise nasumican token bez padding-a, pogodan za URL upotrebu.
     *
     * @return URL-safe nasumicni token
     */
    @Override
    public String generateRandomToken() {
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
