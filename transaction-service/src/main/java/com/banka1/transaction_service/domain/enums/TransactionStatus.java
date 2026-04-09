package com.banka1.transaction_service.domain.enums;

/**
 * Enum representing the status of a transaction.
 * <p>
 * Possible values:
 * <ul>
 *   <li>IN_PROGRESS - Transaction is currently being processed</li>
 *   <li>COMPLETED - Transaction has been successfully completed</li>
 *   <li>DENIED - Transaction has been denied or could not be completed</li>
 * </ul>
 */
public enum TransactionStatus {
    /** Transaction is currently being processed */
    IN_PROGRESS,
    /** Transaction has been successfully completed */
    COMPLETED,
    /** Transaction has been denied or could not be completed */
    DENIED
}
