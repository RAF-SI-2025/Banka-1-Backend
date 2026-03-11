package com.banka1.userService.integration;

import com.banka1.userService.domain.ConfirmationToken;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.requests.EmployeeCreateRequestDto;
import com.banka1.userService.dto.requests.EmployeeUpdateRequestDto;
import com.banka1.userService.rabbitMQ.RabbitClient;
import com.banka1.userService.repository.ConfirmationTokenRepository;
import com.banka1.userService.repository.ZaposlenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrudEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private ZaposlenRepository zaposlenRepository;

    @Autowired
    private ConfirmationTokenRepository confirmationTokenRepository;

    @MockitoBean
    private RabbitClient rabbitClient;

    @BeforeEach
    void setUp() {
        confirmationTokenRepository.deleteAll();
        zaposlenRepository.deleteAll();
        doNothing().when(rabbitClient).sendEmailNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchEmployeesReturnsPersistedEmployees() throws Exception {
        zaposlenRepository.save(employee("ana@banka.com", "ana", Role.AGENT));
        zaposlenRepository.save(employee("marko@banka.com", "marko", Role.BASIC));

        mockMvc.perform(get("/employees")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("roles", "BASIC").claim("id", 100L))
                                .authorities(new SimpleGrantedAuthority("ROLE_BASIC")))
                        .param("ime", "Ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ana@banka.com"));
    }

    @Test
    void createEmployeePersistsEmployeeAndConfirmationToken() throws Exception {
        EmployeeCreateRequestDto request = new EmployeeCreateRequestDto();
        request.setIme("Nikola");
        request.setPrezime("Nikolic");
        request.setDatumRodjenja(LocalDate.of(1997, 7, 7));
        request.setPol(Pol.M);
        request.setEmail("nikola@banka.com");
        request.setUsername("nikola");
        request.setPozicija("Agent");
        request.setDepartman("Prodaja");
        request.setRole(Role.BASIC);

        mockMvc.perform(post("/employees")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("roles", "ADMIN").claim("id", 999L))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nikola@banka.com"));

        Zaposlen created = zaposlenRepository.findByEmail("nikola@banka.com").orElseThrow();
        ConfirmationToken confirmationToken = confirmationTokenRepository.findAll().getFirst();

        assertThat(created.getConfirmationToken()).isNotNull();
        assertThat(confirmationToken.getZaposlen().getId()).isEqualTo(created.getId());
    }

    @Test
    void updateEmployeeChangesPersistedData() throws Exception {
        Zaposlen employee = zaposlenRepository.save(employee("jana@banka.com", "jana", Role.BASIC));

        EmployeeUpdateRequestDto request = new EmployeeUpdateRequestDto();
        request.setDepartman("IT");
        request.setPozicija("Senior Agent");
        request.setAktivan(false);

        mockMvc.perform(put("/employees/{id}", employee.getId())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("roles", "ADMIN").claim("id", 500L))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departman").value("IT"))
                .andExpect(jsonPath("$.aktivan").value(false));

        Zaposlen updated = zaposlenRepository.findById(employee.getId()).orElseThrow();
        assertThat(updated.getDepartman()).isEqualTo("IT");
        assertThat(updated.getPozicija()).isEqualTo("Senior Agent");
        assertThat(updated.isAktivan()).isFalse();
    }

    private Zaposlen employee(String email, String username, Role role) {
        Zaposlen employee = new Zaposlen();
        employee.setIme(email.startsWith("ana") ? "Ana" : "Marko");
        employee.setPrezime("Test");
        employee.setDatumRodjenja(LocalDate.of(1991, 1, 1));
        employee.setPol(Pol.M);
        employee.setEmail(email);
        employee.setUsername(username);
        employee.setPozicija("Agent");
        employee.setDepartman("Prodaja");
        employee.setAktivan(true);
        employee.setRole(role);
        return employee;
    }
}
