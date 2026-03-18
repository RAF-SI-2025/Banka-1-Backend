package com.banka1.account_service.domain;

import com.banka1.account_service.domain.enums.AccountStatus;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
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
@DiscriminatorValue("FX")
//todo fk firma instanca
public class FxAccount extends Account {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountOwnershipType accountOwnershipType;

    public FxAccount(String brojRacuna, String nazivRacuna, Long vlasnik, BigDecimal stanje, BigDecimal raspolozivoStanje, Long zaposleni, LocalDateTime datumIVremeKreiranja, LocalDate datumIsteka, Currency currency, AccountStatus status, BigDecimal dnevniLimit, BigDecimal mesecniLimit, BigDecimal dnevnaPotrosnja, BigDecimal mesecnaPotrosnja, AccountOwnershipType accountOwnershipType) {
        super(brojRacuna, nazivRacuna, vlasnik, stanje, raspolozivoStanje, zaposleni, datumIVremeKreiranja, datumIsteka, status, dnevniLimit, mesecniLimit, dnevnaPotrosnja, mesecnaPotrosnja);
        this.setCurrency(currency);
        this.accountOwnershipType = accountOwnershipType;
    }


    @Override
    public void setCurrency(Currency currency) {
        if(currency == Currency.RSD)
            throw new IllegalArgumentException("Ne sme RSD");
        super.setCurrency(currency);
    }
}
