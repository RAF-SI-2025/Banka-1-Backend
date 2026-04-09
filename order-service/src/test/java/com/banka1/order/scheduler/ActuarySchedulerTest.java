package com.banka1.order.scheduler;

import com.banka1.order.service.ActuaryService;
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
class ActuarySchedulerTest {

    @Mock
    private ActuaryService actuaryService;

    private ActuaryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ActuaryScheduler(actuaryService);
    }

    @Test
    void resetDailyLimits_delegatesToService() {
        scheduler.resetDailyLimits();

        verify(actuaryService).resetAllLimits();
    }

    @Test
    void resetDailyLimits_hasExpectedCron() throws Exception {
        Method method = ActuaryScheduler.class.getDeclaredMethod("resetDailyLimits");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 59 23 * * *");
    }
}
