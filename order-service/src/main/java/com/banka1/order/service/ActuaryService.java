package com.banka1.order.service;

import com.banka1.order.dto.ActuaryAgentDto;
import com.banka1.order.dto.SetLimitRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business logic interface for actuary management.
 * All operations are restricted to supervisors (including admins via role hierarchy).
 */
public interface ActuaryService {

    /**
     * Returns a list of all employees with the AGENT role, combined with their actuary limit data.
     * Employee data is fetched from employee-service; limit data from the local database.
     * An {@link com.banka1.order.entity.ActuaryInfo} record is created with default values
     * if one does not yet exist for a given agent.
     *
     * @param email    optional email filter passed to employee-service
     * @param ime      optional first name filter
     * @param prezime  optional last name filter
     * @param pozicija optional position filter
     * @return list of agent DTOs with actuary limit information
     */
    Page<ActuaryAgentDto> getAgents(String email, String ime, String prezime, String pozicija, Pageable pageable);

    /**
     * Updates the daily trading limit for the specified agent.
     * Throws {@link IllegalArgumentException} if the target employee is an ADMIN
     * or does not have the AGENT role.
     *
     * @param employeeId the employee's identifier
     * @param request    new limit value in RSD
     */
    void setLimit(Long employeeId, SetLimitRequestDto request);

    /**
     * Resets the consumed daily limit ({@code usedLimit}) to zero for the specified agent.
     * Throws {@link IllegalArgumentException} if the target employee is an ADMIN.
     *
     * @param employeeId the employee's identifier
     */
    void resetLimit(Long employeeId);

    /**
     * Resets {@code usedLimit} to zero for every actuary record in the database.
     * Called automatically by the scheduler at 23:59 every day.
     */
    void resetAllLimits();
}
