package com.banka1.order.controller;

import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.PortfolioResponse;
import com.banka1.order.dto.PortfolioSummaryResponse;
import com.banka1.order.dto.SetPublicQuantityRequestDto;
import com.banka1.order.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * REST controller exposing portfolio-related endpoints.
 * Allows clients and agent-role employees (actuaries in business terminology) to view and manage their portfolio positions.
 */
@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class
PortfolioController {

    private final PortfolioService portfolioService;

    /**
     * Returns a list of all portfolio positions for the authenticated user.
     * Includes stock data fetched from stock-service such as ticker and current price,
     * as well as calculated profit for each position.
     *
     * @param userId ID of the authenticated user (should be extracted from JWT in real scenario)
     * @return list of portfolio positions with detailed information
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT_BASIC','CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<PortfolioSummaryResponse> getPortfolio(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(portfolioService.getPortfolio(toAuthenticatedUser(jwt)));
    }

    /**
     * Sets the number of shares from a STOCK position to be publicly available for OTC trading.
     * Only STOCK positions support public exposure.
     *
     * @param id      portfolio position ID
     * @param request request containing desired public quantity
     * @return 200 OK on success
     */
    @PutMapping("/{id}/set-public")
    @PreAuthorize("hasAnyRole('CLIENT_BASIC','CLIENT_TRADING','AGENT','SUPERVISOR')")
    public ResponseEntity<Void> setPublicQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody @Valid SetPublicQuantityRequestDto request
    ) {
        portfolioService.setPublicQuantity(toAuthenticatedUser(jwt), id, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Exercises an option position from the portfolio.
     * Only users with the AGENT role (actuaries) are allowed to execute options.
     *
     * Business rules:
     * - Option must not be expired (settlementDate in the future)
     * - Option must be in-the-money (CALL or PUT logic)
     * - Executes contract based on contract size (e.g. 100 shares per option)
     *
     * @param id     portfolio position ID
     * @param userId ID of the user performing the action
     * @return 200 OK on successful execution
     */
    @PostMapping("/{id}/exercise-option")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR')")
    public ResponseEntity<Void> exerciseOption(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        portfolioService.exerciseOption(toAuthenticatedUser(jwt), id);
        return ResponseEntity.ok().build();
    }

    private AuthenticatedUser toAuthenticatedUser(Jwt jwt) {
        return new AuthenticatedUser(
                Long.valueOf(jwt.getSubject()),
                extractStrings(jwt.getClaim("roles")),
                extractStrings(jwt.getClaim("permissions"))
        );
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractStrings(Object claim) {
        if (claim == null) {
            return Set.of();
        }
        if (claim instanceof String value) {
            return Set.of(value);
        }
        if (claim instanceof Collection<?> values) {
            Set<String> result = new LinkedHashSet<>();
            for (Object value : values) {
                if (value != null) {
                    result.add(String.valueOf(value));
                }
            }
            return result;
        }
        return Set.of(String.valueOf(claim));
    }
}


