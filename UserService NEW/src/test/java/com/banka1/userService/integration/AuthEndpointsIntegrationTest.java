package com.banka1.userService.integration;

import com.banka1.userService.domain.RefreshToken;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.requests.LoginRequestDto;
import com.banka1.userService.dto.requests.RefreshTokenRequestDto;
import com.banka1.userService.rabbitMQ.RabbitClient;
import com.banka1.userService.repository.ConfirmationTokenRepository;
import com.banka1.userService.repository.TokenRepository;
import com.banka1.userService.repository.ZaposlenRepository;
import com.banka1.userService.security.JWTService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private ZaposlenRepository zaposlenRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private ConfirmationTokenRepository confirmationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTService jwtService;

    @MockitoBean
    private RabbitClient rabbitClient;

    @BeforeEach
    void setUp() {
        confirmationTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        zaposlenRepository.deleteAll();
        doNothing().when(rabbitClient).sendEmailNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginEndpointReturnsJwtAndRefreshTokenForPersistedUser() throws Exception {
        Zaposlen employee = activeEmployee("pera@banka.com", "pera", Role.AGENT);
        employee.setPassword(passwordEncoder.encode("Password12"));
        zaposlenRepository.save(employee);

        LoginRequestDto request = new LoginRequestDto("pera@banka.com", "Password12");

        mockMvc.perform(post("/auth/login")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").isString())
                .andExpect(jsonPath("$.refreshToken").isString());

        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    void refreshEndpointRotatesExistingRefreshToken() throws Exception {
        Zaposlen employee = zaposlenRepository.save(activeEmployee("mika@banka.com", "mika", Role.AGENT));
        String rawRefreshToken = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        tokenRepository.save(new RefreshToken(
                jwtService.sha256Hex(rawRefreshToken),
                LocalDateTime.now().plusDays(1),
                employee
        ));

        RefreshTokenRequestDto request = new RefreshTokenRequestDto(rawRefreshToken);

        String rotatedToken = mockMvc.perform(post("/auth/refresh")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(rotatedToken).doesNotContain(rawRefreshToken);
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    private Zaposlen activeEmployee(String email, String username, Role role) {
        Zaposlen employee = new Zaposlen();
        employee.setIme("Pera");
        employee.setPrezime("Peric");
        employee.setDatumRodjenja(LocalDate.of(1990, 1, 1));
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
