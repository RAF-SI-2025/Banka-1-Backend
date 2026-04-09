package com.banka1.transaction_service.controller;

import com.banka1.transaction_service.dto.response.ErrorResponseDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing employee-related operations.
 * Provides endpoints for retrieving employee-specific data.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/employee")
public class EmployeeController {

    private TransactionService transactionService;

    /**
     * Retrieves all transactions for a specific account accessible by an employee.
     *
     * @param jwt JWT token of the authenticated employee
     * @param accountNumber the account number to retrieve transactions for
     * @param page page number (starting from 0)
     * @param size number of items per page
     * @return a paginated list of transactions
     */
    @Operation(summary = "Get all transactions for an account (employee access")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/accounts/{accountNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'AGENT', 'BASIC')")
    public ResponseEntity<Page<TransactionResponseDto>> findAllTransactionsForEmployee(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {

        return new ResponseEntity<>(
                transactionService.findAllTransactionsForEmployee(accountNumber, page, size),
                HttpStatus.OK);
    }
}
