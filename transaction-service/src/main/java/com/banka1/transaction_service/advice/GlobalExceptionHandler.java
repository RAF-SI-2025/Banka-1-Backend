package com.banka1.transaction_service.advice;


import com.banka1.transaction_service.dto.response.ErrorResponseDto;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Global exception handler for all REST controllers.
 * Maps expected and unexpected exceptions to standardized HTTP responses with {@link ErrorResponseDto} body.
 */
@RestControllerAdvice
@Component("transactionServiceGlobalExceptionHandler")
public class GlobalExceptionHandler {

    /**
     * Handles database constraint violation errors (e.g., duplicate unique column).
     *
     * @param ex exception thrown when integrity constraint is violated
     * @return HTTP 409 Conflict response with code {@code ERR_CONSTRAINT_VIOLATION}
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_CONSTRAINT_VIOLATION",
                "Podatak već postoji",
                "Jedan od podataka je već u upotrebi."
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Handles errors when the requested resource does not exist in the collection.
     *
     * @param ex exception thrown when accessing a non-existent element
     * @return HTTP 404 Not Found response with code {@code ERR_NOT_FOUND}
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponseDto> handleNoSuchElement(NoSuchElementException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_NOT_FOUND",
                "Resurs nije pronađen",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles invalid arguments that do not pass program validation.
     *
     * @param ex exception thrown when an invalid argument is detected
     * @return HTTP 400 Bad Request response with code {@code ERR_VALIDATION}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni argumenti",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles communication errors with RabbitMQ broker.
     *
     * @param ex AMQP exception thrown when sending a message
     * @return HTTP 500 Internal Server Error response with code {@code ERR_INTERNAL_SERVER}
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponseDto> handleRabbitMqException(AmqpException ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Mejl nije poslat. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles unexpected exceptions and returns a generic internal server error response.
     *
     * @param ex unexpected exception
     * @return HTTP 500 response with standardized error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception ex) {
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_INTERNAL_SERVER",
                "Serverska greška",
                "Došlo je do neočekivanog problema. Naš tim je obavešten."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

//    /**
//     * Handles known business exceptions and maps them to the appropriate HTTP status.
//     *
//     * @param ex business exception containing domain-specific error code
//     * @return response with business error details and HTTP status from {@link ErrorCode}
//     */
//    @ExceptionHandler(BusinessException.class)
//    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {
//        ErrorCode errorCode = ex.getErrorCode();
//        ErrorResponseDto error = new ErrorResponseDto(
//                errorCode.getCode(),
//                errorCode.getTitle(),
//                ex.getMessage()
//        );
//        return new ResponseEntity<>(error, errorCode.getHttpStatus());
//    }

    /**
     * Handles validation errors for DTO requests and returns a list of invalid fields.
     *
     * @param ex exception thrown during input data validation
     * @return HTTP 400 response with a map of validation errors by field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponseDto error = new ErrorResponseDto(
                "ERR_VALIDATION",
                "Neispravni podaci",
                "Molimo Vas proverite unete podatke.",
                validationErrors
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
