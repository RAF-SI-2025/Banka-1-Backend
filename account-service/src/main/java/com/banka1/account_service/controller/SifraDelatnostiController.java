package com.banka1.account_service.controller;

import com.banka1.account_service.domain.SifraDelatnosti;
import com.banka1.account_service.dto.response.SifraDelatnostiResponseDto;
import com.banka1.account_service.repository.SifraDelatnostiRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST kontroler koji izlaze sifre delatnosti (klasifikacija po granama) koje
 * frontend koristi pri kreiranju poslovnog (firma) racuna.
 * <p>
 * Eksterna putanja je {@code /accounts/api/sifra-delatnosti} (gateway prosledjuje
 * bez strip-ovanja prefiksa, isto kao {@code /accounts/api/currencies}). Pristup
 * imaju klijenti i zaposleni jer je podatak read-only sifarnik potreban tokom
 * forme za otvaranje racuna.
 */
@RestController
@RequestMapping("/accounts/api/sifra-delatnosti")
@AllArgsConstructor
@PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
public class SifraDelatnostiController {

    /** Repozitorijum sifri delatnosti (read-only sifarnik). */
    private final SifraDelatnostiRepository sifraDelatnostiRepository;

    /**
     * Vraca sve sifre delatnosti, sortirane po sifri rastuce.
     *
     * @param jwt JWT token autentifikovanog korisnika
     * @return lista {@link SifraDelatnostiResponseDto} ({@code {sifra, grana}})
     */
    @GetMapping
    public ResponseEntity<List<SifraDelatnostiResponseDto>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<SifraDelatnostiResponseDto> result = sifraDelatnostiRepository
                .findAll(Sort.by(Sort.Direction.ASC, "sifra"))
                .stream()
                .map(SifraDelatnostiResponseDto::from)
                .toList();
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
