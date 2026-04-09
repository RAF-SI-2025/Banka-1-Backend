package com.banka1.order.client;

import com.banka1.order.dto.BankAccountDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeClientContractTest {

    @Test
    void getBankAccount_returnsBankAccountDto() throws Exception {
        Method method = EmployeeClient.class.getDeclaredMethod("getBankAccount", String.class);

        assertThat(method.getReturnType()).isEqualTo(BankAccountDto.class);
    }
}
