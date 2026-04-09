package com.banka1.order.dto;

import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for the paginated employee list returned by the employee-service.
 * Maps the relevant fields from Spring's {@code Page} response format.
 */
@Data
public class EmployeePageResponse {
    /** Employees on the current page. */
    private List<EmployeeDto> content;
    /** Total number of pages available. */
    private int totalPages;
    /** Total number of matching employees across all pages. */
    private long totalElements;
}
