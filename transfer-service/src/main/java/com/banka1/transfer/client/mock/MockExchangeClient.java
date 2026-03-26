package com.banka1.transfer.client.mock;

import com.banka1.transfer.client.ExchangeClient;
import com.banka1.transfer.dto.client.ExchangeResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@Profile("local")
public class MockExchangeClient implements ExchangeClient {
    @Override
    public ExchangeResponseDto calculateExchange(String from, String to, BigDecimal amount) {
        log.info("MOCK: Calculating exchange from {} to {} for amount {}", from, to, amount);
        if(from.equals(to)) return new ExchangeResponseDto(from, to, amount, amount, BigDecimal.ONE, BigDecimal.ZERO, LocalDate.now());

        BigDecimal rate = new BigDecimal("1.05");
        BigDecimal commission = amount.multiply(new BigDecimal("0.01"));
        BigDecimal converted = amount.multiply(rate).subtract(commission);
        return new ExchangeResponseDto(from, to, amount, converted, rate, commission, LocalDate.now());
    }
}
