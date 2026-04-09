package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO combining employee data from employee-service with actuary limit data from the local database.
 * Returned by the actuary management portal endpoint.
 */
@Data
public class ActuaryAgentDto {
    /** The employee's unique identifier. */
    private Long employeeId;
    /** First name (Serbian: ime). */
    private String ime;
    /** Last name (Serbian: prezime). */
    private String prezime;
    /** Email address. */
    private String email;
    /** Job position (Serbian: pozicija). */
    private String pozicija;
    /** Daily trading limit in RSD. Null if not yet set by a supervisor. */
    private BigDecimal limit;
    /** Amount of the daily limit already consumed in RSD. */
    private BigDecimal usedLimit;
    /** If true, all orders placed by this agent require supervisor approval. */
    private Boolean needApproval;
}
