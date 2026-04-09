package com.banka1.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * DTO representing customer data returned by the client-service.
 */
@Data
public class CustomerDto {
    /** The customer's unique identifier. */
    private Long id;
    /** Customer's first name. */
    @JsonAlias({"firstName", "name", "ime"})
    private String firstName;
    /** Customer's last name. */
    @JsonAlias({"lastName", "prezime"})
    private String lastName;
    /** Customer's email address. */
    private String email;
}
