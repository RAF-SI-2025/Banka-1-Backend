package com.banka1.transaction_service.exception;

import lombok.Getter;

/**
 * Exception thrown for expected business logic errors.
 * Contains a structured {@link ErrorCode} that the {@code GlobalExceptionHandler} maps
 * to the appropriate HTTP status and response body.
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Structured error code describing the type and severity of the business error.
     */
    private final ErrorCode errorCode;

    /**
     * Creates a business exception with the associated error code and detailed message.
     *
     * @param errorCode standardized code for domain-specific errors
     * @param detailedMessage detailed message for logging and client response
     */
    public BusinessException(ErrorCode errorCode, String detailedMessage) {
        super(detailedMessage);
        this.errorCode = errorCode;
    }
}
