package com.banka1.order.service;

import com.banka1.order.client.EmployeeClient;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import com.banka1.order.dto.SetLimitRequestDto;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.repository.ActuaryInfoRepository;
import com.banka1.order.service.impl.ActuaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActuaryServiceTest {

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    @Mock
    private EmployeeClient employeeClient;

    @InjectMocks
    private ActuaryServiceImpl actuaryService;

    private EmployeeDto agentEmployee;
    private EmployeeDto adminEmployee;
    private ActuaryInfo actuaryInfo;

    @BeforeEach
    void setUp() {
        agentEmployee = new EmployeeDto();
        agentEmployee.setId(1L);
        agentEmployee.setIme("Marko");
        agentEmployee.setPrezime("Markovic");
        agentEmployee.setEmail("marko@banka.com");
        agentEmployee.setPozicija("Agent");
        agentEmployee.setRole("AGENT");

        adminEmployee = new EmployeeDto();
        adminEmployee.setId(2L);
        adminEmployee.setRole("ADMIN");

        actuaryInfo = new ActuaryInfo();
        actuaryInfo.setEmployeeId(1L);
        actuaryInfo.setLimit(new BigDecimal("100000.00"));
        actuaryInfo.setUsedLimit(new BigDecimal("15000.00"));
        actuaryInfo.setReservedLimit(new BigDecimal("8000.00"));
        actuaryInfo.setNeedApproval(false);
    }

    @Test
    void getAgents_returnsOnlyAgents() {
        EmployeeDto supervisor = new EmployeeDto();
        supervisor.setId(3L);
        supervisor.setRole("SUPERVISOR");

        EmployeePageResponse page = new EmployeePageResponse();
        page.setContent(List.of(agentEmployee, supervisor));
        page.setTotalPages(1);

        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(page);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(actuaryInfo));

        var result = actuaryService.getAgents(null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
        assertThat(result.get(0).getLimit()).isEqualByComparingTo("100000.00");
    }

    @Test
    void getAgents_createsActuaryInfoIfMissing() {
        EmployeePageResponse page = new EmployeePageResponse();
        page.setContent(List.of(agentEmployee));
        page.setTotalPages(1);

        ActuaryInfo newInfo = new ActuaryInfo();
        newInfo.setEmployeeId(1L);
        newInfo.setUsedLimit(BigDecimal.ZERO);
        newInfo.setNeedApproval(false);

        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(page);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
        when(actuaryInfoRepository.save(any())).thenReturn(newInfo);

        var result = actuaryService.getAgents(null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getAgents_traversesMultiplePagesAndKeepsOnlyAgents() {
        EmployeeDto secondAgent = new EmployeeDto();
        secondAgent.setId(4L);
        secondAgent.setIme("Jelena");
        secondAgent.setPrezime("Jelic");
        secondAgent.setRole("AGENT");

        EmployeeDto nonAgent = new EmployeeDto();
        nonAgent.setId(5L);
        nonAgent.setRole("SUPERVISOR");

        EmployeePageResponse page0 = new EmployeePageResponse();
        page0.setContent(List.of(agentEmployee, nonAgent));
        page0.setTotalPages(2);

        EmployeePageResponse page1 = new EmployeePageResponse();
        page1.setContent(List.of(secondAgent));
        page1.setTotalPages(2);

        ActuaryInfo secondInfo = new ActuaryInfo();
        secondInfo.setEmployeeId(4L);
        secondInfo.setUsedLimit(BigDecimal.ZERO);
        secondInfo.setNeedApproval(false);

        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(page0);
        when(employeeClient.searchEmployees(null, null, null, null, 1, 100)).thenReturn(page1);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(actuaryInfo));
        when(actuaryInfoRepository.findByEmployeeId(4L)).thenReturn(Optional.of(secondInfo));

        var result = actuaryService.getAgents(null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("employeeId").containsExactlyInAnyOrder(1L, 4L);
        verify(employeeClient).searchEmployees(null, null, null, null, 0, 100);
        verify(employeeClient).searchEmployees(null, null, null, null, 1, 100);
        verify(employeeClient, never()).searchEmployees(null, null, null, null, 0, 200);
    }

    @Test
    void setLimit_updatesAgentLimit() {
        SetLimitRequestDto request = new SetLimitRequestDto();
        request.setLimit(new BigDecimal("50000.00"));

        when(employeeClient.getEmployee(1L)).thenReturn(agentEmployee);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(actuaryInfo));
        when(actuaryInfoRepository.save(any())).thenReturn(actuaryInfo);

        actuaryService.setLimit(1L, request);

        assertThat(actuaryInfo.getLimit()).isEqualByComparingTo("50000.00");
        verify(actuaryInfoRepository).save(actuaryInfo);
    }

    @Test
    void setLimit_throwsWhenTargetIsAdmin() {
        SetLimitRequestDto request = new SetLimitRequestDto();
        request.setLimit(new BigDecimal("50000.00"));

        when(employeeClient.getEmployee(2L)).thenReturn(adminEmployee);

        assertThatThrownBy(() -> actuaryService.setLimit(2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void resetLimit_setsUsedLimitToZero() {
        when(employeeClient.getEmployee(1L)).thenReturn(agentEmployee);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(actuaryInfo));
        when(actuaryInfoRepository.save(any())).thenReturn(actuaryInfo);

        actuaryService.resetLimit(1L);

        assertThat(actuaryInfo.getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(actuaryInfo.getReservedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(actuaryInfoRepository).save(actuaryInfo);
    }

    @Test
    void resetLimit_throwsWhenTargetIsAdmin() {
        when(employeeClient.getEmployee(2L)).thenReturn(adminEmployee);

        assertThatThrownBy(() -> actuaryService.resetLimit(2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetAllLimits_resetsEveryRecord() {
        ActuaryInfo info1 = new ActuaryInfo();
        info1.setUsedLimit(new BigDecimal("5000.00"));
        info1.setReservedLimit(new BigDecimal("1200.00"));

        ActuaryInfo info2 = new ActuaryInfo();
        info2.setUsedLimit(new BigDecimal("12000.00"));
        info2.setReservedLimit(new BigDecimal("3400.00"));

        when(actuaryInfoRepository.findAll()).thenReturn(List.of(info1, info2));

        actuaryService.resetAllLimits();

        assertThat(info1.getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(info1.getReservedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(info2.getUsedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(info2.getReservedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(actuaryInfoRepository).saveAll(List.of(info1, info2));
    }
}
