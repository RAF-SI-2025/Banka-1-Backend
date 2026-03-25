package com.banka1.account_service.rabbitMQ;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO koji se salje RabbitMQ email servisu.
 * Sadrzi podatke potrebne za generisanje odgovarajuceg email-a.
 * Polja sa {@code null} vrednoscu se iskljucuju iz JSON serijalizacije.
 */
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailDto {

    /**
     * Email adresa primaoca.
     */
    private String userEmail;

    /**
     * Ime ili korisnicko ime primaoca (koristi se u tekstu mejla).
     */
    private String username;

    /**
     * Tip email notifikacije koji odredjuje sadrzaj i sablonu mejla.
     */
    private EmailType emailType;



    /**
     * Kreira payload za mejl koji ne zahteva dodatni link.
     *
     * @param userEmail email adresa primaoca
     * @param username  korisnicko ime ili ime za prikaz
     * @param emailType tip email notifikacije
     */
    public EmailDto(String username, String userEmail, EmailType emailType) {
        this.userEmail = userEmail;
        this.username = username;
        this.emailType = emailType;
    }
}
