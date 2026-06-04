package com.banka1.account_service.dto.response;

import com.banka1.account_service.domain.SifraDelatnosti;

/**
 * Lagani odgovor sa sifrom delatnosti i nazivom grane.
 * <p>
 * Frontend (account-create) ocekuje listu objekata oblika {@code {sifra, grana}}
 * za popunjavanje dropdown-a pri kreiranju poslovnog racuna. Vracamo DTO umesto
 * {@link SifraDelatnosti} entiteta da ne bismo serijalizovali lazy {@code sektori}
 * kolekciju i interna polja (id, version).
 *
 * @param sifra jedinstvena sifra delatnosti (npr. "6419")
 * @param grana ljudski-citljiv naziv grane (npr. "Finansijska delatnost")
 */
public record SifraDelatnostiResponseDto(String sifra, String grana) {

    /**
     * Mapira JPA entitet u lagani DTO.
     *
     * @param entity entitet sifre delatnosti
     * @return DTO sa sifrom i granom
     */
    public static SifraDelatnostiResponseDto from(SifraDelatnosti entity) {
        return new SifraDelatnostiResponseDto(entity.getSifra(), entity.getGrana());
    }
}
