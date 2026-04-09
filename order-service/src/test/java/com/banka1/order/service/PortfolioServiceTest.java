package com.banka1.order.service;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.PortfolioSummaryResponse;
import com.banka1.order.dto.SetPublicQuantityRequestDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OptionType;
import com.banka1.order.exception.BadRequestException;
import com.banka1.order.exception.BusinessConflictException;
import com.banka1.order.exception.ForbiddenOperationException;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.service.impl.PortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private StockClient stockClient;
    @Mock
    private AccountClient accountClient;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private ExchangeClient exchangeClient;
    @Mock
    private TaxService taxService;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private AuthenticatedUser clientUser;
    private AuthenticatedUser actuaryUser;
    private AuthenticatedUser supervisorUser;
    private Portfolio stockPortfolio;
    private Portfolio optionPortfolio;

    @BeforeEach
    void setUp() {
        clientUser = new AuthenticatedUser(1L, Set.of("CLIENT"), Set.of());
        actuaryUser = new AuthenticatedUser(1L, Set.of("AGENT"), Set.of());
        supervisorUser = new AuthenticatedUser(1L, Set.of("SUPERVISOR"), Set.of());

        stockPortfolio = new Portfolio();
        stockPortfolio.setId(1L);
        stockPortfolio.setUserId(1L);
        stockPortfolio.setListingId(100L);
        stockPortfolio.setListingType(ListingType.STOCK);
        stockPortfolio.setQuantity(10);
        stockPortfolio.setAveragePurchasePrice(BigDecimal.valueOf(100));
        stockPortfolio.setPublicQuantity(2);
        stockPortfolio.setLastModified(LocalDateTime.now().minusDays(1));

        optionPortfolio = new Portfolio();
        optionPortfolio.setId(2L);
        optionPortfolio.setUserId(1L);
        optionPortfolio.setListingId(200L);
        optionPortfolio.setListingType(ListingType.OPTION);
        optionPortfolio.setQuantity(2);
        optionPortfolio.setAveragePurchasePrice(BigDecimal.valueOf(12));
        optionPortfolio.setLastModified(LocalDateTime.now().minusHours(2));

        lenient().when(taxService.getCurrentYearPaidTax(1L)).thenReturn(new BigDecimal("15.00"));
        lenient().when(taxService.getCurrentMonthUnpaidTax(1L)).thenReturn(new BigDecimal("5.00"));
    }

    @Test
    void getPortfolio_returnsHoldingsSummaryWithTaxAndExercisableFlag() {
        when(portfolioRepository.findByUserId(1L)).thenReturn(List.of(stockPortfolio, optionPortfolio));

        StockListingDto stockListing = new StockListingDto();
        stockListing.setId(100L);
        stockListing.setListingType(ListingType.STOCK);
        stockListing.setPrice(BigDecimal.valueOf(150));
        stockListing.setTicker("AAPL");

        StockListingDto optionListing = new StockListingDto();
        optionListing.setId(200L);
        optionListing.setListingType(ListingType.OPTION);
        optionListing.setPrice(BigDecimal.valueOf(200));
        optionListing.setTicker("AAPL-CALL");
        optionListing.setStrikePrice(BigDecimal.valueOf(100));
        optionListing.setSettlementDate(LocalDate.now().plusDays(1));
        optionListing.setOptionType(OptionType.CALL);

        when(stockClient.getListing(100L)).thenReturn(stockListing);
        when(stockClient.getListing(200L)).thenReturn(optionListing);

        PortfolioSummaryResponse result = portfolioService.getPortfolio(clientUser);

        assertThat(result.getHoldings()).hasSize(2);
        assertThat(result.getHoldings().get(0).getTicker()).isEqualTo("AAPL");
        assertThat(result.getHoldings().get(0).getAveragePurchasePrice()).isEqualByComparingTo("100");
        assertThat(result.getHoldings().get(0).getProfit()).isEqualByComparingTo("500");
        assertThat(result.getHoldings().get(0).getPublicQuantity()).isEqualTo(2);
        assertThat(result.getHoldings().get(1).getAveragePurchasePrice()).isEqualByComparingTo("12");
        assertThat(result.getHoldings().get(1).getExercisable()).isTrue();
        assertThat(result.getTotalProfit()).isEqualByComparingTo("876");
        assertThat(result.getYearlyTaxPaid()).isEqualByComparingTo("15.00");
        assertThat(result.getMonthlyTaxDue()).isEqualByComparingTo("5.00");
    }

    @Test
    void getPortfolio_reusesListingLookupPerUniqueListingId() {
        Portfolio duplicateStockPortfolio = new Portfolio();
        duplicateStockPortfolio.setId(3L);
        duplicateStockPortfolio.setUserId(1L);
        duplicateStockPortfolio.setListingId(100L);
        duplicateStockPortfolio.setListingType(ListingType.STOCK);
        duplicateStockPortfolio.setQuantity(4);
        duplicateStockPortfolio.setAveragePurchasePrice(BigDecimal.valueOf(80));
        duplicateStockPortfolio.setPublicQuantity(0);
        duplicateStockPortfolio.setLastModified(LocalDateTime.now());

        when(portfolioRepository.findByUserId(1L)).thenReturn(List.of(stockPortfolio, duplicateStockPortfolio, optionPortfolio));

        StockListingDto stockListing = new StockListingDto();
        stockListing.setId(100L);
        stockListing.setListingType(ListingType.STOCK);
        stockListing.setPrice(BigDecimal.valueOf(150));
        stockListing.setTicker("AAPL");

        StockListingDto optionListing = new StockListingDto();
        optionListing.setId(200L);
        optionListing.setListingType(ListingType.OPTION);
        optionListing.setPrice(BigDecimal.valueOf(200));
        optionListing.setTicker("AAPL-CALL");
        optionListing.setStrikePrice(BigDecimal.valueOf(100));
        optionListing.setSettlementDate(LocalDate.now().plusDays(1));
        optionListing.setOptionType(OptionType.CALL);

        when(stockClient.getListing(100L)).thenReturn(stockListing);
        when(stockClient.getListing(200L)).thenReturn(optionListing);

        PortfolioSummaryResponse result = portfolioService.getPortfolio(clientUser);

        assertThat(result.getHoldings()).hasSize(3);
        verify(stockClient, times(1)).getListing(100L);
        verify(stockClient, times(1)).getListing(200L);
    }

    @Test
    void setPublicQuantity_updatesOwnedStockPosition() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(stockPortfolio));

        SetPublicQuantityRequestDto request = new SetPublicQuantityRequestDto();
        request.setPublicQuantity(5);

        portfolioService.setPublicQuantity(clientUser, 1L, request);

        assertThat(stockPortfolio.getPublicQuantity()).isEqualTo(5);
        assertThat(stockPortfolio.getIsPublic()).isTrue();
        verify(portfolioRepository).save(stockPortfolio);
    }

    @Test
    void setPublicQuantity_rejectsNegativeQuantity() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(stockPortfolio));
        SetPublicQuantityRequestDto request = new SetPublicQuantityRequestDto();
        request.setPublicQuantity(-1);

        assertThatThrownBy(() -> portfolioService.setPublicQuantity(clientUser, 1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void setPublicQuantity_rejectsQuantityAboveOwned() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(stockPortfolio));
        SetPublicQuantityRequestDto request = new SetPublicQuantityRequestDto();
        request.setPublicQuantity(11);

        assertThatThrownBy(() -> portfolioService.setPublicQuantity(clientUser, 1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void setPublicQuantity_rejectsNonStockAndNonOwner() {
        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        SetPublicQuantityRequestDto request = new SetPublicQuantityRequestDto();
        request.setPublicQuantity(1);

        assertThatThrownBy(() -> portfolioService.setPublicQuantity(clientUser, 2L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only STOCK");

        Portfolio foreignPortfolio = new Portfolio();
        foreignPortfolio.setId(3L);
        foreignPortfolio.setUserId(9L);
        foreignPortfolio.setListingId(300L);
        foreignPortfolio.setListingType(ListingType.STOCK);
        foreignPortfolio.setQuantity(5);
        when(portfolioRepository.findById(3L)).thenReturn(Optional.of(foreignPortfolio));

        assertThatThrownBy(() -> portfolioService.setPublicQuantity(clientUser, 3L, request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("authenticated user");
    }

    @Test
    void exerciseOption_rejectsNonOptionExpiredAndNotInMoney() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(stockPortfolio));
        assertThatThrownBy(() -> portfolioService.exerciseOption(actuaryUser, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only OPTION");

        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        StockListingDto expired = optionListing(LocalDate.now().minusDays(1), OptionType.CALL, new BigDecimal("150"), 300L);
        when(stockClient.getListing(200L)).thenReturn(expired);
        assertThatThrownBy(() -> portfolioService.exerciseOption(actuaryUser, 2L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("expired");

        StockListingDto notInMoney = optionListing(LocalDate.now().plusDays(1), OptionType.CALL, new BigDecimal("0.50"), 300L);
        when(stockClient.getListing(200L)).thenReturn(notInMoney);
        assertThatThrownBy(() -> portfolioService.exerciseOption(actuaryUser, 2L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("in-the-money");
    }

    @Test
    void exerciseOption_rejectsNonOwnerAndNonActuary() {
        Portfolio foreignOption = new Portfolio();
        foreignOption.setId(4L);
        foreignOption.setUserId(9L);
        foreignOption.setListingId(200L);
        foreignOption.setListingType(ListingType.OPTION);
        foreignOption.setQuantity(1);
        when(portfolioRepository.findById(4L)).thenReturn(Optional.of(foreignOption));

        assertThatThrownBy(() -> portfolioService.exerciseOption(actuaryUser, 4L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("authenticated user");

        assertThatThrownBy(() -> portfolioService.exerciseOption(clientUser, 2L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("actuaries");
    }

    @Test
    void exerciseOption_rejectsLegacyActuaryAliasWithoutAgentRole() {
        AuthenticatedUser legacyActuaryAlias = new AuthenticatedUser(1L, Set.of("ACTUARY"), Set.of());

        assertThatThrownBy(() -> portfolioService.exerciseOption(legacyActuaryAlias, 2L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("actuaries");
    }

    @Test
    void supervisorCanExerciseOptionWhenOtherwiseEligible() {
        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        StockListingDto optionListing = optionListing(LocalDate.now().plusDays(1), OptionType.CALL, new BigDecimal("150"), 300L);
        StockListingDto underlying = new StockListingDto();
        underlying.setId(300L);
        underlying.setListingType(ListingType.STOCK);
        when(stockClient.getListing(200L)).thenReturn(optionListing);
        when(stockClient.getListing(300L)).thenReturn(underlying);
        when(portfolioRepository.findByUserIdAndListingId(1L, 300L)).thenReturn(Optional.empty());

        BankAccountDto bankAccount = new BankAccountDto();
        bankAccount.setAccountId(999L);
        when(employeeClient.getBankAccount("USD")).thenReturn(bankAccount);

        AccountDetailsDto bankDetails = new AccountDetailsDto();
        bankDetails.setAccountNumber("ACC-USER");
        bankDetails.setCurrency("USD");
        when(accountClient.getAccountDetails(999L)).thenReturn(bankDetails);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-MARKET");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("23400.00"));
        when(exchangeClient.calculate("USD", "RSD", new BigDecimal("200.00"))).thenReturn(conversion);

        portfolioService.exerciseOption(supervisorUser, 2L);

        verify(accountClient).transaction(org.mockito.ArgumentMatchers.any(PaymentDto.class));
        verify(portfolioRepository).delete(optionPortfolio);
    }

    @Test
    void exerciseCall_createsUnderlyingStockAndInvokesAccountService() {
        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        StockListingDto optionListing = optionListing(LocalDate.now().plusDays(1), OptionType.CALL, new BigDecimal("150"), 300L);
        StockListingDto underlying = new StockListingDto();
        underlying.setId(300L);
        underlying.setListingType(ListingType.STOCK);
        underlying.setTicker("AAPL");
        when(stockClient.getListing(200L)).thenReturn(optionListing);
        when(stockClient.getListing(300L)).thenReturn(underlying);
        when(portfolioRepository.findByUserIdAndListingId(1L, 300L)).thenReturn(Optional.empty());

        BankAccountDto bankAccount = new BankAccountDto();
        bankAccount.setAccountId(999L);
        when(employeeClient.getBankAccount("USD")).thenReturn(bankAccount);

        AccountDetailsDto bankDetails = new AccountDetailsDto();
        bankDetails.setAccountNumber("ACC-USER");
        bankDetails.setCurrency("USD");
        when(accountClient.getAccountDetails(999L)).thenReturn(bankDetails);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-MARKET");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("23400.00"));
        when(exchangeClient.calculate("USD", "RSD", new BigDecimal("200.00"))).thenReturn(conversion);

        portfolioService.exerciseOption(actuaryUser, 2L);

        ArgumentCaptor<PaymentDto> paymentCaptor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(accountClient).transaction(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFromAccountNumber()).isEqualTo("ACC-USER");
        assertThat(paymentCaptor.getValue().getToAccountNumber()).isEqualTo("ACC-MARKET");
        assertThat(paymentCaptor.getValue().getFromAmount()).isEqualByComparingTo("200.00");

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        assertThat(portfolioCaptor.getValue().getListingId()).isEqualTo(300L);
        assertThat(portfolioCaptor.getValue().getQuantity()).isEqualTo(200);
        assertThat(portfolioCaptor.getValue().getAveragePurchasePrice()).isEqualByComparingTo("1.00");
        verify(portfolioRepository).delete(optionPortfolio);
    }

    @Test
    void exercisePut_reducesUnderlyingStockAndInvokesAccountService() {
        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        StockListingDto optionListing = optionListing(LocalDate.now().plusDays(1), OptionType.PUT, new BigDecimal("0.50"), 300L);
        StockListingDto underlying = new StockListingDto();
        underlying.setId(300L);
        underlying.setListingType(ListingType.STOCK);
        underlying.setTicker("AAPL");
        when(stockClient.getListing(200L)).thenReturn(optionListing);
        when(stockClient.getListing(300L)).thenReturn(underlying);

        Portfolio underlyingHolding = new Portfolio();
        underlyingHolding.setId(5L);
        underlyingHolding.setUserId(1L);
        underlyingHolding.setListingId(300L);
        underlyingHolding.setListingType(ListingType.STOCK);
        underlyingHolding.setQuantity(250);
        underlyingHolding.setAveragePurchasePrice(new BigDecimal("20.00"));
        when(portfolioRepository.findByUserIdAndListingId(1L, 300L)).thenReturn(Optional.of(underlyingHolding));

        BankAccountDto bankAccount = new BankAccountDto();
        bankAccount.setAccountId(999L);
        when(employeeClient.getBankAccount("USD")).thenReturn(bankAccount);

        AccountDetailsDto bankDetails = new AccountDetailsDto();
        bankDetails.setAccountNumber("ACC-USER");
        bankDetails.setCurrency("USD");
        when(accountClient.getAccountDetails(999L)).thenReturn(bankDetails);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-MARKET");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("11700.00"));
        when(exchangeClient.calculate("USD", "RSD", new BigDecimal("200.00"))).thenReturn(conversion);

        portfolioService.exerciseOption(actuaryUser, 2L);

        ArgumentCaptor<PaymentDto> paymentCaptor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(accountClient).transaction(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFromAccountNumber()).isEqualTo("ACC-MARKET");
        assertThat(paymentCaptor.getValue().getToAccountNumber()).isEqualTo("ACC-USER");

        assertThat(underlyingHolding.getQuantity()).isEqualTo(50);
        verify(portfolioRepository).save(underlyingHolding);
        verify(portfolioRepository).delete(optionPortfolio);
    }

    @Test
    void exercisePut_rejectsWhenUnderlyingSharesMissing() {
        when(portfolioRepository.findById(2L)).thenReturn(Optional.of(optionPortfolio));
        StockListingDto optionListing = optionListing(LocalDate.now().plusDays(1), OptionType.PUT, new BigDecimal("0.50"), 300L);
        StockListingDto underlying = new StockListingDto();
        underlying.setId(300L);
        underlying.setListingType(ListingType.STOCK);
        when(stockClient.getListing(200L)).thenReturn(optionListing);
        when(stockClient.getListing(300L)).thenReturn(underlying);
        when(portfolioRepository.findByUserIdAndListingId(1L, 300L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.exerciseOption(actuaryUser, 2L))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("underlying stock quantity");
        verify(accountClient, never()).transaction(org.mockito.ArgumentMatchers.any(PaymentDto.class));
    }

    private StockListingDto optionListing(LocalDate settlementDate, OptionType optionType, BigDecimal marketPrice, Long underlyingListingId) {
        StockListingDto listing = new StockListingDto();
        listing.setId(200L);
        listing.setListingType(ListingType.OPTION);
        listing.setTicker("AAPL-OPT");
        listing.setPrice(marketPrice);
        listing.setStrikePrice(BigDecimal.ONE);
        listing.setSettlementDate(settlementDate);
        listing.setOptionType(optionType);
        listing.setCurrency("USD");
        listing.setUnderlyingListingId(underlyingListingId);
        return listing;
    }
}
