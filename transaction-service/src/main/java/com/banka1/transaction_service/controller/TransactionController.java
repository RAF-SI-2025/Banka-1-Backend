package com.banka1.transaction_service.controller;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.ErrorResponseDto;
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


@RestController
@RequestMapping("/transaction")
@AllArgsConstructor
//@PreAuthorize("hasRole('CLIENT_BASIC')")

public class TransactionController {
    private TransactionService transactionService;
    @Operation(summary = "Create a new payment")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/payments")
    @PreAuthorize("hasRole('CLIENT_BASIC')")
    public ResponseEntity<String> newPayment(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid NewPaymentDto newPaymentDto) {
        return new ResponseEntity<>(transactionService.newPayment(jwt,newPaymentDto), HttpStatus.OK);
    }

    @Operation(summary = "Approve a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/approve/{id}")
    @PreAuthorize("hasRole('CLIENT_BASIC')")
    public ResponseEntity<String> approveTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody @Valid ApproveDto approveDto) {
        return new ResponseEntity<>(transactionService.approveTransaction(jwt,id,approveDto), HttpStatus.OK);
    }
    @Operation(summary = "Get account transactions")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/accounts/{id}")
    //todo proveriti da li uospte treba za BASIC(EMPLOYEE_BASIC)
    @PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
    public ResponseEntity<Page<TransactionResponseDto>> findAllTransactions(@AuthenticationPrincipal Jwt jwt,
                                                                            @PathVariable Long id,
                                                                            @RequestParam(required = false) String status,
                                                                            @RequestParam(required = false) LocalDate fromDate,
                                                                            @RequestParam(required = false) LocalDate toDate,
                                                                            @RequestParam(required = false) BigDecimal minAmount,
                                                                            @RequestParam(required = false) BigDecimal maxAmount,
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
        return new ResponseEntity<>(transactionService.findAllTransactions(jwt,id,transactionStatus,fromDate,toDate,minAmount,maxAmount,page,size), HttpStatus.OK);
    }

}
