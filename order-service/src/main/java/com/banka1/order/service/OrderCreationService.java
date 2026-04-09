package com.banka1.order.service;

import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.CreateBuyOrderRequest;
import com.banka1.order.dto.CreateSellOrderRequest;
import com.banka1.order.dto.OrderOverviewResponse;
import com.banka1.order.dto.OrderResponse;
import com.banka1.order.entity.enums.OrderOverviewStatusFilter;

import java.util.List;

/**
 * Service for creating buy and sell orders.
 */
public interface OrderCreationService {

    /**
     * Creates a buy order with validation and approval logic.
     *
     * @param userId the ID of the user placing the order
     * @param request the buy order request
     * @return the created order response
     */
    OrderResponse createBuyOrder(AuthenticatedUser user, CreateBuyOrderRequest request);

    /**
     * Creates a sell order with validation and approval logic.
     *
     * @param userId the ID of the user placing the order
     * @param request the sell order request
     * @return the created order response
     */
    OrderResponse createSellOrder(AuthenticatedUser user, CreateSellOrderRequest request);

    /**
     * Returns the supervisor portal overview of orders with optional status filtering.
     *
     * @param statusFilter the requested status filter
     * @return overview rows for the portal
     */
    List<OrderOverviewResponse> getOrders(OrderOverviewStatusFilter statusFilter);

    /**
     * Confirms a draft order and finalizes validation, approval state, and fee transfer.
     *
     * @param user the authenticated owner of the order
     * @param orderId the order to confirm
     * @return the updated order response
     */
    OrderResponse confirmOrder(AuthenticatedUser user, Long orderId);

    /**
     * Cancels a not-yet-completed order.
     *
     * @param user the authenticated owner of the order
     * @param orderId the order to cancel
     * @return the updated order response
     */
    OrderResponse cancelOrder(AuthenticatedUser user, Long orderId);

    /**
     * Cancels only the remaining unexecuted portion of an order for supervisor portal actions.
     *
     * @param orderId the order to cancel
     * @return the updated order response
     */
    OrderResponse cancelOrder(Long orderId);

    /**
     * Cancels the remaining unfilled portion, or part of it, for supervisor actions.
     *
     * @param orderId the order to cancel
     * @param quantityToCancel null to cancel all remaining quantity, otherwise the quantity to cancel from the remainder
     * @return the updated order response
     */
    OrderResponse cancelOrder(Long orderId, Integer quantityToCancel);

    /**
     * Approves a pending actuary order.
     *
     * @param supervisorId the approving supervisor
     * @param orderId the order to approve
     * @return the updated order response
     */
    OrderResponse approveOrder(Long supervisorId, Long orderId);

    /**
     * Declines a pending actuary order.
     *
     * @param supervisorId the declining supervisor
     * @param orderId the order to decline
     * @return the updated order response
     */
    OrderResponse declineOrder(Long supervisorId, Long orderId);

    /**
     * Automatically declines pending orders whose settlement date has passed.
     */
    void autoDeclineExpiredPendingOrders();
}
