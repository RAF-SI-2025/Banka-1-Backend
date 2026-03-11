package com.banka1.userService.service.implementation;

import com.banka1.userService.domain.ConfirmationToken;
import com.banka1.userService.domain.RefreshToken;
import com.banka1.userService.domain.Zaposlen;
import com.banka1.userService.domain.enums.Pol;
import com.banka1.userService.domain.enums.Role;
import com.banka1.userService.dto.requests.ActivateDto;
import com.banka1.userService.dto.requests.ForgotPasswordDto;
import com.banka1.userService.dto.requests.LoginRequestDto;
import com.banka1.userService.dto.requests.RefreshTokenRequestDto;
import com.banka1.userService.dto.responses.TokenResponseDto;
import com.banka1.userService.rabbitMQ.RabbitClient;
import com.banka1.userService.repository.ConfirmationTokenRepository;
import com.banka1.userService.repository.TokenRepository;
import com.banka1.userService.repository.ZaposlenRepository;
import com.banka1.userService.security.JWTService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplementationTest {

    @Mock
    private ZaposlenRepository zaposlenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JWTService jwtService;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private ConfirmationTokenRepository confirmationTokenRepository;

    @Mock
    private RabbitClient rabbitClient;

    @InjectMocks
    private AuthServiceImplementation authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "urlResetPassword", "http://localhost/reset?token=");
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void loginReturnsTokensWhenCredentialsAreValid() {
        Zaposlen employee = activeEmployee();
        employee.setPassword("encoded-password");

        when(zaposlenRepository.findByEmail("pera@banka.com")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("Password12", "encoded-password")).thenReturn(true);
        when(jwtService.generateRandomToken()).thenReturn("plain-refresh");
        when(jwtService.sha256Hex("plain-refresh")).thenReturn("hashed-refresh");
        when(jwtService.generateJwtToken(employee)).thenReturn("jwt-token");
        when(tokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponseDto response = authService.login(new LoginRequestDto("pera@banka.com", "Password12"));

        assertThat(response.getJwt()).isEqualTo("jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("plain-refresh");
        verify(tokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshTokenThrowsWhenTokenIsExpired() {
        RefreshToken refreshToken = new RefreshToken(activeEmployee());
        refreshToken.setExpirationDateTime(LocalDateTime.now().minusMinutes(1));

        when(jwtService.sha256Hex("plain-refresh")).thenReturn("hashed-refresh");
        when(tokenRepository.findByValue("hashed-refresh")).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequestDto("plain-refresh")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Pogresan token");
    }

    @Test
    void checkReturnsIdForValidConfirmationToken() {
        ConfirmationToken confirmationToken = new ConfirmationToken();
        confirmationToken.setId(11L);
        confirmationToken.setValue("hashed-token");
        confirmationToken.setZaposlen(activeEmployee());

        String plainToken = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        when(jwtService.sha256Hex(plainToken)).thenReturn("hashed-token");
        when(confirmationTokenRepository.findByValue("hashed-token")).thenReturn(Optional.of(confirmationToken));

        Long id = authService.check(plainToken);

        assertThat(id).isEqualTo(11L);
    }

    @Test
    void editPasswordActivatesUserAndDeletesConfirmationToken() {
        Zaposlen employee = activeEmployee();
        employee.setAktivan(false);

        ConfirmationToken confirmationToken = new ConfirmationToken();
        confirmationToken.setId(7L);
        confirmationToken.setValue("hashed-token");
        confirmationToken.setZaposlen(employee);

        ActivateDto request = new ActivateDto(7L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "Password12");

        when(confirmationTokenRepository.findById(7L)).thenReturn(Optional.of(confirmationToken));
        when(jwtService.sha256Hex(request.getConfirmationToken())).thenReturn("hashed-token");
        when(passwordEncoder.encode("Password12")).thenReturn("encoded-password");

        String response = authService.editPassword(request, true);

        assertThat(response).isEqualTo("Uspesno aktiviranje usera");
        assertThat(employee.isAktivan()).isTrue();
        assertThat(employee.getPassword()).isEqualTo("encoded-password");
        verify(confirmationTokenRepository).delete(confirmationToken);
    }

    @Test
    void forgotPasswordCreatesConfirmationTokenWhenMissing() {
        Zaposlen employee = activeEmployee();
        employee.setConfirmationToken(null);

        when(zaposlenRepository.findByEmail("pera@banka.com")).thenReturn(Optional.of(employee));
        when(jwtService.generateRandomToken()).thenReturn("plain-reset");
        when(jwtService.sha256Hex("plain-reset")).thenReturn("hashed-reset");
        when(confirmationTokenRepository.save(any(ConfirmationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String response = authService.forgotPassword(new ForgotPasswordDto("pera@banka.com"));

        assertThat(response).isEqualTo("Poslat mejl");
        assertThat(employee.getConfirmationToken()).isNotNull();
        assertThat(employee.getConfirmationToken().getValue()).isEqualTo("hashed-reset");
        verify(rabbitClient, never()).sendEmailNotification(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    private Zaposlen activeEmployee() {
        Zaposlen employee = new Zaposlen();
        employee.setId(1L);
        employee.setIme("Pera");
        employee.setPrezime("Peric");
        employee.setDatumRodjenja(LocalDate.of(1990, 1, 1));
        employee.setPol(Pol.M);
        employee.setEmail("pera@banka.com");
        employee.setUsername("pera");
        employee.setPozicija("Agent");
        employee.setDepartman("Prodaja");
        employee.setAktivan(true);
        employee.setRole(Role.AGENT);
        return employee;
    }
}
