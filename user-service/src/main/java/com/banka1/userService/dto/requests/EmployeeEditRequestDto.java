package com.banka1.userService.dto.requests;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EmployeeEditRequestDto {
    private String ime;
    private String prezime;
    private String brojTelefona;
    private String adresa;
    private String pozicija;
    private String departman;
}
