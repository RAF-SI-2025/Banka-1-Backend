package com.banka1.order.client;

import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;

/**
 * Client interface for communicating with the employee-service.
 * Used to retrieve employee data and filter agents for actuary management.
 */
public interface EmployeeClient {

    /**
     * Fetches a single employee by their identifier.
     *
     * @param id the employee's unique identifier
     * @return employee details including role and position
     */
    EmployeeDto getEmployee(Long id);

    /**
     * Searches employees using optional filters with pagination support.
     *
     * @param email    optional email filter
     * @param ime      optional first name filter (Serbian field name matching the API)
     * @param prezime  optional last name filter (Serbian field name matching the API)
     * @param pozicija optional position filter (Serbian field name matching the API)
     * @param page     zero-based page index
     * @param size     number of results per page
     * @return paginated list of matching employees
     */
    EmployeePageResponse searchEmployees(String email, String ime, String prezime, String pozicija, int page, int size);

    /**
     * Gets the bank account for a given currency.
     *
     * @param currency the currency code
     * @return bank account reference DTO
     */
    BankAccountDto getBankAccount(String currency);
}
