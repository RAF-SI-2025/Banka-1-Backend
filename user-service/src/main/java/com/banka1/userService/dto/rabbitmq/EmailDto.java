package com.banka1.userService.dto.rabbitmq;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailDto {
    private String userEmail;
    private String username;
    private EmailType emailType;
    private ResetLinkDto resetLinkDto;

    /**
     * Kreira payload za mejl koji sadrzi i link za aktivaciju ili reset lozinke.
     *
     * @param userEmail email adresa primaoca
     * @param username korisnicko ime ili ime za prikaz
     * @param emailType tip email notifikacije
     * @param link link koji se salje korisniku
     */
    public EmailDto(String userEmail, String username, EmailType emailType, String link) {
        this.userEmail = userEmail;
        this.username = username;
        this.emailType = emailType;
        resetLinkDto=new ResetLinkDto(link,emailType);
    }

    /**
     * Kreira payload za mejl koji ne zahteva dodatni link.
     *
     * @param userEmail email adresa primaoca
     * @param username korisnicko ime ili ime za prikaz
     * @param emailType tip email notifikacije
     */
    public EmailDto(String userEmail, String username, EmailType emailType) {
        this.userEmail = userEmail;
        this.username = username;
        this.emailType = emailType;
    }
}
