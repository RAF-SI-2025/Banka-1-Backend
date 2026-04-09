package com.banka1.order.scheduler;

import com.banka1.order.service.OrderCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically resolves expired pending orders so they do not remain indefinitely pending.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredPendingOrderScheduler {

    private final OrderCreationService orderCreationService;

    @Scheduled(cron = "0 */15 * * * *")
    public void autoDeclineExpiredPendingOrders() {
        log.info("Checking for expired pending orders");
        orderCreationService.autoDeclineExpiredPendingOrders();
    }
}
