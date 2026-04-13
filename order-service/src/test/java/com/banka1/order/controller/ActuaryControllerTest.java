package com.banka1.order.controller;

import com.banka1.order.dto.ActuaryAgentDto;
import com.banka1.order.dto.SetLimitRequestDto;
import com.banka1.order.dto.SimpleResponse;
import com.banka1.order.service.ActuaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ActuaryController.
 * Verifies that the controller properly delegates to the ActuaryService
 * and returns appropriate responses.
 */
@ExtendWith(MockitoExtension.class)
class ActuaryControllerTest {

    private ActuaryController controller;

    @Mock
    private ActuaryService actuaryService;

    @BeforeEach
    void setUp() {
        controller = new ActuaryController(actuaryService);
    }

    @Test
    void getAgents_returnsPageOfActuaryAgentDto() {
        ActuaryAgentDto agent1 = new ActuaryAgentDto();
        agent1.setEmployeeId(1L);
        agent1.setEmail("agent1@bank.com");
        agent1.setLimit(new BigDecimal("1000000"));

        ActuaryAgentDto agent2 = new ActuaryAgentDto();
        agent2.setEmployeeId(2L);
        agent2.setEmail("agent2@bank.com");
        agent2.setLimit(new BigDecimal("2000000"));

        Page<ActuaryAgentDto> agents = new PageImpl<>(List.of(agent1, agent2), PageRequest.of(0, 10), 2);
        when(actuaryService.getAgents(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(agents);

        ResponseEntity<Page<ActuaryAgentDto>> response = controller.getAgents(null, null, null, null, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getContent().get(0).getEmployeeId()).isEqualTo(1L);
        assertThat(response.getBody().getContent().get(1).getEmployeeId()).isEqualTo(2L);
    }

    @Test
    void getAgents_withEmailFilter_delegatesToService() {
        Page<ActuaryAgentDto> agents = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(actuaryService.getAgents("john@bank.com", null, null, null, PageRequest.of(0, 10)))
                .thenReturn(agents);

        controller.getAgents("john@bank.com", null, null, null, 0, 10);

        verify(actuaryService).getAgents("john@bank.com", null, null, null, PageRequest.of(0, 10));
    }

    @Test
    void getAgents_withMultipleFilters_delegatesToService() {
        Page<ActuaryAgentDto> agents = new PageImpl<>(List.of(), PageRequest.of(1, 5), 0);
        when(actuaryService.getAgents("john@bank.com", "John", "Doe", "Agent", PageRequest.of(1, 5)))
                .thenReturn(agents);

        controller.getAgents("john@bank.com", "John", "Doe", "Agent", 1, 5);

        verify(actuaryService).getAgents("john@bank.com", "John", "Doe", "Agent", PageRequest.of(1, 5));
    }

    @Test
    void getAgents_withDefaultPagination() {
        Page<ActuaryAgentDto> agents = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(actuaryService.getAgents(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(agents);

        controller.getAgents(null, null, null, null, 0, 10);

        verify(actuaryService).getAgents(null, null, null, null, PageRequest.of(0, 10));
    }

    @Test
    void setLimit_returns200OnSuccess() {
        SetLimitRequestDto request = new SetLimitRequestDto();
        request.setLimit(new BigDecimal("5000000"));

        ResponseEntity<SimpleResponse> response = controller.setLimit(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("success");
        verify(actuaryService).setLimit(1L, request);
    }

    @Test
    void setLimit_delegatesToServiceWithCorrectParameters() {
        SetLimitRequestDto request = new SetLimitRequestDto();
        request.setLimit(new BigDecimal("3500000"));

        controller.setLimit(42L, request);

        verify(actuaryService).setLimit(42L, request);
    }

    @Test
    void setLimit_withDifferentLimitValues() {
        SetLimitRequestDto request1 = new SetLimitRequestDto();
        request1.setLimit(new BigDecimal("1000000"));

        SetLimitRequestDto request2 = new SetLimitRequestDto();
        request2.setLimit(new BigDecimal("10000000"));

        controller.setLimit(1L, request1);
        controller.setLimit(2L, request2);

        verify(actuaryService).setLimit(1L, request1);
        verify(actuaryService).setLimit(2L, request2);
    }

    @Test
    void resetLimit_returns200OnSuccess() {
        ResponseEntity<Void> response = controller.resetLimit(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(actuaryService).resetLimit(1L);

    }

    @Test
    void resetLimit_delegatesToServiceWithCorrectEmployeeId() {
        controller.resetLimit(99L);

        verify(actuaryService).resetLimit(99L);
    }

    @Test
    void resetLimit_multipleTimes() {
        controller.resetLimit(1L);
        controller.resetLimit(2L);
        controller.resetLimit(3L);

        verify(actuaryService).resetLimit(1L);
        verify(actuaryService).resetLimit(2L);
        verify(actuaryService).resetLimit(3L);
    }

    @Test
    void getAgents_returnsEmptyPageWhenNoAgentsFound() {
        Page<ActuaryAgentDto> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(actuaryService.getAgents(null, null, null, null, PageRequest.of(0, 10)))
                .thenReturn(emptyPage);

        ResponseEntity<Page<ActuaryAgentDto>> response = controller.getAgents(null, null, null, null, 0, 10);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isEqualTo(0);
    }

    @Test
    void getAgents_supportsLargePaginationSize() {
        Page<ActuaryAgentDto> agents = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(actuaryService.getAgents(null, null, null, null, PageRequest.of(0, 100)))
                .thenReturn(agents);

        ResponseEntity<Page<ActuaryAgentDto>> response = controller.getAgents(null, null, null, null, 0, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(actuaryService).getAgents(null, null, null, null, PageRequest.of(0, 100));
    }
}



