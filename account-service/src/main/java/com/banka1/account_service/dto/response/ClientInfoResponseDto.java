package com.banka1.account_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO koji vraca samo ID klijenta, koristi se za JMBG lookup endpoint.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientInfoResponseDto {

    /** Identifikator klijenta. */
    private Long id;
    private String name,lastName;
    private String username;
    private String email;
}