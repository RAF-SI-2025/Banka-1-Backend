package com.banka1.account_service.domain.enums;

/**
 * Enumeracija koja predstavlja moguće stanja sesije verifikacije.
 * Prati životni ciklus od kreiranja do završetka ili neuspjeha.
 */
public enum VerificationStatus {
    /** Sesija kreirana i čeka validaciju koda. */
    PENDING,
    /** Kod uspješno validiran i sesija završena. */
    VERIFIED,
    /** Sesija istekla zbog vremenskog ograničenja (5 minuta). */
    EXPIRED,
    /** Sesija otkazana zbog previše neuspjelih pokušaja (3+). */
    CANCELLED
}
