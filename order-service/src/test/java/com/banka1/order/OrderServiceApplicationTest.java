package com.banka1.order;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void schedulingIsEnabledAtApplicationLevel() {
        assertThat(OrderServiceApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
