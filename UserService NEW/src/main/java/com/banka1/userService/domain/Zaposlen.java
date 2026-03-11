package com.banka1.userService.domain;

import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(
        name = "employees",
        indexes = {
                @Index(name = "idx_employees_ime_prezime", columnList = "ime, prezime"),
                @Index(name = "idx_employees_pozicija", columnList = "pozicija")
        }
)
@SQLDelete(sql = "UPDATE employees SET deleted = true WHERE id = ? AND version = ?") // Sprecava hard delete, todo videti da li ovo zelimo i da li uopste radi
@SQLRestriction("deleted = false") // todo Probati Where(clause="deleted=false") ako ovo ne radi
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Zaposlen extends BaseEntity {
    @NotBlank
    @Column(nullable = false)
    private String ime;

    @NotBlank
    @Column(nullable = false)
    private String prezime;

    @Column(nullable = false)
    private LocalDate datumRodjenja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pol pol;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    private String brojTelefona;

    private String adresa;

    @NotBlank
    @Column(nullable = false,unique = true)
    private String username;

    // Sifra je hesirana
    private String password;

    // TODO videti da li ostaje string, da li postaje enum
    @NotBlank
    @Column(nullable = false)
    private String pozicija;

    // TODO videti da li ostaje string, da li postaje enum
    @NotBlank
    @Column(nullable = false)
    private String departman;

    private boolean aktivan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @OneToOne(mappedBy = "zaposlen")
    private ConfirmationToken confirmationToken;
}
