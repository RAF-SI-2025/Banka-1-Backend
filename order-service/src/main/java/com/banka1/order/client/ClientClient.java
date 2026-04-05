package com.banka1.order.client;

import com.banka1.order.dto.CustomerDto;

/**
 * Client interface for communicating with the client-service.
 * Used to retrieve customer data for order authorization and notifications.
 */
public interface ClientClient {

    /**
     * Fetches customer data by their internal identifier.
     *
     * @param id the customer's unique identifier
     * @return customer details including name and email
     */
    CustomerDto getCustomer(Long id);
}
