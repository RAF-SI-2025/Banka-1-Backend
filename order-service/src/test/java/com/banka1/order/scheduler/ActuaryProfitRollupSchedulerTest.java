package com.banka1.order.scheduler;

import com.banka1.order.service.BankProfitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActuaryProfitRollupSchedulerTest {

    @Mock
    private BankProfitService bankProfitService;

    private ActuaryProfitRollupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ActuaryProfitRollupScheduler(bankProfitService);
    }

    @Test
    void refreshActuaryProfitRollup_delegatesToService() {
        scheduler.refreshActuaryProfitRollup();

        verify(bankProfitService).refreshActuaryProfitRollup();
    }

    @Test
    void refreshActuaryProfitRollup_hasExpectedSchedule() throws Exception {
        Method method = ActuaryProfitRollupScheduler.class.getDeclaredMethod("refreshActuaryProfitRollup");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${bank-profit.actuary-rollup-refresh-ms:300000}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${bank-profit.actuary-rollup-initial-delay-ms:0}");
    }
}
