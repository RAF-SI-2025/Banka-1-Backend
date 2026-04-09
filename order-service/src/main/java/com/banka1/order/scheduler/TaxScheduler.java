package com.banka1.order.scheduler;

import com.banka1.order.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that runs monthly tax collection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaxScheduler {

    private final TaxService taxService;

    /**
     * Runs at 00:00 on the first day of every month and collects taxes for the previous month.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlyTaxCollection() {
        log.info("Starting monthly tax collection job");
        taxService.collectMonthlyTax();
    }
}

