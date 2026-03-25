package com.banka1.account_service.dto.request;

import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxDto {
    @NotBlank(message = "Ne sme biti prazan naziv racuna")
    private String nazivRacuna;
    private Long idVlasnika;
    private String jmbg;
    //todo ne znam da li ovde stavljam istek
    //private LocalDate datumIsteka;
    @NotNull(message = "Unesi valutu")
    private CurrencyCode currencyCode;
    @NotNull(message = "Unesi tip racuna")
    private AccountOwnershipType tipRacuna;
    @NotNull(message = "Unesi initialBalance")
    private BigDecimal initialBalance;
    @NotNull(message = "Unesi createCard")
    private Boolean createCard;
    private FirmaDto firma;

}
