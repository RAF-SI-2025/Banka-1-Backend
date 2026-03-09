package com.company.observability.starter.web.filter;

import com.company.observability.starter.domain.RequestLogContext;
import com.company.observability.starter.service.RequestLoggingService;
import com.company.observability.starter.service.SensitiveDataMaskingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class HttpRequestLoggingFilter extends OncePerRequestFilter {
    private final RequestLoggingService requestLoggingService;
    private final SensitiveDataMaskingService maskingService;
    public HttpRequestLoggingFilter(RequestLoggingService requestLoggingService, SensitiveDataMaskingService maskingService) {
        this.requestLoggingService = requestLoggingService;
        this.maskingService = maskingService;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        }finally {
            long durationMs = System.currentTimeMillis() - startTime;
            RequestLogContext logContext = new RequestLogContext(request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, maskingService.maskQuery(request.getQueryString()), maskingService.maskAuthorizationHeader(request.getHeader("Authorization")));
            requestLoggingService.logRequest(logContext);
        }
    }
}
