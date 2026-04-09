package com.banka1.clientService.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(10, 60_000L);
    }

    @Test
    void postRequestsWithinLimitAreAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setServletPath("/customers");
        request.setRemoteAddr("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void eleventhPostToCustomersReturns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setServletPath("/customers");
        request.setRemoteAddr("10.0.0.2");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).isEqualTo("Too many requests");
    }

    @Test
    void getRequestToCustomersBypassesRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customers");
        request.setServletPath("/customers");
        request.setRemoteAddr("10.0.0.3");

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void differentIpsHaveSeparateRateLimits() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/customers");
            req.setServletPath("/customers");
            req.setRemoteAddr("192.168.1." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void xForwardedForHeaderIsUsedAsClientIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setServletPath("/customers");
        request.setRemoteAddr("10.0.0.99");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        // Rate limit should be hit for 10.0.0.1 (last/rightmost IP), not for 10.0.0.99
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void blankXForwardedForHeaderFallsBackToRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/customers");
        request.setServletPath("/customers");
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "   ");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        // blank X-Forwarded-For → falls back to remoteAddr, which should now be rate-limited
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void requestsFromDifferentIpsDontInterfereWithEachOther() throws Exception {
        // Fill up limit for IP A
        MockHttpServletRequest reqA = new MockHttpServletRequest("POST", "/customers");
        reqA.setServletPath("/customers");
        reqA.setRemoteAddr("10.100.0.1");
        for (int i = 0; i < 10; i++) {
            filter.doFilter(reqA, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse respA = new MockHttpServletResponse();
        filter.doFilter(reqA, respA, new MockFilterChain());
        assertThat(respA.getStatus()).isEqualTo(429);

        // IP B should still be allowed
        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/customers");
        reqB.setServletPath("/customers");
        reqB.setRemoteAddr("10.100.0.2");
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilter(reqB, respB, new MockFilterChain());
        assertThat(respB.getStatus()).isNotEqualTo(429);
    }
}
