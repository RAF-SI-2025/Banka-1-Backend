package com.banka1.transaction_service.domain.enums;

/**
 * Enum representing possible states of the verification session.
 * Tracks the lifecycle from creation to completion or failure.
 */
public enum VerificationStatus {
    /** Session created and waiting for code validation. */
    PENDING,
    /** Code successfully validated and session completed. */
    VERIFIED,
    /** Session expired due to time limit (5 minutes). */
    EXPIRED,
    /** Session cancelled due to too many failed attempts (3+). */
    CANCELLED
}
