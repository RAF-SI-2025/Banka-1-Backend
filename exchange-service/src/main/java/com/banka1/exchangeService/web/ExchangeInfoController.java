package com.banka1.exchangeService.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ExchangeInfoController {

    @GetMapping("/info")
    public Map<String, Object> info(@RequestHeader(value = "X-Forwarded-Prefix", required = false) String forwardedPrefix) {
        return Map.of(
                "service", "exchange-service",
                "status", "UP",
                "gatewayPrefix", forwardedPrefix == null ? "/api/exchange" : forwardedPrefix
        );
    }
}
