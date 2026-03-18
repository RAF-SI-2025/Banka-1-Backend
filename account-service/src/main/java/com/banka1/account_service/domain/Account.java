package com.banka1.account_service.domain;

import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountStatus;
import com.banka1.account_service.domain.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "account_table"
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type", discriminatorType = DiscriminatorType.STRING)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
//todo da li staviti datum isteka na null
public abstract class Account extends BaseEntity{
    @Column(nullable = false,unique = true,updatable = false)
    private String brojRacuna;
    @Column(nullable = false)
    private String nazivRacuna;
    @Column(nullable = false)
    private Long vlasnik;
    @Column(nullable = false)
    private BigDecimal stanje= BigDecimal.valueOf(0);
    @Column(nullable = false)
    private BigDecimal raspolozivoStanje= BigDecimal.valueOf(0);
    @Column(nullable = false)
    private Long zaposleni;
    //todo mozda LocalDateTime
    @CreationTimestamp
    @Column(nullable = false,updatable = false)
    private LocalDateTime datumIVremeKreiranja;
    private LocalDate datumIsteka;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Currency currency;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;
    @Column(nullable = false)
    private BigDecimal dnevniLimit;
    @Column(nullable = false)
    private BigDecimal mesecniLimit;
    @Column(nullable = false)
    private BigDecimal dnevnaPotrosnja=BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal mesecnaPotrosnja=BigDecimal.ZERO;

    public Account(String brojRacuna, String nazivRacuna, Long vlasnik, BigDecimal stanje, BigDecimal raspolozivoStanje, Long zaposleni, LocalDateTime datumIVremeKreiranja, LocalDate datumIsteka, AccountStatus status, BigDecimal dnevniLimit, BigDecimal mesecniLimit, BigDecimal dnevnaPotrosnja, BigDecimal mesecnaPotrosnja) {
        this.brojRacuna = brojRacuna;
        this.nazivRacuna = nazivRacuna;
        this.vlasnik = vlasnik;
        this.stanje = stanje;
        this.raspolozivoStanje = raspolozivoStanje;
        this.zaposleni = zaposleni;
        this.datumIVremeKreiranja = datumIVremeKreiranja;
        this.datumIsteka = datumIsteka;
        this.status = status;
        this.dnevniLimit = dnevniLimit;
        this.mesecniLimit = mesecniLimit;
        this.dnevnaPotrosnja = dnevnaPotrosnja;
        this.mesecnaPotrosnja = mesecnaPotrosnja;
    }
}
