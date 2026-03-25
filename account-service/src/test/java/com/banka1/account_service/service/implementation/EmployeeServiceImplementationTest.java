package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Company;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.FxAccount;
import com.banka1.account_service.domain.SifraDelatnosti;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.FxDto;
import com.banka1.account_service.dto.request.UpdateCompanyDto;
import com.banka1.account_service.dto.response.AccountDetailsResponseDto;
import com.banka1.account_service.dto.response.AccountSearchResponseDto;
import com.banka1.account_service.dto.response.ClientInfoResponseDto;
import com.banka1.account_service.dto.response.CompanyResponseDto;
import com.banka1.account_service.rabbitMQ.RabbitClient;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.repository.CompanyRepository;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.repository.SifraDelatnostiRepository;
import com.banka1.account_service.rest_client.CardServiceRestClient;
import com.banka1.account_service.rest_client.RestClientService;
import com.banka1.account_service.util.AccountNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplementationTest {

    @Mock private RestClientService restClientService;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private SifraDelatnostiRepository sifraDelatnostiRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RabbitClient rabbitClient;
    @Mock private CardServiceRestClient cardServiceRestClient;

    @Captor private ArgumentCaptor<Account> accountCaptor;

    private EmployeeServiceImplementation service;

    private static final Currency EUR =
            new Currency("Euro", CurrencyCode.EUR, "EUR", Set.of("EU"), "desc", Status.ACTIVE);
    private static final Currency RSD =
            new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);

    @BeforeEach
    void setUp() {
        service = new EmployeeServiceImplementation(
                42L,
                restClientService,
                currencyRepository,
                sifraDelatnostiRepository,
                companyRepository,
                accountRepository,
                rabbitClient,
                cardServiceRestClient
        );
        ReflectionTestUtils.setField(service, "appPropertiesId", "employeeId");
    }

    @Test
    void createFxAccountSavesAccountAndReturnsDto() {
        FxDto dto = new FxDto("Devizni", 10L, null, CurrencyCode.EUR, AccountOwnershipType.PERSONAL,
                new BigDecimal("1000.00"), false, null);

        when(currencyRepository.findByOznaka(CurrencyCode.EUR)).thenReturn(Optional.of(EUR));
        when(restClientService.getUser(10L))
                .thenReturn(new ClientInfoResponseDto(10L, "Ana", "Anic", "ana", "ana@test.com"));
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager
                    .registerSynchronization(any(TransactionSynchronization.class)))
               .thenAnswer(inv -> null);

            AccountDetailsResponseDto result = service.createFxAccount(jwt(900L), dto);

            assertThat(result).isNotNull();
            assertThat(result.getAccountCategory()).isEqualTo("FOREIGN_CURRENCY");
            assertThat(result.getVlasnik()).isEqualTo(10L);

            verify(accountRepository).save(accountCaptor.capture());
            Account saved = accountCaptor.getValue();
            assertThat(saved).isInstanceOf(FxAccount.class);
            assertThat(saved.getVlasnik()).isEqualTo(10L);
            assertThat(saved.getZaposlen()).isEqualTo(900L);
            assertThat(saved.getImeVlasnikaRacuna()).isEqualTo("Ana");
            assertThat(saved.getBrojRacuna()).hasSize(19).matches("\\d{19}");
            assertThat(AccountNumberGenerator.validateAccountNumber(saved.getBrojRacuna())).isTrue();
        }
    }

    @Test
    void createFxAccountRejectsRsdCurrency() {
        FxDto dto = new FxDto("Racun", 10L, null, CurrencyCode.RSD, AccountOwnershipType.PERSONAL,
                new BigDecimal("100"), false, null);

        assertThatThrownBy(() -> service.createFxAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ne moze RSD");
    }

    @Test
    void createFxAccountRejectsMissingOwner() {
        FxDto dto = new FxDto("Racun", null, " ", CurrencyCode.EUR, AccountOwnershipType.PERSONAL,
                new BigDecimal("100"), false, null);

        assertThatThrownBy(() -> service.createFxAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unesi id ili jmbg");
    }

    @Test
    void createFxAccountRejectsInactiveCurrency() {
        FxDto dto = new FxDto("Racun", 5L, null, CurrencyCode.EUR, AccountOwnershipType.PERSONAL,
                new BigDecimal("100"), false, null);
        Currency inactive = new Currency("Euro", CurrencyCode.EUR, "EUR", Set.of(), "desc", Status.INACTIVE);
        when(currencyRepository.findByOznaka(CurrencyCode.EUR)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createFxAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Deaktivirana valuta");
    }

    @Test
    void createFxAccountRejectsWhenCurrencyNotFound() {
        FxDto dto = new FxDto("Racun", 5L, null, CurrencyCode.EUR, AccountOwnershipType.PERSONAL,
                new BigDecimal("100"), false, null);
        when(currencyRepository.findByOznaka(CurrencyCode.EUR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFxAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nisu unete valute");
    }

    @Test
    void createCheckingAccountSavesRsdPersonalAccount() {
        CheckingDto dto = new CheckingDto("Tekuci", 42L, null, AccountConcrete.STANDARDNI, null,
                new BigDecimal("5000"), false);

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(restClientService.getUser(42L))
                .thenReturn(new ClientInfoResponseDto(42L, "Pera", "Peric", "pera", "pera@test.com"));
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsm.when(() -> TransactionSynchronizationManager
                    .registerSynchronization(any(TransactionSynchronization.class)))
               .thenAnswer(inv -> null);

            AccountDetailsResponseDto result = service.createCheckingAccount(jwt(901L), dto);

            assertThat(result).isNotNull();
            assertThat(result.getAccountCategory()).isEqualTo("CHECKING");

            verify(accountRepository).save(accountCaptor.capture());
            Account saved = accountCaptor.getValue();
            assertThat(saved).isInstanceOf(CheckingAccount.class);
            assertThat(saved.getVlasnik()).isEqualTo(42L);
            assertThat(saved.getZaposlen()).isEqualTo(901L);
            assertThat(saved.getBrojRacuna()).hasSize(19).matches("\\d{19}");
            assertThat(AccountNumberGenerator.validateAccountNumber(saved.getBrojRacuna())).isTrue();
            assertThat(saved.getCompany()).isNull();
        }
    }

    @Test
    void createCheckingAccountRejectsMissingOwner() {
        CheckingDto dto = new CheckingDto("Tekuci", null, null, AccountConcrete.STANDARDNI, null,
                new BigDecimal("100"), false);

        assertThatThrownBy(() -> service.createCheckingAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unesi id ili jmbg");
    }

    @Test
    void createCheckingAccountRejectsBusinessTypeWithoutFirma() {
        CheckingDto dto = new CheckingDto("Tekuci", 5L, null, AccountConcrete.DOO, null,
                new BigDecimal("100"), false);

        assertThatThrownBy(() -> service.createCheckingAccount(jwt(1L), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pogresan tip racuna");
    }

    @Test
    void searchAllAccountsTrimsInputsAndReturnsMappedPage() {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna("111000110000000011");
        ca.setImeVlasnikaRacuna("Zika");
        ca.setPrezimeVlasnikaRacuna("Zoric");

        FxAccount fa = new FxAccount(AccountOwnershipType.PERSONAL);
        fa.setBrojRacuna("111000120000000021");
        fa.setImeVlasnikaRacuna("Ana");
        fa.setPrezimeVlasnikaRacuna("Andric");

        when(accountRepository.searchAccounts(eq("NUM"), eq("Ime"), eq("Prezime"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ca, fa)));

        Page<AccountSearchResponseDto> page =
                service.searchAllAccounts(jwt(1L), " Ime ", " Prezime ", " NUM ", 0, 10);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getTekuciIliDevizni()).isEqualTo("tekuci");
        assertThat(page.getContent().get(1).getTekuciIliDevizni()).isEqualTo("devizni");
        assertThat(page.getContent().get(0).getIme()).isEqualTo("Zika");
        assertThat(page.getContent().get(1).getPrezime()).isEqualTo("Andric");
    }

    @Test
    void getBankAccountsReturnsList() {
        CheckingAccount bank = bankRsdAccount("111000110000000099");
        when(accountRepository.findAllBankAccounts()).thenReturn(List.of(bank));

        List<AccountDetailsResponseDto> result = service.getBankAccounts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountCategory()).isEqualTo("CHECKING");
    }

    @Test
    void getBankAccountByCurrencyReturnsDto() {
        FxAccount fa = new FxAccount(AccountOwnershipType.PERSONAL);
        fa.setBrojRacuna("111000120000000021");
        fa.setImeVlasnikaRacuna("Banka");
        fa.setPrezimeVlasnikaRacuna("Banka");
        fa.setNazivRacuna("Bank EUR");
        fa.setVlasnik(-1L);
        fa.setZaposlen(1L);
        fa.setCurrency(EUR);

        when(accountRepository.findBankAccountByCurrencyCode(CurrencyCode.EUR)).thenReturn(Optional.of(fa));

        AccountDetailsResponseDto result = service.getBankAccountByCurrency(CurrencyCode.EUR);

        assertThat(result).isNotNull();
        assertThat(result.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void getBankAccountByCurrencyThrowsWhenNotFound() {
        when(accountRepository.findBankAccountByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBankAccountByCurrency(CurrencyCode.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void getAccountDetailsReturnsDto() {
        CheckingAccount ca = bankRsdAccount("111000110000000011");
        ca.setVlasnik(5L);
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(ca));

        AccountDetailsResponseDto result = service.getAccountDetails("111000110000000011");

        assertThat(result.getBrojRacuna()).isEqualTo("111000110000000011");
        assertThat(result.getVlasnik()).isEqualTo(5L);
    }

    @Test
    void getAccountDetailsThrowsWhenNotFound() {
        when(accountRepository.findByBrojRacuna("999999999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccountDetails("999999999999999999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999999999999999");
    }

    @Test
    void getClientAccountsReturnsPaged() {
        CheckingAccount ca = bankRsdAccount("111000110000000011");
        ca.setVlasnik(7L);
        when(accountRepository.findByVlasnikAndStatus(eq(7L), eq(Status.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ca)));

        Page<AccountDetailsResponseDto> result = service.getClientAccounts(7L, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getVlasnik()).isEqualTo(7L);
    }

    @Test
    void getCompanyReturnsDto() {
        SifraDelatnosti sifra = mock(SifraDelatnosti.class);
        when(sifra.getSifra()).thenReturn("6419");
        Company company = new Company("Firma d.o.o.", "12345678", "123456789", sifra, "Adresa 1", 10L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));

        CompanyResponseDto result = service.getCompany(1L);

        assertThat(result.getNaziv()).isEqualTo("Firma d.o.o.");
        assertThat(result.getSifraDelatnosti()).isEqualTo("6419");
    }

    @Test
    void getCompanyThrowsWhenNotFound() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompany(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateCompanyUpdatesFields() {
        SifraDelatnosti oldSifra = mock(SifraDelatnosti.class);
        SifraDelatnosti newSifra = mock(SifraDelatnosti.class);
        when(newSifra.getSifra()).thenReturn("7010");
        Company company = new Company("Staro", "12345678", "123456789", oldSifra, "Stara adresa", 10L);

        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(sifraDelatnostiRepository.findBySifra("7010")).thenReturn(Optional.of(newSifra));
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCompanyDto dto = new UpdateCompanyDto("Novo ime", "7010", "Nova adresa", 20L);
        CompanyResponseDto result = service.updateCompany(1L, dto);

        assertThat(result.getNaziv()).isEqualTo("Novo ime");
        assertThat(result.getSifraDelatnosti()).isEqualTo("7010");
        assertThat(result.getAdresa()).isEqualTo("Nova adresa");
        assertThat(result.getVlasnik()).isEqualTo(20L);
    }

    @Test
    void updateCompanyThrowsWhenSifraNotFound() {
        SifraDelatnosti sifra = mock(SifraDelatnosti.class);
        Company company = new Company("Firma", "12345678", "123456789", sifra, "Adresa", 1L);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(sifraDelatnostiRepository.findBySifra("INVALID")).thenReturn(Optional.empty());

        UpdateCompanyDto dto = new UpdateCompanyDto("Ime", "INVALID", null, null);

        assertThatThrownBy(() -> service.updateCompany(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    private CheckingAccount bankRsdAccount(String broj) {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna(broj);
        ca.setImeVlasnikaRacuna("Banka");
        ca.setPrezimeVlasnikaRacuna("Banka");
        ca.setNazivRacuna("Racun");
        ca.setVlasnik(-1L);
        ca.setZaposlen(1L);
        ca.setCurrency(RSD);
        return ca;
    }

    private Jwt jwt(Long employeeId) {
        return Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("employeeId", employeeId)
                .build();
    }
}
