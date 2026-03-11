package com.banka1.userService.security;

import com.banka1.userService.domain.Zaposlen;

public interface JWTService {
    /**
     * Generise JWT pristupni token za zaposlenog.
     *
     * @param zaposlen autentifikovani zaposleni
     * @return potpisani JWT token
     */
    String generateJwtToken(Zaposlen zaposlen);
    /**
     * Generise kriptografski bezbedan nasumicni token.
     *
     * @return nasumicni token spreman za slanje klijentu
     */
    String generateRandomToken();
    /**
     * Vraca SHA-256 heksadecimalni zapis prosledjene vrednosti.
     *
     * @param value ulazna vrednost za hesiranje
     * @return hesirana vrednost u hex formatu
     */
    String sha256Hex(String value);
}
