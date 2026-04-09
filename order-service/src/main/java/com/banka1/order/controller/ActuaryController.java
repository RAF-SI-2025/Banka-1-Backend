package com.banka1.order.controller;

import com.banka1.order.dto.ActuaryAgentDto;
import com.banka1.order.dto.SetLimitRequestDto;
import com.banka1.order.service.ActuaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing actuary management endpoints.
 * All endpoints require the SUPERVISOR role (which includes ADMIN via role hierarchy).
 */
@RestController
@RequestMapping("/actuaries")
@RequiredArgsConstructor
public class ActuaryController {

    private final ActuaryService actuaryService;

    /**
     * Returns a list of all agents, optionally filtered by email, name, surname, or position.
     * Employee data is fetched from employee-service; actuary limits are loaded from the local database.
     *
     * @param email    optional email filter
     * @param ime      optional first name filter
     * @param prezime  optional last name filter
     * @param pozicija optional position filter
     * @return list of agents with their actuary limit information
     */
    @GetMapping("/agents")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Page<ActuaryAgentDto>> getAgents(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String ime,
            @RequestParam(required = false) String prezime,
            @RequestParam(required = false) String pozicija,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(actuaryService.getAgents(email, ime, prezime, pozicija, PageRequest.of(page, size)));
    }

    /**
     * Updates the daily trading limit for the specified agent.
     * Admins cannot be targeted. Only employees with the AGENT role are eligible.
     *
     * @param id      the employee ID of the agent
     * @param request request body containing the new limit value in RSD
     * @return 200 OK on success
     */
    @PutMapping("/agents/{id}/limit")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> setLimit(
            @PathVariable Long id,
            @RequestBody @Valid SetLimitRequestDto request
    ) {
        actuaryService.setLimit(id, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Resets the used daily limit ({@code usedLimit}) to zero for the specified agent.
     * Can be triggered manually by a supervisor at any time.
     *
     * @param id the employee ID of the agent
     * @return 200 OK on success
     */
    @PutMapping("/agents/{id}/reset-limit")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> resetLimit(@PathVariable Long id) {
        actuaryService.resetLimit(id);
        return ResponseEntity.ok().build();
    }
}
