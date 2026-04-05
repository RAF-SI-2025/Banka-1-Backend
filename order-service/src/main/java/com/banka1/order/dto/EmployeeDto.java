package com.banka1.order.dto;

import lombok.Data;

/**
 * DTO representing an employee as returned by the employee-service API.
 * Field names match the Serbian naming convention used by that service.
 */
@Data
public class EmployeeDto {
    /** The employee's unique identifier. */
    private Long id;
    /** First name (Serbian API field: ime). */
    private String ime;
    /** Last name (Serbian API field: prezime). */
    private String prezime;
    /** Email address. */
    private String email;
    /** Username used for login. */
    private String username;
    /** Job position (Serbian API field: pozicija). */
    private String pozicija;
    /** Department name (Serbian API field: departman). */
    private String departman;
    /** Whether the employee's account is active. */
    private boolean aktivan;
    /** RBAC role name (e.g. "AGENT", "SUPERVISOR", "ADMIN"). */
    private String role;
}
