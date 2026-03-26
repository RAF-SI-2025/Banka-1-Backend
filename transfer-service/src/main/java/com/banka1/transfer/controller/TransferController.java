package com.banka1.transfer.controller;

import com.banka1.transfer.dto.requests.TransferRequestDto;
import com.banka1.transfer.dto.responses.TransferResponseDto;
import com.banka1.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Glavni REST kontroler koji upravlja operacijama prenosa sredstava (transferima).
 * Sve rute su mapirane na koren servisa (iza API Gateway-a).
 */
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Validated
public class TransferController {

    private final TransferService transferService;

    /**
     * Kreira novi interni prenos sredstava između računa koji pripadaju istom klijentu.
     * @param jwt token trenutno ulogovanog korisnika
     * @param request podaci o transferu (računi, iznos, 2FA kod)
     * @return DTO sa podacima o izvršenom transferu i brojem naloga
     */
    @PreAuthorize("hasAnyRole('CLIENT_BASIC', 'ADMIN', 'SERVICE')")
    @PostMapping
    public ResponseEntity<TransferResponseDto> executeTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid TransferRequestDto request
    ) {
        TransferResponseDto response = transferService.executeTransfer(jwt, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Dobavlja paginiranu istoriju svih transfera za specifičnog klijenta.
     * Pristup je dozvoljen samo vlasniku podataka ili zaposlenom (ROLE_AGENT/ADMIN).
     * @param jwt token ulogovanog korisnika za proveru ovlašćenja
     * @param clientId ID klijenta čija se istorija potražuje
     * @param page broj stranice (offset)
     * @param size broj elemenata po stranici
     * @return stranica sa istorijom transfera
     */
    @PreAuthorize("hasAnyRole('CLIENT_BASIC', 'ADMIN', 'SERVICE')")
    @GetMapping
    public ResponseEntity<Page<TransferResponseDto>> getClientTransfers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        // Da li klijent traži svoje transfere, ili je zaposleni?
        Long loggedInId = Long.parseLong(jwt.getClaimAsString("id"));
        boolean isEmployee = jwt.getClaimAsStringList("roles").stream().anyMatch(r -> r.contains("ROLE_AGENT") || r.contains("ROLE_ADMIN"));

        if (!isEmployee && !loggedInId.equals(clientId)) {
            return ResponseEntity.status(403).build(); // Klijent ne sme da gleda tuđe
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<TransferResponseDto> response = transferService.getClientTransfers(clientId, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Dobavlja detaljne informacije o jednom transferu na osnovu broja naloga.
     * @param orderNumber jedinstveni broj naloga (npr. TRF-XXXX-XXXX)
     * @return detalji transfera
     */
    @PreAuthorize("hasAnyRole('CLIENT_BASIC', 'ADMIN', 'SERVICE')")
    @GetMapping("/{orderNumber}")
    public ResponseEntity<TransferResponseDto> getTransferDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNumber
    ) {

        TransferResponseDto response = transferService.getTransferDetails(jwt, orderNumber);
        // NOTE: Ovde bi takođe trebala autorizacija da klijent A ne može po orderNumberu
        // da otvori transfer klijenta B. To ćemo rešiti validacijom u servisu.
        return ResponseEntity.ok(response);
    }

    /**
     * Dobavlja paginiranu istoriju transfera vezanih za određeni broj računa.
     * Rezultat uključuje transfere gde je račun bio ili pošiljalac ili primalac.
     * @param accountNumber broj bankovnog računa
     * @param page broj stranice
     * @param size broj elemenata po stranici
     * @return stranica sa podacima o transferima za traženi račun
     */
    @PreAuthorize("hasAnyRole('CLIENT_BASIC', 'ADMIN', 'SERVICE')")
    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<Page<TransferResponseDto>> getAccountTransfers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<TransferResponseDto> response = transferService.getTransfersByAccountNumber(jwt, accountNumber, pageable);

        return ResponseEntity.ok(response);
    }
}
