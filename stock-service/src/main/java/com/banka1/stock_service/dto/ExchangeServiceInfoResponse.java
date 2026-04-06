package com.banka1.stock_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal DTO for the {@code exchange-service} info endpoint response.
 *
 * @param service name of the remote service
 * @param status reported service status
 * @param gatewayPrefix gateway prefix seen by the service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeServiceInfoResponse(
        String service,
        String status,
        String gatewayPrefix
) {
}
