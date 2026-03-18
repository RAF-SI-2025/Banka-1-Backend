//package com.banka1.account_service.controller;
//
//import com.banka1.account_service.dto.request.*;
//import com.banka1.account_service.dto.response.*;
//import jakarta.validation.Valid;
//import jakarta.validation.constraints.Max;
//import jakarta.validation.constraints.Min;
//import org.springframework.data.domain.Page;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.web.bind.annotation.*;
//
//public class SVEZASVAKISLUCAJ {
//    @PostMapping("/payments")
//    public ResponseEntity<String> newPayment(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid NewPaymentDto newPaymentDto) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    @PostMapping("/transactions/{id}/approve")
//    public ResponseEntity<String> approveTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody @Valid ApproveDto approveDto) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    @PostMapping("/create/checking-account")
//    public ResponseEntity<String> createCheckingAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid CheckingDto checkingDto) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    @PostMapping("/create/fx-account")
//    public ResponseEntity<String> createFxAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid FxDto fxDto) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    //todo mozda detalji i find uopste ne moraju da se razlikuju po pitanju toga sta se vraca
//    @GetMapping("/my-accounts")
//    public ResponseEntity<Page<AccountResponseDto>> findMyAccounts(@AuthenticationPrincipal Jwt jwt,
//                                                                   @RequestParam(defaultValue = "0") @Min(value = 0) int page,
//                                                                   @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    @GetMapping("/all-accounts")
//    public ResponseEntity<Page<AccountSearchResponseDto>> searchAllAccounts(@AuthenticationPrincipal Jwt jwt,
//                                                                            @RequestParam(required = false) String imeVlasnikaRacuna,
//                                                                            @RequestParam(required = false) String prezimeVlasnikaRacuna,
//                                                                            @RequestParam(required = false) String accountNumber,
//                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
//                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size
//    ) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//
//    @GetMapping("/transactions/{accountId}")
//    public ResponseEntity<Page<TransactionResponseDto>> findAllTransactions(@AuthenticationPrincipal Jwt jwt,
//                                                                            @PathVariable Long accountId,
//                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
//                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//
//    @PutMapping("/edit-account-name/{id}")
//    public ResponseEntity<String> editAccountName(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody @Valid EditAccountNameDto editAccountNameDto)
//    {
//        return null;
//    }
//    //todo samo vlasnik racuna, znaci nema autorizacije vrv samo menjas za sebe a ne exposujes endpoint da neko moze za nekog drugog
//    @PutMapping("/edit-account-limit/{id}")
//    public ResponseEntity<String> editAccountLimit(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody @Valid EditAccountLimitDto editAccountLimitDto)
//    {
//        return null;
//    }
//    @GetMapping("/details/{id}")
//    public ResponseEntity<AccountDetailsResponseDto> editAccountLimit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id)
//    {
//        return null;
//    }
//    @GetMapping("/cards/{accountId}")
//    public ResponseEntity<Page<CardResponseDto>> findAllCards(@AuthenticationPrincipal Jwt jwt,
//                                                              @PathVariable Long accountId,
//                                                              @RequestParam(defaultValue = "0") @Min(value = 0) int page,
//                                                              @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size) {
////        return new ResponseEntity<>(authService.editPassword(activateDto, true), HttpStatus.OK);
//        return null;
//    }
//    @PutMapping("/update-card/{id}")
//    public ResponseEntity<String> updateCard(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody @Valid UpdateCardDto updateCardDto)
//    {
//        return null;
//    }
//}
