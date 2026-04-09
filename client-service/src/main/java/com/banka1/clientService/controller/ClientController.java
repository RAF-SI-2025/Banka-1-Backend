package com.banka1.clientService.controller;

import com.banka1.clientService.dto.requests.ClientCreateRequestDto;
import com.banka1.clientService.dto.requests.ClientUpdateRequestDto;
import com.banka1.clientService.dto.responses.ClientInfoResponseDto;
import com.banka1.clientService.dto.responses.ClientResponseDto;
import com.banka1.clientService.service.ClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST kontroler koji izlaze endpoint-e za CRUD operacije nad klijentima.
 * Svi endpoint-i su dostupni pod baznom putanjom {@code /customers}.
 * Pristup je ogranicen na zaposlene (validni JWT token sa employee ulogom).
 * JMBG lookup endpoint je dostupan iskljucivo SERVICE tokenima.
 */
@RestController
@RequestMapping("/customers")
@AllArgsConstructor
@Validated
public class ClientController {

    /** Servis koji sadrzi poslovnu logiku upravljanja klijentima. */
    private final ClientService clientService;

    /**
     * Kreira novog klijenta. Dostupno zaposlenima sa AGENT ili jacom ulogom.
     *
     * @param jwt JWT trenutno prijavljenog zaposlenog
     * @param dto podaci za kreiranje klijenta
     * @return kreirani klijent sa statusom 201 Created
     */
    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ClientResponseDto> createClient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ClientCreateRequestDto dto
    ) {
        ClientResponseDto created = clientService.createClient(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Pretrazuje klijente po filterima (ime, prezime, email) uz paginaciju.
     * Rezultati su sortirani abecedno po prezimenu.
     *
     * @param jwt JWT trenutno prijavljenog zaposlenog
     * @param ime filter po imenu (opcioni)
     * @param prezime filter po prezimenu (opcioni)
     * @param email filter po email adresi (opcioni)
     * @param page broj stranice (pocetak od 0)
     * @param size velicina stranice (1–100)
     * @return stranica klijenata koji odgovaraju filterima
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','SERVICE')")
    public ResponseEntity<Page<ClientResponseDto>> searchClients(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String ime,
            @RequestParam(required = false) String prezime,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(clientService.searchClients(ime, prezime, email, pageable));
    }

    /**
     * Vrsi globalnu pretragu klijenata jednim tekstualnim upitom po imenu, prezimenu i emailu.
     *
     * @param jwt JWT trenutno prijavljenog zaposlenog
     * @param query tekstualni upit za pretragu (opcioni)
     * @param page broj stranice
     * @param size velicina stranice
     * @return stranica rezultata globalne pretrage
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Page<ClientResponseDto>> globalSearchClients(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(clientService.globalSearchClients(query, pageable));
    }

    /**
     * Azurira podatke klijenta po identifikatoru.
     * JMBG i password se ne mogu menjati.
     * Pri promeni emaila automatski se proverava jedinstvenost.
     *
     * @param jwt JWT trenutno prijavljenog zaposlenog
     * @param id identifikator klijenta
     * @param dto podaci za izmenu
     * @return azurirani klijent
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ClientResponseDto> updateClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid ClientUpdateRequestDto dto
    ) {
        return ResponseEntity.ok(clientService.updateClient(id, dto));
    }

    /**
     * Soft-brise klijenta po identifikatoru. Dostupno samo korisnicima sa ADMIN ulogom.
     *
     * @param jwt JWT trenutno prijavljenog zaposlenog
     * @param id identifikator klijenta za brisanje
     * @return prazan odgovor sa statusom 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        clientService.deleteClient(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Vraca ID klijenta na osnovu JMBG-a.
     * Ovoj ruti moze pristupiti SAMO SERVICE token (interni pozivi izmedju servisa).
     *
     * @param jmbg JMBG klijenta
     * @return DTO sa ID-em klijenta
     */
    @GetMapping("/jmbg/{jmbg}")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    public ResponseEntity<ClientInfoResponseDto> getInfoByJmbg(@AuthenticationPrincipal Jwt jwt,@PathVariable String jmbg) {
        return ResponseEntity.ok(clientService.getInfoByJmbg(jmbg));
    }

    /**
     * Vraca osnovne informacije o klijentu na osnovu internog ID-a.
     * Ovoj ruti moze pristupiti SAMO SERVICE token (interni pozivi izmedju servisa).
     *
     * @param id identifikator klijenta
     * @return DTO sa ID-em, imenom i prezimenom klijenta
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    public ResponseEntity<ClientInfoResponseDto> getInfoById(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getInfoById(id));
    }
}
