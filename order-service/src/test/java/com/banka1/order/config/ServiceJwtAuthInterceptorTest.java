package com.banka1.order.config;

import com.banka1.order.security.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceJwtAuthInterceptorTest {

    @Test
    void repeatedRequestsReuseTokenWhileStillValid() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-08T10:00:00Z"));
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.generateJwtToken()).thenReturn("token-1");

        ServiceJwtAuthInterceptor interceptor = new ServiceJwtAuthInterceptor(jwtService, 60_000L, clock);

        execute(interceptor);
        clock.advanceSeconds(10);
        execute(interceptor);

        verify(jwtService, times(1)).generateJwtToken();
    }

    @Test
    void tokenIsRegeneratedWhenRefreshThresholdIsReached() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-08T10:00:00Z"));
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.generateJwtToken()).thenReturn("token-1", "token-2");

        ServiceJwtAuthInterceptor interceptor = new ServiceJwtAuthInterceptor(jwtService, 60_000L, clock);

        execute(interceptor);
        clock.advanceSeconds(55);
        HttpHeaders headers = execute(interceptor);

        verify(jwtService, times(2)).generateJwtToken();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-2");
    }

    private HttpHeaders execute(ServiceJwtAuthInterceptor interceptor) throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();

        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
        when(execution.execute(request, new byte[0])).thenReturn(response);

        interceptor.intercept(request, new byte[0], execution);
        return headers;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
