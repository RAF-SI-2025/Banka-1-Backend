package com.banka1.order.service;

import com.banka1.order.entity.Order;

/**
 * Service for executing approved orders asynchronously in parts.
 */
public interface OrderExecutionService {

    /**
     * Starts the asynchronous execution of an approved order.
     *
     * @param orderId the ID of the order to execute
     */
    void executeOrderAsync(Long orderId);

    /**
     * Executes a single portion of an order.
     *
     * @param order the order to execute a portion of
     */
    void executeOrderPortion(Order order);
}
