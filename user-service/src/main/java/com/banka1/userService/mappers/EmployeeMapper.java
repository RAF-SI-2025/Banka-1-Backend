package com.banka1.userService.mappers;


import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.domain.service.ZaposlenService;
import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeEditRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.dto.responses.EmployeeResponseDto;
import com.banka1.userService.exception.BusinessException;
import com.banka1.userService.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeMapper {
    private final ZaposlenService zaposlenService;

    // DTO -> Entity
    /**
     * Mapira DTO za kreiranje zaposlenog u entitet.
     *
     * @param dto ulazni podaci za kreiranje
     * @return novi entitet zaposlenog
     */
    public Zaposlen toEntity(EmployeeCreateRequestDto dto) {
        Zaposlen zaposlen = new Zaposlen();
        zaposlen.setIme(dto.getIme());
        zaposlen.setPrezime(dto.getPrezime());
        zaposlen.setDatumRodjenja(dto.getDatumRodjenja());
        zaposlen.setPol(dto.getPol());
        zaposlen.setEmail(dto.getEmail());
        zaposlen.setBrojTelefona(dto.getBrojTelefona());
        zaposlen.setAdresa(dto.getAdresa());
        zaposlen.setUsername(dto.getUsername());
        zaposlen.setPozicija(dto.getPozicija());
        zaposlen.setDepartman(dto.getDepartman());
        zaposlen.setRole(dto.getRole());
        zaposlenService.setovanjePermisija(zaposlen);
        return zaposlen;
    }

    // Entity -> DTO
    /**
     * Mapira entitet zaposlenog u izlazni DTO.
     *
     * @param zaposlen entitet zaposlenog
     * @return DTO za API odgovor
     */
    public EmployeeResponseDto toDto(Zaposlen zaposlen) {
        return new EmployeeResponseDto(
                zaposlen.getId(),
                zaposlen.getIme(),
                zaposlen.getPrezime(),
                zaposlen.getEmail(),
                zaposlen.getUsername(),
                zaposlen.getPozicija(),
                zaposlen.getDepartman(),
                zaposlen.isAktivan(),
                zaposlen.getRole()
        );
    }

    // Update postojećeg Entiteta pomoću DTO-a
    /**
     * Azurira entitet zaposlenog administrativnim izmenama.
     *
     * @param zaposlen entitet koji se menja
     * @param dto DTO sa novim vrednostima
     */
    public void updateEntityFromDto(Zaposlen zaposlen, EmployeeUpdateRequestDto dto, Role role) {
        if (dto.getIme() != null) zaposlen.setIme(dto.getIme());
        if (dto.getPrezime() != null) zaposlen.setPrezime(dto.getPrezime());
        if (dto.getBrojTelefona() != null) zaposlen.setBrojTelefona(dto.getBrojTelefona());
        if (dto.getAdresa() != null) zaposlen.setAdresa(dto.getAdresa());
        if (dto.getPozicija() != null) zaposlen.setPozicija(dto.getPozicija());
        if (dto.getDepartman() != null) zaposlen.setDepartman(dto.getDepartman());
        if (dto.getAktivan() != null) zaposlen.setAktivan(dto.getAktivan());
        if (dto.getRole() != null) {
            if(dto.getRole().getPower() > role.getPower())
                throw new BusinessException(ErrorCode.NOT_STRONG_ROLE,"Ne mozes da mu das jacu rolu od svoje");
            zaposlen.setRole(dto.getRole());
        }
    }

    /**
     * Azurira entitet zaposlenog podacima koje korisnik moze samostalno da menja.
     *
     * @param zaposlen entitet koji se menja
     * @param dto DTO sa novim vrednostima
     */
    public void updateEntityFromDto(Zaposlen zaposlen, EmployeeEditRequestDto dto) {
        if (dto.getIme() != null) zaposlen.setIme(dto.getIme());
        if (dto.getPrezime() != null) zaposlen.setPrezime(dto.getPrezime());
        if (dto.getBrojTelefona() != null) zaposlen.setBrojTelefona(dto.getBrojTelefona());
        if (dto.getAdresa() != null) zaposlen.setAdresa(dto.getAdresa());
        if (dto.getPozicija() != null) zaposlen.setPozicija(dto.getPozicija());
        if (dto.getDepartman() != null) zaposlen.setDepartman(dto.getDepartman());
    }
}
