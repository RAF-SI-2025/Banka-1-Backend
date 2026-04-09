package com.banka1.order.dto;

import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for order creation and details.
 */
@Data
public class OrderResponse {
    private Long id;
    private Long userId;
    private Long listingId;
    private OrderType orderType;
    private Integer quantity;
    private Integer contractSize;
    private BigDecimal pricePerUnit;
    private BigDecimal limitValue;
    private BigDecimal stopValue;
    private OrderDirection direction;
    private OrderStatus status;
    private Long approvedBy;
    private Boolean isDone;
    private LocalDateTime lastModification;
    private Integer remainingPortions;
    private Boolean afterHours;
    private Boolean exchangeClosed;
    private Boolean allOrNone;
    private Boolean margin;
    private Long accountId;
    private BigDecimal approximatePrice;
    private BigDecimal fee;
}
