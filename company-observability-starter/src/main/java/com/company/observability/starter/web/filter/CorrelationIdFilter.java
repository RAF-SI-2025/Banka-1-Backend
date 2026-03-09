package com.company.observability.starter.web.filter;

import com.company.observability.starter.config.ObservabilityProperties;
import com.company.observability.starter.domain.CorrelationContext;
import com.company.observability.starter.service.CorrelationIdService;
import com.company.observability.starter.service.UserIdMdcService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Filter koji cita ili generise correlation Id, upisuje ga u mdc i vraca ga kroz response header
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    private final CorrelationIdService correlationIdService;
    private final ObservabilityProperties observabilityProperties;
    private final UserIdMdcService userIdMdcService;

    public CorrelationIdFilter(CorrelationIdService correlationIdService, ObservabilityProperties observabilityProperties, UserIdMdcService userIdMdcService) {
        this.correlationIdService = correlationIdService;
        this.observabilityProperties = observabilityProperties;
        this.userIdMdcService = userIdMdcService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String headerName = observabilityProperties.getCorrelationHeaderName();
        String incomingCID = request.getHeader(headerName);
        CorrelationContext correlationContext = correlationIdService.resolve(incomingCID);
        MDC.put(MDC_KEY, correlationContext.correlationId());
        userIdMdcService.putUserIdIfPresent();
        response.setHeader(headerName, correlationContext.correlationId());
        try {
            filterChain.doFilter(request, response);
        }finally {
            userIdMdcService.clear();
            MDC.remove(MDC_KEY);
        }
    }
}
