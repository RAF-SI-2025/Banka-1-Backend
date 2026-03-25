package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.domain.enums.VerificationStatus;
import com.banka1.account_service.dto.request.EditAccountLimitDto;
import com.banka1.account_service.dto.request.EditAccountNameDto;
import com.banka1.account_service.dto.request.EditStatus;
import com.banka1.account_service.dto.response.AccountDetailsResponseDto;
import com.banka1.account_service.dto.response.AccountResponseDto;
import com.banka1.account_service.dto.response.CardResponseDto;
import com.banka1.account_service.dto.response.ValidateResponse;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.rabbitMQ.RabbitClient;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.rest_client.CardServiceRestClient;
import com.banka1.account_service.rest_client.RestClientService;
import com.banka1.account_service.rest_client.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplementationTest {

    @Mock private AccountRepository accountRepository;
    @Mock private VerificationService verificationService;
    @Mock private RabbitClient rabbitClient;
    @Mock private RestClientService restClientService;
    @Mock private CardServiceRestClient cardServiceRestClient;

    private ClientServiceImplementation service;

    private static final Currency RSD = new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);
    private static final long OWNER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImplementation(accountRepository, verificationService, rabbitClient,
                restClientService, cardServiceRestClient);
        ReflectionTestUtils.setField(service, "appPropertiesId", "userId");
    }

    // ──────────────────── findMyAccounts ────────────────────

    @Test
    void findMyAccountsReturnsMappedPage() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findByVlasnikAndStatus(eq(OWNER_ID), eq(Status.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ca)));

        Page<AccountResponseDto> result = service.findMyAccounts(jwt(OWNER_ID), 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBrojRacuna()).isEqualTo("111000110000000011");
        assertThat(result.getContent().get(0).getAccountCategory()).isEqualTo("CHECKING");
    }

    // ──────────────────── editAccountName (by id) ────────────────────

    @Test
    void editAccountNameByIdUpdatesName() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        when(accountRepository.existsByVlasnikAndNazivRacuna(OWNER_ID, "Novo ime")).thenReturn(false);

        String result = service.editAccountName(jwt(OWNER_ID), 1L, new EditAccountNameDto("Novo ime"));

        assertThat(result).isEqualTo("Uspesno editovano ime");
        assertThat(ca.getNazivRacuna()).isEqualTo("Novo ime");
    }

    @Test
    void editAccountNameByIdThrowsWhenSameName() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatThrownBy(() -> service.editAccountName(jwt(OWNER_ID), 1L, new EditAccountNameDto("Moj racun")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ime ne sme biti isto");
    }

    @Test
    void editAccountNameByIdThrowsWhenDuplicateName() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        when(accountRepository.existsByVlasnikAndNazivRacuna(OWNER_ID, "Drugi racun")).thenReturn(true);

        assertThatThrownBy(() -> service.editAccountName(jwt(OWNER_ID), 1L, new EditAccountNameDto("Drugi racun")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vlasnik poseduje racun sa ovim imenom");
    }

    @Test
    void editAccountNameByIdThrowsWhenNotOwner() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatThrownBy(() -> service.editAccountName(jwt(999L), 1L, new EditAccountNameDto("X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nisi vlasnik racuna");
    }

    @Test
    void editAccountNameByIdThrowsWhenAccountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editAccountName(jwt(OWNER_ID), 99L, new EditAccountNameDto("X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ne postoji unet racun");
    }

    // ──────────────────── editAccountName (by account number) ────────────────────

    @Test
    void editAccountNameByNumberUpdatesName() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));
        when(accountRepository.existsByVlasnikAndNazivRacuna(OWNER_ID, "Novi naziv")).thenReturn(false);

        String result = service.editAccountName(jwt(OWNER_ID), "111000110000000011", new EditAccountNameDto("Novi naziv"));

        assertThat(result).isEqualTo("Uspesno editovano ime");
    }

    // ──────────────────── editAccountLimit ────────────────────

    @Test
    void editAccountLimitByIdUpdatesLimits() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        ValidateResponse ok = new ValidateResponse(true, VerificationStatus.VERIFIED, 0);
        when(verificationService.validate(any())).thenReturn(ok);

        EditAccountLimitDto dto = new EditAccountLimitDto(
                new BigDecimal("500"), new BigDecimal("10000"), "123456", 77L);

        String result = service.editAccountLimit(jwt(OWNER_ID), 1L, dto);

        assertThat(result).isEqualTo("Uspesno setovani limiti");
        assertThat(ca.getDnevniLimit()).isEqualByComparingTo("500");
        assertThat(ca.getMesecniLimit()).isEqualByComparingTo("10000");
    }

    @Test
    void editAccountLimitThrowsWhenDailyExceedsMonthly() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));

        EditAccountLimitDto dto = new EditAccountLimitDto(
                new BigDecimal("20000"), new BigDecimal("10000"), "123456", 77L);

        assertThatThrownBy(() -> service.editAccountLimit(jwt(OWNER_ID), 1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dnevni limit mora biti manji ili jednak od mesecnog");
    }

    @Test
    void editAccountLimitThrowsWhenVerificationFails() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        ValidateResponse fail = new ValidateResponse(false, VerificationStatus.PENDING, 2);
        when(verificationService.validate(any())).thenReturn(fail);

        EditAccountLimitDto dto = new EditAccountLimitDto(
                new BigDecimal("500"), new BigDecimal("10000"), "wrong", 77L);

        assertThatThrownBy(() -> service.editAccountLimit(jwt(OWNER_ID), 1L, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void editAccountLimitThrowsWhenVerificationReturnsNull() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        when(verificationService.validate(any())).thenReturn(null);

        EditAccountLimitDto dto = new EditAccountLimitDto(
                new BigDecimal("500"), new BigDecimal("10000"), "x", 77L);

        assertThatThrownBy(() -> service.editAccountLimit(jwt(OWNER_ID), 1L, dto))
                .isInstanceOf(BusinessException.class);
    }

    // ──────────────────── getDetails ────────────────────

    @Test
    void getDetailsByIdReturnsDto() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));

        AccountDetailsResponseDto result = service.getDetails(jwt(OWNER_ID), 1L);

        assertThat(result.getBrojRacuna()).isEqualTo("111000110000000011");
        assertThat(result.getVlasnik()).isEqualTo(OWNER_ID);
    }

    @Test
    void getDetailsByIdThrowsWhenNotOwner() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));

        assertThatThrownBy(() -> service.getDetails(jwt(999L), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nisi vlasnik racuna");
    }

    @Test
    void getDetailsByAccountNumberReturnsDto() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        AccountDetailsResponseDto result = service.getDetails(jwt(OWNER_ID), "111000110000000011");

        assertThat(result.getBrojRacuna()).isEqualTo("111000110000000011");
    }

    // ──────────────────── editStatus ────────────────────

    @Test
    void editStatusUpdatesAccountStatusAndRegistersSync() {
        CheckingAccount ca = activeCheckingAccount();
        ca.setEmail("vlasnik@test.com");
        ca.setUsername("vlasnik");
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
               .thenAnswer(inv -> null);

            String result = service.editStatus(jwt(OWNER_ID), "111000110000000011", new EditStatus(Status.INACTIVE));

            assertThat(result).isEqualTo("Uspesno editovan status");
            assertThat(ca.getStatus()).isEqualTo(Status.INACTIVE);
        }
    }

    @Test
    void editStatusInactiveSendsEmailAndCardEventAfterCommit() {
        CheckingAccount ca = activeCheckingAccount();
        ca.setEmail("vlasnik@test.com");
        ca.setUsername("vlasnik");
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                    .thenAnswer(inv -> {
                        TransactionSynchronization synchronization = inv.getArgument(0);
                        synchronization.afterCommit();
                        return null;
                    });

            service.editStatus(jwt(OWNER_ID), "111000110000000011", new EditStatus(Status.INACTIVE));

            verify(rabbitClient).sendEmailNotification(any());
            verify(rabbitClient).sendCardEvent(any());
        }
    }

    @Test
    void editStatusInactiveWithoutContactSkipsEmailButSendsCardEvent() {
        CheckingAccount ca = activeCheckingAccount();
        ca.setEmail(null);
        ca.setUsername(null);
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                    .thenAnswer(inv -> {
                        TransactionSynchronization synchronization = inv.getArgument(0);
                        synchronization.afterCommit();
                        return null;
                    });

            service.editStatus(jwt(OWNER_ID), "111000110000000011", new EditStatus(Status.INACTIVE));

            verify(rabbitClient, never()).sendEmailNotification(any());
            verify(rabbitClient).sendCardEvent(any());
        }
    }

    @Test
    void editStatusActiveSendsNoDeactivationNotifications() {
        CheckingAccount ca = activeCheckingAccount();
        ca.setEmail("vlasnik@test.com");
        ca.setUsername("vlasnik");
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                    .thenAnswer(inv -> {
                        TransactionSynchronization synchronization = inv.getArgument(0);
                        synchronization.afterCommit();
                        return null;
                    });

            service.editStatus(jwt(OWNER_ID), "111000110000000011", new EditStatus(Status.ACTIVE));

            verify(rabbitClient, never()).sendEmailNotification(any());
            verify(rabbitClient, never()).sendCardEvent(any());
        }
    }

    @Test
    void editStatusThrowsWhenAccountNotFound() {
        when(accountRepository.findByBrojRacuna("999999999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editStatus(jwt(OWNER_ID), "999999999999999999", new EditStatus(Status.INACTIVE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ne postoji racun");
    }

    // ──────────────────── findAllCards ────────────────────

    @Test
    void findAllCardsReturnsPaged() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        CardResponseDto card = new CardResponseDto();
        when(cardServiceRestClient.getCardsForAccount("111000110000000011")).thenReturn(List.of(card));

        Page<CardResponseDto> result = service.findAllCards(jwt(OWNER_ID), 1L, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findAllCardsReturnsEmptyPageWhenOffsetOutOfBounds() {
        CheckingAccount ca = activeCheckingAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(ca));
        when(cardServiceRestClient.getCardsForAccount("111000110000000011")).thenReturn(List.of());

        Page<CardResponseDto> result = service.findAllCards(jwt(OWNER_ID), 1L, 5, 10);

        assertThat(result.getContent()).isEmpty();
    }

    // ──────────────────── helpers ────────────────────

    private CheckingAccount activeCheckingAccount() {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna("111000110000000011");
        ca.setImeVlasnikaRacuna("Pera");
        ca.setPrezimeVlasnikaRacuna("Peric");
        ca.setNazivRacuna("Moj racun");
        ca.setVlasnik(OWNER_ID);
        ca.setZaposlen(1L);
        ca.setCurrency(RSD);
        ca.setDnevniLimit(new BigDecimal("250000"));
        ca.setMesecniLimit(new BigDecimal("1000000"));
        return ca;
    }

    private Jwt jwt(Long userId) {
        return Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("userId", userId)
                .build();
    }
}
