package com.banka1.exchangeService.service;

import com.banka1.exchangeService.config.ExchangeRateProperties;
import com.banka1.exchangeService.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler i startup hook za dnevno osvezavanje kurseva.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateScheduler {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateProperties exchangeRateProperties;

    /**
     * Dnevno osvezava kurseve za sve podrzane valute.
     */
    @Scheduled(cron = "${exchange.rates.fetch-cron}")
    public void refreshDailyRates() {
        log.info("Starting scheduled exchange-rate refresh.");
        exchangeRateService.fetchAndStoreDailyRates();
    }

    /**
     * Opcionalno inicijalno puni bazu pri startu aplikacije.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchOnStartup() {
        if (!exchangeRateProperties.fetchOnStartup()) {
            return;
        }
        log.info("Fetching exchange rates on startup.");
        try {
            exchangeRateService.fetchAndStoreDailyRates();
        } catch (BusinessException ex) {
            log.error("Startup exchange-rate fetch failed — service will start without rates. Cause: {}", ex.getMessage());
        }
    }
}
