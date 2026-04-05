package com.banka1.order.dto;

import lombok.Data;

/**
 * DTO representing customer data returned by the client-service.
 */
@Data
public class CustomerDto {
    /** The customer's unique identifier. */
    private Long id;
    /** Customer's first name. */
    private String firstName;
    /** Customer's last name. */
    private String lastName;
    /** Customer's email address. */
    private String email;
}
