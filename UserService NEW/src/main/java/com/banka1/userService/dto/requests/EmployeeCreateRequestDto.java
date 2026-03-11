package com.banka1.userService.dto.requests;

import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeCreateRequestDto {
    @NotBlank(message = "Ime je obavezno")
    private String ime;

    @NotBlank(message = "Prezime je obavezno")
    private String prezime;

    @NotNull(message = "Datum rođenja je obavezan")
    private LocalDate datumRodjenja;

    @NotNull(message = "Pol je obavezan")
    private Pol pol;

    @Email(message = "Nevalidan format email-a")
    @NotBlank(message = "Email je obavezan")
    private String email;

    private String brojTelefona;

    private String adresa;

    @NotBlank(message = "Korisničko ime je obavezno")
    private String username;

    //todo Prema specifikaciji, sifra se postavlja nakon verifikacije, postavlja korisnik istu
//    @NotBlank(message = "Lozinka je obavezna")
//    private String password;

    @NotBlank(message = "Pozicija je obavezna")
    private String pozicija;

    @NotBlank(message = "Departman je obavezan")
    private String departman;

    @NotNull(message = "Uloga (Role) je obavezna")
    private Role role;
}


