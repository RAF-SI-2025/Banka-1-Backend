package com.banka1.account_service.dto.request;

import com.banka1.account_service.domain.enums.AccountConcrete;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
//todo proveriti sta cemo za odrzavanje racuna
public class CheckingDto {

    @NotBlank
    private String nazivRacuna;
    private Long idVlasnika;
    private String jmbg;
//    //todo ne znam da li ovde stavljam istek
//    private LocalDate datumIsteka;
    @NotNull(message = "Unesi podvrstu racuna")
    private AccountConcrete vrstaRacuna;
    private FirmaDto firma;
}
