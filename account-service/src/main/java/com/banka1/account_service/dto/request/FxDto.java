package com.banka1.account_service.dto.request;

import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxDto {
    @NotBlank
    private String nazivRacuna;
    private Long idVlasnika;
    private String jmbg;
    //todo ne znam da li ovde stavljam istek
    //private LocalDate datumIsteka;
    @NotNull(message = "Unesi valutu")
    private Currency currency;
    @NotNull(message = "Unesi tip racuna")
    private AccountOwnershipType tipRacuna;
    private FirmaDto firma;

}
