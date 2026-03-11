package com.banka1.userService.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken extends BaseEntity{
    //todo videcu da obrisem unique = true posto vise nije neophodno, al ne skodi
    @NotBlank
    @Column(nullable = false,unique = true)
    private String value;

    @Column(nullable = false)
    private LocalDateTime expirationDateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zaposlen_id", nullable = false)
    private Zaposlen zaposlen;

    /**
     * Kreira refresh token sa zadatom vrednoscu i vremenom isteka.
     *
     * @param value hesirana vrednost tokena
     * @param expirationDateTime datum i vreme isteka tokena
     * @param zaposlen zaposleni kome token pripada
     */
    public RefreshToken(String value, LocalDateTime expirationDateTime, Zaposlen zaposlen) {
        this.value = value;
        this.expirationDateTime = expirationDateTime;
        this.zaposlen = zaposlen;
    }

    /**
     * Kreira refresh token bez vrednosti, ali sa unapred zadatim istekom.
     *
     * @param zaposlen zaposleni kome token pripada
     * @param expirationDateTime datum i vreme isteka tokena
     */
    public RefreshToken(Zaposlen zaposlen, LocalDateTime expirationDateTime) {
        this.zaposlen = zaposlen;
        this.expirationDateTime=expirationDateTime;
    }

    /**
     * Kreira refresh token za zaposlenog, pri cemu se vrednost i istek postavljaju naknadno.
     *
     * @param zaposlen zaposleni kome token pripada
     */
    public RefreshToken(Zaposlen zaposlen) {
        this.zaposlen = zaposlen;
    }
}
