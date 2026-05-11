package com.banka1.order.scheduler;

import com.banka1.order.service.BankProfitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActuaryProfitRollupScheduler {

    private final BankProfitService bankProfitService;

    @Scheduled(
            fixedDelayString = "${bank-profit.actuary-rollup-refresh-ms:300000}",
            initialDelayString = "${bank-profit.actuary-rollup-initial-delay-ms:0}"
    )
    public void refreshActuaryProfitRollup() {
        log.info("Refreshing actuary profit rollup.");
        bankProfitService.refreshActuaryProfitRollup();
    }
}
