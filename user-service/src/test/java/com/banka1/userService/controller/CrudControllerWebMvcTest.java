package com.banka1.userService.controller;

import com.banka1.userService.advice.GlobalExceptionHandler;
import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.dto.responses.EmployeeResponseDto;
import com.banka1.userService.service.CrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrudController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class CrudControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private CrudService crudService;

    @Test
    void searchEmployeesReturnsPagedResponse() throws Exception {
        EmployeeResponseDto employee = new EmployeeResponseDto(
                1L, "Ana", "Anic", "ana@banka.com", "ana", "Broker", "Prodaja", true, Role.AGENT
        );

        when(crudService.searchEmployees(eq("Ana"), eq("Anic"), eq("ana@banka.com"), eq("Broker"), eq("Prodaja"), any()))
                .thenReturn(new PageImpl<>(List.of(employee), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/employees")
                        .param("ime", "Ana")
                        .param("prezime", "Anic")
                        .param("email", "ana@banka.com")
                        .param("pozicija", "Broker")
                        .param("departman", "Prodaja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("ana@banka.com"))
                .andExpect(jsonPath("$.content[0].role").value("AGENT"));
    }

    @Test
    void createEmployeeReturnsCreatedForValidPayload() throws Exception {
        EmployeeCreateRequestDto request = validCreateRequest();
        EmployeeResponseDto response = new EmployeeResponseDto(
                10L, "Nikola", "Nikolic", "nikola@banka.com", "nikola", "Agent", "Prodaja", false, Role.BASIC
        );

        when(crudService.createEmployee(any(EmployeeCreateRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.email").value("nikola@banka.com"));
    }

    @Test
    void createEmployeeReturnsValidationErrorsForInvalidPayload() throws Exception {
        EmployeeCreateRequestDto request = new EmployeeCreateRequestDto();

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.validationErrors.ime").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    void updateEmployeeReturnsUpdatedEmployee() throws Exception {
        EmployeeUpdateRequestDto request = new EmployeeUpdateRequestDto();
        request.setDepartman("IT");
        request.setPozicija("Senior Agent");

        EmployeeResponseDto response = new EmployeeResponseDto(
                3L, "Ana", "Anic", "ana@banka.com", "ana", "Senior Agent", "IT", true, Role.AGENT
        );

        when(crudService.updateEmployee(any(), eq(3L), any(EmployeeUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/employees/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pozicija").value("Senior Agent"))
                .andExpect(jsonPath("$.departman").value("IT"));
    }

    @Test
    void deleteEmployeeReturnsNoContent() throws Exception {
        doNothing().when(crudService).deleteEmployee(7L);

        mockMvc.perform(delete("/employees/7"))
                .andExpect(status().isAccepted());
    }

    private EmployeeCreateRequestDto validCreateRequest() {
        EmployeeCreateRequestDto request = new EmployeeCreateRequestDto();
        request.setIme("Nikola");
        request.setPrezime("Nikolic");
        request.setDatumRodjenja(LocalDate.of(1995, 5, 5));
        request.setPol(Pol.M);
        request.setEmail("nikola@banka.com");
        request.setUsername("nikola");
        request.setPozicija("Agent");
        request.setDepartman("Prodaja");
        request.setRole(Role.BASIC);
        return request;
    }
}
