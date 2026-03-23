package com.banka1.account_service.controller;

import com.banka1.account_service.dto.request.*;
import com.banka1.account_service.dto.response.*;
import com.banka1.account_service.service.EmployeeService;
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

@RestController
@AllArgsConstructor
@RequestMapping("/employee")
//todo autorizacija

public class EmployeeController {

    private EmployeeService employeeService;

    @Operation(summary = "Create checking account")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PreAuthorize("hasRole('BASIC')")
    @PostMapping("/accounts/checking")
    public ResponseEntity<String> createCheckingAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid CheckingDto checkingDto) {
        return new ResponseEntity<>(employeeService.createCheckingAccount(jwt,checkingDto), HttpStatus.OK);
    }

    @Operation(summary = "Create FX account")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PreAuthorize("hasRole('BASIC')")
    @PostMapping("/accounts/fx")
    public ResponseEntity<String> createFxAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid FxDto fxDto) {
        return new ResponseEntity<>(employeeService.createFxAccount(jwt,fxDto), HttpStatus.OK);
    }

    @Operation(summary = "Search all accounts")
    @ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PreAuthorize("hasAnyRole('BASIC','SERVICE')")
    @GetMapping("/accounts")
    public ResponseEntity<Page<AccountSearchResponseDto>> searchAllAccounts(@AuthenticationPrincipal Jwt jwt,
                                                                            @RequestParam(required = false) String imeVlasnikaRacuna,
                                                                            @RequestParam(required = false) String prezimeVlasnikaRacuna,
                                                                            @RequestParam(required = false) String accountNumber,
                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size
    ) {
        return new ResponseEntity<>(employeeService.searchAllAccounts(jwt,imeVlasnikaRacuna,prezimeVlasnikaRacuna,accountNumber,page,size), HttpStatus.OK);
    }



//    @Operation(summary = "Update card status")
//    @ApiResponses({
//        @ApiResponse(responseCode = "400", description = "Invalid request body",
//            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
//        @ApiResponse(responseCode = "401", description = "Unauthorized",
//            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
//        @ApiResponse(responseCode = "403", description = "Forbidden",
//            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
//        @ApiResponse(responseCode = "404", description = "Card not found",
//            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
//    })
//    @PreAuthorize("hasRole('BASIC')")
//    @PutMapping("/cards/{id}")
//    public ResponseEntity<String> updateCard(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody @Valid UpdateCardDto updateCardDto)
//    {
//        return new ResponseEntity<>(employeeService.updateCard(jwt,id,updateCardDto), HttpStatus.OK);
//    }
}
