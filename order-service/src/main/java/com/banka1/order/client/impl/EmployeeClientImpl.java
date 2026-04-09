package com.banka1.order.client.impl;

import com.banka1.order.client.EmployeeClient;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * RestClient-based implementation of {@link EmployeeClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class EmployeeClientImpl implements EmployeeClient {

    private final RestClient employeeRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeDto getEmployee(Long id) {
        return employeeRestClient.get()
                .uri("/employees/{id}", id)
                .retrieve()
                .body(EmployeeDto.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeePageResponse searchEmployees(String email, String ime, String prezime, String pozicija, int page, int size) {
        return employeeRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/employees")
                        .queryParamIfPresent("email", Optional.ofNullable(email))
                        .queryParamIfPresent("ime", Optional.ofNullable(ime))
                        .queryParamIfPresent("prezime", Optional.ofNullable(prezime))
                        .queryParamIfPresent("pozicija", Optional.ofNullable(pozicija))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(EmployeePageResponse.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BankAccountDto getBankAccount(String currency) {
        return employeeRestClient.get()
                .uri("/employee/accounts/bank/{currency}", currency)
                .retrieve()
                .body(BankAccountDto.class);
    }
}
