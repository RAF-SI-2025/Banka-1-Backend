package com.banka1.order.advice;

import com.banka1.order.dto.ApiErrorResponse;
import com.banka1.order.exception.BadRequestException;
import com.banka1.order.exception.BusinessConflictException;
import com.banka1.order.exception.ForbiddenOperationException;
import com.banka1.order.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global REST exception handler for order-service.
 */
@RestControllerAdvice
public class OrderServiceExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        if (ex instanceof MethodArgumentNotValidException validationEx) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            for (FieldError fieldError : validationEx.getBindingResult().getFieldErrors()) {
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return build(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
        }
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({BusinessConflictException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleConflict(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }
}
