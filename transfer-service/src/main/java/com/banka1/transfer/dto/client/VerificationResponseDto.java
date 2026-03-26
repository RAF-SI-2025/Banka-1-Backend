package com.banka1.transfer.dto.client;

/**
 * Rezultat provere verifikacionog koda.
 */
public record VerificationResponseDto(Long sessionId, String status) {
    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }
}