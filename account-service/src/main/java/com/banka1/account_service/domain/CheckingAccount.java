package com.banka1.account_service.domain;

import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountStatus;
import com.banka1.account_service.domain.enums.Currency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
@Setter
@Entity
@DiscriminatorValue("CHECKING")

public class CheckingAccount extends Account{
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountConcrete accountConcrete;
    private BigDecimal odrzavanjeRacuna= BigDecimal.ZERO;

    public CheckingAccount(String brojRacuna, String nazivRacuna, Long vlasnik, BigDecimal stanje, BigDecimal raspolozivoStanje, Long zaposleni, LocalDateTime datumIVremeKreiranja, LocalDate datumIsteka, Currency currency, AccountStatus status, BigDecimal dnevniLimit, BigDecimal mesecniLimit, BigDecimal dnevnaPotrosnja, BigDecimal mesecnaPotrosnja, AccountConcrete accountConcrete, BigDecimal odrzavanjeRacuna) {
        super(brojRacuna, nazivRacuna, vlasnik, stanje, raspolozivoStanje, zaposleni, datumIVremeKreiranja, datumIsteka, currency, status, dnevniLimit, mesecniLimit, dnevnaPotrosnja, mesecnaPotrosnja);
        this.accountConcrete = accountConcrete;
        this.odrzavanjeRacuna = odrzavanjeRacuna;
    }
}
