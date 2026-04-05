package com.banka1.order.service.impl;

import com.banka1.order.client.EmployeeClient;
import com.banka1.order.dto.ActuaryAgentDto;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import com.banka1.order.dto.SetLimitRequestDto;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.repository.ActuaryInfoRepository;
import com.banka1.order.service.ActuaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Default implementation of {@link ActuaryService}.
 * Combines employee data from employee-service with local {@link ActuaryInfo} records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActuaryServiceImpl implements ActuaryService {

    private final ActuaryInfoRepository actuaryInfoRepository;
    private final EmployeeClient employeeClient;

    /**
     * {@inheritDoc}
     * Fetches up to 200 employees per page from employee-service, filters to AGENT role,
     * and merges with local actuary limit data.
     */
    @Override
    public List<ActuaryAgentDto> getAgents(String email, String ime, String prezime, String pozicija) {
        EmployeePageResponse page = employeeClient.searchEmployees(email, ime, prezime, pozicija, 0, 200);

        return page.getContent().stream()
                .filter(emp -> "AGENT".equals(emp.getRole()))
                .map(emp -> {
                    ActuaryInfo info = actuaryInfoRepository.findByEmployeeId(emp.getId())
                            .orElseGet(() -> createDefaultActuaryInfo(emp.getId()));
                    return toDto(emp, info);
                })
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setLimit(Long employeeId, SetLimitRequestDto request) {
        EmployeeDto employee = employeeClient.getEmployee(employeeId);

        if ("ADMIN".equals(employee.getRole())) {
            throw new IllegalArgumentException("Cannot change the limit of an admin.");
        }
        if (!"AGENT".equals(employee.getRole())) {
            throw new IllegalArgumentException("Limit can only be set for employees with the AGENT role.");
        }

        ActuaryInfo info = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> createDefaultActuaryInfo(employeeId));

        info.setLimit(request.getLimit());
        actuaryInfoRepository.save(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void resetLimit(Long employeeId) {
        EmployeeDto employee = employeeClient.getEmployee(employeeId);

        if ("ADMIN".equals(employee.getRole())) {
            throw new IllegalArgumentException("Cannot reset the limit of an admin.");
        }

        ActuaryInfo info = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> createDefaultActuaryInfo(employeeId));

        info.setUsedLimit(BigDecimal.ZERO);
        actuaryInfoRepository.save(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void resetAllLimits() {
        log.info("Resetting usedLimit for all actuary records.");
        List<ActuaryInfo> all = actuaryInfoRepository.findAll();
        for (ActuaryInfo info : all) {
            info.setUsedLimit(BigDecimal.ZERO);
        }
        actuaryInfoRepository.saveAll(all);
    }

    /**
     * Creates and persists a new {@link ActuaryInfo} with default values for the given employee.
     * Used when an agent appears in employee-service but has no local actuary record yet.
     *
     * @param employeeId the employee's identifier
     * @return the newly saved actuary record
     */
    private ActuaryInfo createDefaultActuaryInfo(Long employeeId) {
        ActuaryInfo info = new ActuaryInfo();
        info.setEmployeeId(employeeId);
        info.setUsedLimit(BigDecimal.ZERO);
        info.setNeedApproval(false);
        return actuaryInfoRepository.save(info);
    }

    /**
     * Maps an employee DTO and its actuary info to the combined response DTO.
     *
     * @param emp  employee data from employee-service
     * @param info local actuary limit record
     * @return combined agent DTO
     */
    private ActuaryAgentDto toDto(EmployeeDto emp, ActuaryInfo info) {
        ActuaryAgentDto dto = new ActuaryAgentDto();
        dto.setEmployeeId(emp.getId());
        dto.setIme(emp.getIme());
        dto.setPrezime(emp.getPrezime());
        dto.setEmail(emp.getEmail());
        dto.setPozicija(emp.getPozicija());
        dto.setLimit(info.getLimit());
        dto.setUsedLimit(info.getUsedLimit());
        dto.setNeedApproval(info.getNeedApproval());
        return dto;
    }
}
