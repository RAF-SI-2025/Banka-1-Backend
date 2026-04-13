package com.banka1.employeeService.service;

import com.banka1.employeeService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeEditRequestDto;
import com.banka1.employeeService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.employeeService.dto.responses.EmployeeResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Servis za CRUD operacije nad entitetom zaposlenog.
 * Pokriva kreiranje, pretragu, azuriranje, soft-brisanje i upravljanje profilom.
 */
public interface CrudService {

    /**
     * Kreira novog zaposlenog, generise aktivacioni token i salje aktivacioni mejl.
     *
     * @param dto podaci za kreiranje zaposlenog
     * @return kreiran zaposleni
     */
    EmployeeResponseDto createEmployee(EmployeeCreateRequestDto dto);

    /**
     * Pretrazuje zaposlene po kombinaciji pojedinacnih filtera uz paginaciju.
     * Svaki filter koristi case-insensitive LIKE; vrednost {@code null} se tretira kao wildcard.
     *
     * @param ime filter po imenu
     * @param prezime filter po prezimenu
     * @param email filter po email adresi
     * @param departman filter po departmanu
     * @param pozicija filter po poziciji
     * @param pageable parametri paginacije i sortiranja
     * @return stranica zaposlenih koji odgovaraju svim filterima
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
     * Proverava da pozivalac ima dovoljno jaku ulogu pre izmene.
     *
     * @param jwt JWT korisnika koji vrsi izmenu
     * @param id identifikator zaposlenog koji se menja
     * @param dto podaci za izmenu
     * @return azurirani zaposleni
     */
    EmployeeResponseDto updateEmployee(Jwt jwt, Long id, EmployeeUpdateRequestDto dto);

    /**
     * Omogucava prijavljenom korisniku da izmeni sopstvene podatke profila.
     * Identifikator se izvlaci iz JWT claim-a {@code id}.
     *
     * @param jwt JWT prijavljenog korisnika
     * @param dto podaci za izmenu sopstvenog profila
     * @return azurirani prikaz korisnika
     */
    EmployeeResponseDto editEmployee(Jwt jwt, EmployeeEditRequestDto dto);

    /**
     * Vrsi globalnu tekstualnu pretragu zaposlenih po svim relevantnim kolonama.
     *
     * @param query tekstualni upit
     * @param pageable parametri paginacije i sortiranja
     * @return stranica rezultata pretrage
     */
    Page<EmployeeResponseDto> globalSearchEmployees(String query, Pageable pageable);

    /**
     * Vraca jednog zaposlenog po identifikatoru.
     *
     * @param id identifikator zaposlenog
     * @return zaposleni
     */
    EmployeeResponseDto getEmployee(Long id);

    /**
     * Soft-brise zaposlenog po identifikatoru i salje email o deaktivaciji.
     *
     * @param id identifikator zaposlenog koji se brise
     */
    void deleteEmployee(Long id, Jwt jwt);
}
