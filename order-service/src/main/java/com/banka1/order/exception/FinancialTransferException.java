package com.banka1.order.exception;

/**
 * Signals that order execution reached a stage with external financial side effects
 * and automatic retry must stop until the situation is investigated.
 */
public class FinancialTransferException extends RuntimeException {

    public FinancialTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
