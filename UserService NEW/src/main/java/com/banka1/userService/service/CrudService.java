package com.banka1.userService.service;

import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeEditRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.dto.responses.EmployeeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

public interface CrudService {

    /**
     * Kreira novog zaposlenog.
     *
     * @param dto podaci za kreiranje zaposlenog
     * @return kreirani zaposleni
     */
    EmployeeResponseDto createEmployee(EmployeeCreateRequestDto dto);

    /**
     * Pretrazuje zaposlene po kombinaciji pojedinacnih filtera.
     *
     * @param ime filter po imenu
     * @param prezime filter po prezimenu
     * @param email filter po email adresi
     * @param departman filter po departmanu
     * @param pozicija filter po poziciji
     * @param pageable parametri paginacije
     * @return stranica zaposlenih koji odgovaraju filterima
     */
    Page<EmployeeResponseDto> searchEmployees(
            String ime,
            String prezime,
            String email,
            String departman,
            String pozicija,
            Pageable pageable
    );

    /**
     * Administrativno azurira podatke izabranog zaposlenog.
     *
     * @param jwt JWT korisnika koji vrsi izmenu
     * @param id identifikator zaposlenog koji se menja
     * @param dto podaci za izmenu
     * @return azurirani zaposleni
     */
    EmployeeResponseDto updateEmployee(Jwt jwt, Long id, EmployeeUpdateRequestDto dto);

    /**
     * Omogucava korisniku da izmeni sopstvene podatke.
     *
     * @param jwt JWT prijavljenog korisnika
     * @param dto podaci za izmenu sopstvenog profila
     * @return azurirani prikaz korisnika
     */
    EmployeeResponseDto editEmployee(Jwt jwt, EmployeeEditRequestDto dto);

    /**
     * Vrsi globalnu tekstualnu pretragu zaposlenih.
     *
     * @param query tekstualni upit
     * @param pageable parametri paginacije
     * @return stranica rezultata pretrage
     */
    Page<EmployeeResponseDto> globalSearchEmployees(String query, Pageable pageable);

    /**
     * Soft-brise zaposlenog po identifikatoru.
     *
     * @param id identifikator zaposlenog
     */
    void deleteEmployee(Long id);

}
