package com.company.observability.starter.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// servis za logovanje neobradjenih gresaka
public class ExceptionLoggingService {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingService.class);
    private final SensitiveDataMaskingService maskingService;
    public ExceptionLoggingService(SensitiveDataMaskingService maskingService) {
        this.maskingService = maskingService;
    }
    public void logUnhandledException(Exception exception, HttpServletRequest request) {
        String maskedQuery = maskingService.maskQuery(request.getQueryString());
        String maskedAuthorization = maskingService.maskAuthorizationHeader(
                request.getHeader("Authorization")
        );
        log.error(
                "Unhandled exception method={} uri={} query={} authorization={}",
                request.getMethod(),
                request.getRequestURI(),
                maskedQuery,
                maskedAuthorization,
                exception // automatski loguje stack trace
        );
    }
}
