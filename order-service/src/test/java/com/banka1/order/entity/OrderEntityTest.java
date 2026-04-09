package com.banka1.order.entity;

import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEntityTest {

    @Test
    void defaultValues_areCorrect() {
        Order order = new Order();

        assertThat(order.getIsDone()).isFalse();
    }

    @Test
    void setFields_storesCorrectly() {
        Order order = new Order();
        order.setUserId(1L);
        order.setListingId(42L);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(10);
        order.setContractSize(1);
        order.setPricePerUnit(new BigDecimal("100.00"));
        order.setDirection(OrderDirection.BUY);
        order.setStatus(OrderStatus.PENDING);
        order.setIsDone(false);
        order.setRemainingPortions(10);
        order.setAfterHours(false);
        order.setAllOrNone(false);
        order.setMargin(false);
        order.setAccountId(5L);

        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getListingId()).isEqualTo(42L);
        assertThat(order.getOrderType()).isEqualTo(OrderType.MARKET);
        assertThat(order.getQuantity()).isEqualTo(10);
        assertThat(order.getPricePerUnit()).isEqualByComparingTo("100.00");
        assertThat(order.getDirection()).isEqualTo(OrderDirection.BUY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getIsDone()).isFalse();
        assertThat(order.getAccountId()).isEqualTo(5L);
    }

    @Test
    void limitOrder_hasLimitValue() {
        Order order = new Order();
        order.setOrderType(OrderType.LIMIT);
        order.setLimitValue(new BigDecimal("95.00"));

        assertThat(order.getLimitValue()).isEqualByComparingTo("95.00");
        assertThat(order.getStopValue()).isNull();
    }

    @Test
    void stopOrder_hasStopValue() {
        Order order = new Order();
        order.setOrderType(OrderType.STOP);
        order.setStopValue(new BigDecimal("80.00"));

        assertThat(order.getStopValue()).isEqualByComparingTo("80.00");
        assertThat(order.getLimitValue()).isNull();
    }

    @Test
    void stopLimitOrder_hasBothValues() {
        Order order = new Order();
        order.setOrderType(OrderType.STOP_LIMIT);
        order.setStopValue(new BigDecimal("80.00"));
        order.setLimitValue(new BigDecimal("78.00"));

        assertThat(order.getStopValue()).isEqualByComparingTo("80.00");
        assertThat(order.getLimitValue()).isEqualByComparingTo("78.00");
    }

    @Test
    void approvedBy_isNullableByDefault() {
        Order order = new Order();

        assertThat(order.getApprovedBy()).isNull();
    }

    @Test
    void allOrderStatusValues_exist() {
        assertThat(OrderStatus.values()).containsExactlyInAnyOrder(
                OrderStatus.PENDING_CONFIRMATION,
                OrderStatus.PENDING,
                OrderStatus.APPROVED,
                OrderStatus.DECLINED,
                OrderStatus.DONE,
                OrderStatus.CANCELLED
        );
    }

    @Test
    void allOrderTypeValues_exist() {
        assertThat(OrderType.values()).containsExactlyInAnyOrder(
                OrderType.MARKET,
                OrderType.LIMIT,
                OrderType.STOP,
                OrderType.STOP_LIMIT
        );
    }

    @Test
    void allOrderDirectionValues_exist() {
        assertThat(OrderDirection.values()).containsExactlyInAnyOrder(
                OrderDirection.BUY,
                OrderDirection.SELL
        );
    }
}
