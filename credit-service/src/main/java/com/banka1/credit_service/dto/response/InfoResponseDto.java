package com.banka1.credit_service.dto.response;

import com.banka1.credit_service.domain.enums.CurrencyCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class InfoResponseDto {
    private CurrencyCode fromCurrencyCode;
    private CurrencyCode toCurrencyCode;
    private Long fromVlasnik;
    private Long toVlasnik;
    private String fromEmail;
    private String fromUsername;
}
