package com.banka1.userService.dto.requests;

import com.banka1.userService.domain.enums.Role;
import lombok.Getter;
import lombok.Setter;

// Sve je opciono, jer klijent možda želi da updatuje samo jedno polje
@Getter
@Setter
public class EmployeeUpdateRequestDto {
    private String ime;
    private String prezime;
    private String brojTelefona;
    private String adresa;
    private String pozicija;
    private String departman;
    private Boolean aktivan; // Omogućava deaktivaciju radnika
    private Role role;       // Omogućava menjanje permisija
}
