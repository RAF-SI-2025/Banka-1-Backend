package com.banka1.order.scheduler;

import com.banka1.order.service.OrderCreationService;
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
class ExpiredPendingOrderSchedulerTest {

    @Mock
    private OrderCreationService orderCreationService;

    private ExpiredPendingOrderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExpiredPendingOrderScheduler(orderCreationService);
    }

    @Test
    void autoDeclineExpiredPendingOrders_delegatesToOrderCreationService() {
        scheduler.autoDeclineExpiredPendingOrders();

        verify(orderCreationService).autoDeclineExpiredPendingOrders();
    }

    @Test
    void autoDeclineExpiredPendingOrders_hasExpectedCron() throws Exception {
        Method method = ExpiredPendingOrderScheduler.class.getDeclaredMethod("autoDeclineExpiredPendingOrders");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 */15 * * * *");
    }
}
