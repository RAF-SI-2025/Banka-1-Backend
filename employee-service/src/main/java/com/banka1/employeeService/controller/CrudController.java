package com.banka1.employeeService.controller;

import com.banka1.employeeService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeEditRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.employeeService.dto.responses.EmployeeResponseDto;
import com.banka1.employeeService.service.CrudService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * REST kontroler koji izlaze endpoint-e za CRUD operacije nad zaposlenima.
 * Svi endpoint-i su dostupni pod baznom putanjom {@code /employees}.
 * Kreiranje i brisanje su ograniceni na ADMIN ulogu; azuriranje zahteva AGENT ili jacu ulogu.
 */
@RestController
@RequestMapping("/employees")
@AllArgsConstructor
@Validated
public class CrudController {

    /** Servis koji sadrzi poslovnu logiku upravljanja zaposlenima. */
    private final CrudService crudService;

    /**
     * Kreira novog zaposlenog. Dostupno samo korisnicima sa ADMIN ulogom.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param employeeDto podaci za kreiranje zaposlenog
     * @return kreirani zaposleni sa statusom 201 Created
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponseDto> createEmployee(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid EmployeeCreateRequestDto employeeDto) {
        EmployeeResponseDto created = crudService.createEmployee(employeeDto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Vrsi pretragu zaposlenih po pojedinacnim filterima uz paginaciju.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param ime filter po imenu (opcioni)
     * @param prezime filter po prezimenu (opcioni)
     * @param email filter po email adresi (opcioni)
     * @param pozicija filter po poziciji (opcioni)
     * @param departman filter po departmanu (opcioni)
     * @param page broj stranice (pocetak od 0)
     * @param size velicina stranice (1–100)
     * @return stranica zaposlenih koji odgovaraju filterima
     */
    @GetMapping
    public ResponseEntity<Page<EmployeeResponseDto>> searchEmployees(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String ime,
            @RequestParam(required = false) String prezime,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String pozicija,
            @RequestParam(required = false) String departman,
            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EmployeeResponseDto> response = crudService.searchEmployees(ime, prezime, email, pozicija, departman, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Vrsi globalnu pretragu zaposlenih preko jedinstvenog slobodnog tekst upita.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param query tekstualni upit za pretragu (opcioni)
     * @param page broj stranice
     * @param size velicina stranice
     * @return stranica rezultata globalne pretrage
     */
    @GetMapping("/search")
    public ResponseEntity<Page<EmployeeResponseDto>> globalSearchEmployees(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EmployeeResponseDto> response = crudService.globalSearchEmployees(query, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Vraca jednog zaposlenog po identifikatoru.
     *
     * @param id identifikator zaposlenog
     * @return zaposleni
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponseDto> getEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(crudService.getEmployee(id));
    }

    /**
     * Azurira podatke zaposlenog po identifikatoru. Dostupno korisnicima sa AGENT ili jacom ulogom.
     * Vraca 202 Accepted ako je nalog deaktiviran, inace 200 OK.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param id identifikator zaposlenog
     * @param updateDto podaci za izmenu
     * @return azurirani zaposleni
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public ResponseEntity<EmployeeResponseDto> updateEmployee(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EmployeeUpdateRequestDto updateDto
    ) {
        EmployeeResponseDto updated = crudService.updateEmployee(jwt, id, updateDto);
        if (updateDto.getAktivan() != null && !updateDto.getAktivan())
            return new ResponseEntity<>(updated, HttpStatus.ACCEPTED);
        return ResponseEntity.ok(updated);
    }

    /**
     * Omogucava prijavljenom korisniku da izmeni sopstvene podatke profila.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param editRequestDto podaci za samostalnu izmenu profila
     * @return azurirani prikaz korisnika
     */
    @PutMapping("/edit")
    public ResponseEntity<EmployeeResponseDto> editEmployee(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid EmployeeEditRequestDto editRequestDto
    ) {
        EmployeeResponseDto updated = crudService.editEmployee(jwt, editRequestDto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft-brise zaposlenog po identifikatoru. Dostupno samo korisnicima sa ADMIN ulogom.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param id identifikator zaposlenog za brisanje
     * @return prazan odgovor sa statusom 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        crudService.deleteEmployee(id, jwt);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
