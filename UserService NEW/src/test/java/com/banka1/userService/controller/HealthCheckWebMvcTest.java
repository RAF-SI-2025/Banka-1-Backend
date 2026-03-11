package com.banka1.userService.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthCheck.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HealthCheckWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthCheckReturnsAlive() throws Exception {
        mockMvc.perform(get("/healthCheck"))
                .andExpect(status().isOk())
                .andExpect(content().string("Alive"));
    }
}
