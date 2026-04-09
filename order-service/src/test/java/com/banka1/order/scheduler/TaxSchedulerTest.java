package com.banka1.order.scheduler;

import com.banka1.order.OrderServiceApplication;
import com.banka1.order.service.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaxSchedulerTest {

    @Mock
    private TaxService taxService;

    private TaxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaxScheduler(taxService);
    }

    @Test
    void runMonthlyTaxCollection_delegatesToTaxService() {
        scheduler.runMonthlyTaxCollection();

        verify(taxService).collectMonthlyTax();
    }

    @Test
    void runMonthlyTaxCollection_hasExpectedCron() throws Exception {
        Method method = TaxScheduler.class.getDeclaredMethod("runMonthlyTaxCollection");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 0 1 * *");
    }

    @Test
    void schedulingConfiguration_isNotDeclaredOnActuaryScheduler() {
        assertThat(ActuaryScheduler.class.isAnnotationPresent(EnableScheduling.class)).isFalse();
        assertThat(OrderServiceApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
