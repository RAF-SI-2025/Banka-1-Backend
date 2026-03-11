package com.banka1.userService.controller;

import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeEditRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.dto.responses.EmployeeResponseDto;
import com.banka1.userService.service.CrudService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employees")
@AllArgsConstructor
@Validated
public class CrudController {

    private final CrudService crudService;

    /**
     * Kreira novog zaposlenog.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param employeeDto podaci za kreiranje zaposlenog
     * @return kreirani zaposleni
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponseDto> createEmployee(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid EmployeeCreateRequestDto employeeDto) {
        EmployeeResponseDto created = crudService.createEmployee(employeeDto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Vrsi pretragu zaposlenih po pojedinacnim filterima.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param ime filter po imenu
     * @param prezime filter po prezimenu
     * @param email filter po email adresi
     * @param pozicija filter po poziciji
     * @param departman filter po departmanu
     * @param page broj stranice
     * @param size velicina stranice
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

        return ResponseEntity.ok(response); // OK 200
    }

    /**
     * Vrsi globalnu pretragu zaposlenih preko jedinstvenog upita.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param query tekstualni upit za pretragu
     * @param page broj stranice
     * @param size velicina stranice
     * @return stranica rezultata globalne pretrage
     */
    @GetMapping("/search")
    public ResponseEntity<Page<EmployeeResponseDto>> globalSearchEmployees(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EmployeeResponseDto> response = crudService.globalSearchEmployees(query, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Azurira podatke zaposlenog po identifikatoru.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param id identifikator zaposlenog
     * @param updateDto podaci za izmenu
     * @return azurirani zaposleni
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<EmployeeResponseDto> updateEmployee(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid EmployeeUpdateRequestDto updateDto
    ) {

        EmployeeResponseDto updated = crudService.updateEmployee(jwt, id, updateDto);
        if(updateDto.getAktivan()!=null && !updateDto.getAktivan())
            return new ResponseEntity<>(updated,HttpStatus.ACCEPTED);
        return ResponseEntity.ok(updated); // OK 200
    }

    /**
     * Omogucava prijavljenom korisniku da izmeni sopstvene podatke.
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
        return ResponseEntity.ok(updated); // OK 200
    }

    /**
     * Soft-brise zaposlenog po identifikatoru.
     *
     * @param jwt JWT trenutno prijavljenog korisnika
     * @param id identifikator zaposlenog za brisanje
     * @return prazan odgovor sa statusom 204
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        crudService.deleteEmployee(id);
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
}
