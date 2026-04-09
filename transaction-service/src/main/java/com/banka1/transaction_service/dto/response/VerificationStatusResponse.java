package com.banka1.transaction_service.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO response containing the status of a verification session.
 * Used to check whether the user has successfully passed 2FA verification.
 */
@Getter
@Setter
@NoArgsConstructor
public class VerificationStatusResponse {

    /**
     * ID of the verification session.
     */
    private Long sessionId;

    /**
     * Status of the session: PENDING, VERIFIED, EXPIRED, or CANCELLED.
     */
    private String status;

    /**
     * Checks whether the session is successfully verified.
     *
     * @return true if the status is "VERIFIED", false otherwise
     */
    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }
}
