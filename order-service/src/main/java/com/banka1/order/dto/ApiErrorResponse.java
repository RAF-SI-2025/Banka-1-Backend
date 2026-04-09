package com.banka1.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error payload for order-service endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
