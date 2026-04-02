package com.banka1.transaction_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NewPaymentResponseDto {
    private String message;
    private String status;
}

