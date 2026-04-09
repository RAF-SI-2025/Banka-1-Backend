package com.banka1.order.dto;

import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ payload for supervisor order decisions.
 *
 * <p>Fields mirror the notification-service NotificationRequest contract while
 * keeping order-specific metadata available in the same payload.</p>
 */
@Data
public class OrderNotificationPayload {
    private Long orderId;
    private OrderStatus status;
    private Long userId;
    private Long supervisorId;
    private Long listingId;
    private OrderType orderType;
    private OrderDirection direction;
    private String username;
    private String userEmail;
    private Map<String, String> templateVariables = new LinkedHashMap<>();
}
