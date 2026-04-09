package com.banka1.credit_service.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerificationStatusResponse {
    private Long sessionId;
    private String status;

    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }
}
