package com.banka1.order.dto;

import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for paginated customer list responses returned by client-service.
 */
@Data
public class CustomerPageResponse {
    private List<CustomerDto> content;
    private int totalPages;
    private long totalElements;
}
