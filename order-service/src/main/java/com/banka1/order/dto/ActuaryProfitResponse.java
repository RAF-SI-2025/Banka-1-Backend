package com.banka1.order.dto;

import java.math.BigDecimal;

public record ActuaryProfitResponse(
        Long id,
        String ime,
        String prezime,
        String role,
        BigDecimal totalProfitRsd
) {
}
