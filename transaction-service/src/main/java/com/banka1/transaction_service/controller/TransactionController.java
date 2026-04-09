package com.banka1.transaction_service.controller;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.ErrorResponseDto;
import com.banka1.transaction_service.dto.response.NewPaymentResponseDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * REST controller for managing transactions.
 * Provides endpoints for creating, retrieving, and searching transactions.
 */
@RestController
@AllArgsConstructor
//@PreAuthorize("hasRole('CLIENT_BASIC')")
public class TransactionController {
    private TransactionService transactionService;

    /**
     * Creates a new transaction.
     *
     * @param jwt JWT token of the authenticated user
     * @param newPaymentDto the details of the new payment
     * @return the response containing the status and message of the created payment
     */
    @Operation(summary = "Create a new payment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment created",
                    content = @Content(schema = @Schema(implementation = NewPaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping({"/payment", "/payments"})
    @PreAuthorize("hasRole('CLIENT_BASIC')")
    public ResponseEntity<NewPaymentResponseDto> newPayment(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid NewPaymentDto newPaymentDto) {
        return new ResponseEntity<>(transactionService.newPayment(jwt,newPaymentDto), HttpStatus.OK);
    }




    /**
     * Retrieves all transactions for a specific account.
     *
     * @param jwt JWT token of the authenticated user
     * @param accountNumber the account number to retrieve transactions for
     * @param page page number (starting from 0)
     * @param size number of items per page
     * @return a paginated list of transactions
     */
    @Operation(summary = "Get account transactions")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/accounts/{accountNumber}")
    //todo proveriti da li uospte treba za BASIC(EMPLOYEE_BASIC)
    @PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
    public ResponseEntity<Page<TransactionResponseDto>> findAllTransactions(@AuthenticationPrincipal Jwt jwt,
                                                                            @PathVariable String accountNumber,
                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {

        return new ResponseEntity<>(transactionService.findAllTransactions(jwt,accountNumber,page,size), HttpStatus.OK);
    }
    /**
     * Searches for transactions based on various criteria.
     *
     * @param jwt JWT token of the authenticated user
     * @param accountNumber account number to filter (optional)
     * @param status transaction status as a string (optional, converts to enum)
     * @param fromDate start date for the period (optional)
     * @param toDate end date for the period (optional)
     * @param initialAmountMin minimum initial amount (optional)
     * @param initialAmountMax maximum initial amount (optional)
     * @param finalAmountMin minimum final amount (optional)
     * @param finalAmountMax maximum final amount (optional)
     * @param page page number
     * @param size number of items per page
     * @return a paginated list of transactions matching the criteria
     */
    @GetMapping("/api/payments")
    //todo proveriti da li uospte treba za BASIC(EMPLOYEE_BASIC)
    @PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
    public ResponseEntity<Page<TransactionResponseDto>> findPayments(@AuthenticationPrincipal Jwt jwt,
                                                                            @RequestParam(required = false) String accountNumber,
                                                                            @RequestParam(required = false) String status,
                                                                            @RequestParam(required = false) LocalDateTime fromDate,
                                                                            @RequestParam(required = false) LocalDateTime toDate,
                                                                            @RequestParam(required = false) BigDecimal initialAmountMin,
                                                                            @RequestParam(required = false) BigDecimal initialAmountMax,
                                                                            @RequestParam(required = false) BigDecimal finalAmountMin,
                                                                            @RequestParam(required = false) BigDecimal finalAmountMax,
                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {
        TransactionStatus transactionStatus=null;
        if(status!=null)
            try {
                transactionStatus = TransactionStatus.valueOf(status.toUpperCase());
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Nevalidan status");
            }
        return new ResponseEntity<>(transactionService.findPayments(jwt,accountNumber,transactionStatus,fromDate,toDate,initialAmountMin,initialAmountMax,finalAmountMin,finalAmountMax,page,size), HttpStatus.OK);
    }


}
