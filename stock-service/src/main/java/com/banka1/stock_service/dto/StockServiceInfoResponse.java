package com.banka1.stock_service.dto;

/**
 * DTO describing the basic state of the {@code stock-service} bootstrap endpoint.
 *
 * @param service service name
 * @param status reported service status
 * @param gatewayPrefix gateway prefix received by the service
 * @param exchangeServiceBaseUrl base URL used for the exchange-service integration
 * @param marketDataBaseUrl base URL of the external stock market data provider
 * @param marketDataApiKeyConfigured indicator showing whether the API key is configured
 */
public record StockServiceInfoResponse(
        String service,
        String status,
        String gatewayPrefix,
        String exchangeServiceBaseUrl,
        String marketDataBaseUrl,
        boolean marketDataApiKeyConfigured
) {
}
