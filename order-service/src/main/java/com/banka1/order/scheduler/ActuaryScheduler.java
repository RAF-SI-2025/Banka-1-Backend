package com.banka1.order.scheduler;

import com.banka1.order.service.ActuaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for actuary limit management.
 * Runs the daily limit reset automatically at 23:59 every day,
 * as specified in the Celina 3 actuary specification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActuaryScheduler {

    private final ActuaryService actuaryService;

    /**
     * Resets the {@code usedLimit} to zero for every agent record.
     * Runs every day at 23:59:00.
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void resetDailyLimits() {
        log.info("Running daily usedLimit reset for all agents.");
        actuaryService.resetAllLimits();
    }
}
