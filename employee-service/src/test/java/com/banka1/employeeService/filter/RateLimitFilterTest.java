package com.banka1.employeeService.filter;

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
        filter = new RateLimitFilter();
    }

    @Test
    void requestsWithinLimitAreAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setRemoteAddr("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void eleventhRequestOnLoginReturns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
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
    void eleventhRequestOnForgotPasswordReturns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/forgot-password");
        request.setServletPath("/auth/forgot-password");
        request.setRemoteAddr("10.0.0.3");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void requestsToNonRateLimitedPathBypassFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/employees");
        request.setServletPath("/employees");
        request.setRemoteAddr("10.0.0.4");

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void differentIpsHaveSeparateRateLimits() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
            req.setServletPath("/auth/login");
            req.setRemoteAddr("192.168.1." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void xForwardedForHeaderIsUsedAsClientIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setRemoteAddr("10.0.0.99");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        // The rate limit should be reached for 203.0.113.5, not for 10.0.0.99
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void eleventhRequestOnRefreshReturns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/refresh");
        request.setServletPath("/auth/refresh");
        request.setRemoteAddr("10.0.1.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void rateLimitBodyContainsTooManyRequestsMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setRemoteAddr("10.2.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).isEqualTo("Too many requests");
    }

    @Test
    void requestsFromDifferentIpsDontInterfereWithEachOther() throws Exception {
        // Fill up limit for IP A
        MockHttpServletRequest reqA = new MockHttpServletRequest("POST", "/auth/login");
        reqA.setServletPath("/auth/login");
        reqA.setRemoteAddr("10.100.0.1");
        for (int i = 0; i < 10; i++) {
            filter.doFilter(reqA, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse respA = new MockHttpServletResponse();
        filter.doFilter(reqA, respA, new MockFilterChain());
        assertThat(respA.getStatus()).isEqualTo(429);

        // IP B should still be allowed
        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/auth/login");
        reqB.setServletPath("/auth/login");
        reqB.setRemoteAddr("10.100.0.2");
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilter(reqB, respB, new MockFilterChain());
        assertThat(respB.getStatus()).isNotEqualTo(429);
    }
}
