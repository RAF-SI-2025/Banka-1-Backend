package com.company.observability.starter.web;

import com.company.observability.starter.service.ExceptionLoggingService;
import com.company.observability.starter.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ExceptionLoggingService exceptionLoggingService;
    public GlobalExceptionHandler(ExceptionLoggingService exceptionLoggingService) {
        this.exceptionLoggingService = exceptionLoggingService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, HttpServletRequest request) {
        exceptionLoggingService.logUnhandledException(e, request);
        ErrorResponse response = new ErrorResponse(OffsetDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "Unexpected server error", MDC.get(CorrelationIdFilter.MDC_KEY), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}
