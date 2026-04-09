package com.banka1.transaction_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for approving a transaction.
 * Contains the necessary information to approve a specific transaction.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ApproveDto {

    /** Unique identifier of the transaction to be approved. */
    @NotBlank(message = "Unesi key")
    private String key;
}
