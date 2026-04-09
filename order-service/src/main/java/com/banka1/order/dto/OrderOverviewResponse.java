package com.banka1.order.dto;

import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Supervisor portal response row for the order overview screen.
 */
@Data
public class OrderOverviewResponse {
    private Long orderId;
    private String agentName;
    private OrderType orderType;
    private ListingType listingType;
    private Integer quantity;
    private Integer contractSize;
    private BigDecimal pricePerUnit;
    private OrderDirection direction;
    private Integer remainingPortions;
    private OrderStatus status;
}
