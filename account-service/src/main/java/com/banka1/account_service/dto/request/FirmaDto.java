package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FirmaDto {
    @NotBlank(message = "Unesi naziv")
    private String naziv;
    @NotBlank(message = "Unesi maticni broj")
    @Pattern(regexp = "^\\d{8}$", message = "Maticni broj mora imati tacno 8 cifara")
    private String maticniBroj;
    @NotBlank(message = "Unesi poreski broj")
    @Pattern(regexp = "^\\d{8}$", message = "Poreski broj mora imati tacno 9 cifara")
    private String poreskiBroj;
    @NotBlank(message = "Unesi sifru delatnosti")
    private String sifraDelatnosti;
    //todo videti da li je adresa opcionalna
    //@NotBlank(message = "Unesi adresu")
    private String adresa;
    @NotNull(message = "Unesi vlasnika")
    private Long vlasnik;
}
