package com.banka1.account_service.scheduler;

import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.MaintenanceFeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MaintenanceFeeService maintenanceFeeService;

    @Test
    void resetDailySpendingDelegatesToRepository() {
        Scheduler scheduler = new Scheduler(accountRepository, maintenanceFeeService);
        when(accountRepository.resetDailySpending()).thenReturn(5);

        scheduler.resetDailySpending();

        verify(accountRepository).resetDailySpending();
    }

    @Test
    void resetMonthlySpendingDelegatesToRepository() {
        Scheduler scheduler = new Scheduler(accountRepository, maintenanceFeeService);
        when(accountRepository.resetMonthlySpending()).thenReturn(10);

        scheduler.resetMonthlySpending();

        verify(accountRepository).resetMonthlySpending();
    }

    @Test
    void runDelegatesToMaintenanceFeeService() {
        Scheduler scheduler = new Scheduler(accountRepository, maintenanceFeeService);

        scheduler.run();

        verify(maintenanceFeeService).process();
    }
}

