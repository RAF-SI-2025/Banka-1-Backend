package com.banka1.userService.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "confirmation_token")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConfirmationToken extends BaseEntity{
    @NotBlank
    @Column(nullable = false,unique = true)
    private String value;


    private LocalDateTime expirationDateTime;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zaposlen_id", nullable = false,unique = true)
    private Zaposlen zaposlen;

    /**
     * Kreira potvrdu za datog zaposlenog bez eksplicitnog isteka.
     *
     * @param value hesirana vrednost tokena
     * @param zaposlen zaposleni kome token pripada
     */
    public ConfirmationToken(String value, Zaposlen zaposlen) {
        this.value = value;
        this.zaposlen = zaposlen;
    }
}
