package com.banka1.account_service.scheduler;

import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.MaintenanceFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Component
@RequiredArgsConstructor
public class Scheduler {

    private final AccountRepository accountRepository;
    private final MaintenanceFeeService maintenanceFeeService;
    /**
     * Svaki dan u ponoć resetuje dailySpending na 0
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailySpending() {
        int updated = accountRepository.resetDailySpending();
        log.info("Daily spending reset executed. Updated accounts: {}", updated);
    }

    /**
     * Svakog prvog u mesecu u ponoc resetuje monthlySpending na 0
     */

    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetMonthlySpending() {
        int updated = accountRepository.resetMonthlySpending();
        log.info("Monthly spending reset executed. Updated accounts: {}", updated);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void run() {
        log.info("Starting monthly maintenance fee job");
        maintenanceFeeService.process();
        log.info("Finished monthly maintenance fee job");
    }

}
