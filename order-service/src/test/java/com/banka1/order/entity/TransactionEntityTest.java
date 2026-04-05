package com.banka1.order.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionEntityTest {

    @Test
    void setFields_storesCorrectly() {
        Transaction tx = new Transaction();
        tx.setOrderId(10L);
        tx.setQuantity(5);
        tx.setPricePerUnit(new BigDecimal("150.00"));
        tx.setTotalPrice(new BigDecimal("750.00"));
        tx.setCommission(new BigDecimal("7.00"));
        tx.setTimestamp(LocalDateTime.of(2025, 1, 1, 12, 0));

        assertThat(tx.getOrderId()).isEqualTo(10L);
        assertThat(tx.getQuantity()).isEqualTo(5);
        assertThat(tx.getPricePerUnit()).isEqualByComparingTo("150.00");
        assertThat(tx.getTotalPrice()).isEqualByComparingTo("750.00");
        assertThat(tx.getCommission()).isEqualByComparingTo("7.00");
        assertThat(tx.getTimestamp()).isEqualTo(LocalDateTime.of(2025, 1, 1, 12, 0));
    }

    @Test
    void prePersist_setsTimestampIfNull() {
        Transaction tx = new Transaction();
        assertThat(tx.getTimestamp()).isNull();

        tx.setTimestamp(null);
        tx.setTimestamp(LocalDateTime.now());

        assertThat(tx.getTimestamp()).isNotNull();
    }

    @Test
    void totalPrice_equalsQuantityTimesPrice() {
        Transaction tx = new Transaction();
        tx.setQuantity(10);
        tx.setPricePerUnit(new BigDecimal("50.00"));
        tx.setTotalPrice(tx.getPricePerUnit().multiply(new BigDecimal(tx.getQuantity())));

        assertThat(tx.getTotalPrice()).isEqualByComparingTo("500.00");
    }
}
