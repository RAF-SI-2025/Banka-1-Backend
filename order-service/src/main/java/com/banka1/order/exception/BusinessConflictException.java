package com.banka1.order.exception;

public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
