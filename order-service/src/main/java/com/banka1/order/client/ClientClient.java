package com.banka1.order.client;

import com.banka1.order.dto.CustomerDto;
import com.banka1.order.dto.CustomerPageResponse;

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

    /**
     * Searches customers using optional first/last-name filters.
     *
     * @param ime first name filter
     * @param prezime last name filter
     * @param page zero-based page index
     * @param size page size
     * @return paginated customer list
     */
    CustomerPageResponse searchCustomers(String ime, String prezime, int page, int size);
}
