package com.banka1.order.service;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.ClientClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.CustomerDto;
import com.banka1.order.dto.CustomerPageResponse;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.TaxDebtResponse;
import com.banka1.order.dto.TaxTrackingRowResponse;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.dto.response.UpdatedBalanceResponseDto;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.TaxCharge;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.TaxChargeStatus;
import com.banka1.order.rabbitmq.OrderNotificationProducer;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.TaxChargeRepository;
import com.banka1.order.repository.TransactionRepository;
import com.banka1.order.service.impl.TaxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AccountClient accountClient;
    @Mock
    private ClientClient clientClient;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private ExchangeClient exchangeClient;
    @Mock
    private StockClient stockClient;
    @Mock
    private OrderNotificationProducer notificationProducer;
    @Mock
    private TaxChargeRepository taxChargeRepository;

    @InjectMocks
    private TaxServiceImpl taxService;

    private Transaction buyTx;
    private Transaction sellTx;
    private Order buyOrder;
    private Order sellOrder;
    private StockListingDto stockListing;

    @BeforeEach
    void setUp() {
        buyTx = new Transaction();
        buyTx.setId(1L);
        buyTx.setOrderId(10L);
        buyTx.setQuantity(10);
        buyTx.setPricePerUnit(new BigDecimal("100.00"));
        buyTx.setTimestamp(LocalDate.now().minusMonths(2).atStartOfDay());

        sellTx = new Transaction();
        sellTx.setId(2L);
        sellTx.setOrderId(11L);
        sellTx.setQuantity(5);
        sellTx.setPricePerUnit(new BigDecimal("150.00"));
        sellTx.setTimestamp(LocalDate.now().minusMonths(1).atStartOfDay());

        buyOrder = new Order();
        buyOrder.setId(10L);
        buyOrder.setUserId(5L);
        buyOrder.setListingId(100L);
        buyOrder.setAccountId(21L);
        buyOrder.setDirection(OrderDirection.BUY);

        sellOrder = new Order();
        sellOrder.setId(11L);
        sellOrder.setUserId(5L);
        sellOrder.setListingId(100L);
        sellOrder.setAccountId(99L);
        sellOrder.setDirection(OrderDirection.SELL);

        stockListing = new StockListingDto();
        stockListing.setId(100L);
        stockListing.setListingType(ListingType.STOCK);
        stockListing.setTicker("AAPL");

        lenient().when(orderRepository.findByDirection(OrderDirection.SELL)).thenReturn(List.of(sellOrder));
        lenient().when(orderRepository.findByUserIdAndDirection(5L, OrderDirection.SELL)).thenReturn(List.of(sellOrder));
        lenient().when(orderRepository.findByUserId(5L)).thenReturn(List.of(buyOrder, sellOrder));
        lenient().when(orderRepository.findByUserIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> userIds = invocation.getArgument(0);
            return userIds != null && userIds.contains(5L) ? List.of(buyOrder, sellOrder) : List.of();
        });
        lenient().when(transactionRepository.findByOrderIdInAndTimestampBetween(any(), any(), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsAndRange(
                        List.of(buyTx, sellTx),
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        lenient().when(transactionRepository.findByOrderIdInAndTimestampBefore(any(), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsBefore(
                        List.of(buyTx, sellTx),
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));

        Map<String, TaxCharge> persistedCharges = new HashMap<>();
        lenient().when(taxChargeRepository.existsBySellTransactionIdAndBuyTransactionId(anyLong(), anyLong()))
                .thenAnswer(invocation -> persistedCharges.containsKey(invocation.getArgument(0) + ":" + invocation.getArgument(1)));
        lenient().when(taxChargeRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    var taxCharge = invocation.getArgument(0, TaxCharge.class);
                    if (taxCharge.getId() == null) {
                        taxCharge.setId((long) (persistedCharges.size() + 1));
                    }
                    persistedCharges.put(taxCharge.getSellTransactionId() + ":" + taxCharge.getBuyTransactionId(), taxCharge);
                    return taxCharge;
                });
        lenient().when(taxChargeRepository.save(any()))
                .thenAnswer(invocation -> {
                    var taxCharge = invocation.getArgument(0, TaxCharge.class);
                    if (taxCharge.getId() == null) {
                        taxCharge.setId((long) (persistedCharges.size() + 1));
                    }
                    persistedCharges.put(taxCharge.getSellTransactionId() + ":" + taxCharge.getBuyTransactionId(), taxCharge);
                    return taxCharge;
                });
        lenient().doAnswer(invocation -> {
            var taxCharge = invocation.getArgument(0, TaxCharge.class);
            persistedCharges.remove(taxCharge.getSellTransactionId() + ":" + taxCharge.getBuyTransactionId());
            return null;
        }).when(taxChargeRepository).delete(any(TaxCharge.class));
    }

    @Test
    void collectMonthlyTax_chargesFifteenPercentForStockSellFromOriginalBuyAccountToGovernmentRsdAccount() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTax();

        ArgumentCaptor<PaymentDto> paymentCaptor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(accountClient).transaction(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFromAccountNumber()).isEqualTo("ACC-BUY-ORIGINAL");
        assertThat(paymentCaptor.getValue().getToAccountNumber()).isEqualTo("ACC-GOV-RSD");
        assertThat(paymentCaptor.getValue().getFromAmount()).isEqualByComparingTo("37.50");
        assertThat(paymentCaptor.getValue().getToAmount()).isEqualByComparingTo("2925.00");
        verify(exchangeClient).calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"));
        verify(exchangeClient, never()).calculate("USD", "RSD", new BigDecimal("37.50"));
        verify(notificationProducer).sendTaxCollected(any());
        verify(transactionRepository, never()).findByTimestampBetween(any(), any());
        verify(transactionRepository).findByOrderIdInAndTimestampBetween(eq(List.of(11L)), any(), any());
        verify(transactionRepository).findByOrderIdInAndTimestampBefore(eq(List.of(10L, 11L)), any());
    }

    @Test
    void collectMonthlyTax_doesNotTaxNonStockSellOrders() {
        StockListingDto forexListing = new StockListingDto();
        forexListing.setId(100L);
        forexListing.setListingType(ListingType.FOREX);

        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(forexListing);

        taxService.collectMonthlyTax();

        verify(accountClient, never()).transaction(any(PaymentDto.class));
        verifyNoInteractions(exchangeClient);
        verifyNoInteractions(notificationProducer);
    }

    @Test
    void collectMonthlyTax_usesHistoricalBuyCostInsteadOfSellOrderAccountOrCurrentPortfolioState() {
        Transaction laterBuyTx = new Transaction();
        laterBuyTx.setId(3L);
        laterBuyTx.setOrderId(12L);
        laterBuyTx.setQuantity(10);
        laterBuyTx.setPricePerUnit(new BigDecimal("300.00"));
        laterBuyTx.setTimestamp(LocalDate.now().atStartOfDay());

        Order laterBuyOrder = new Order();
        laterBuyOrder.setId(12L);
        laterBuyOrder.setUserId(5L);
        laterBuyOrder.setListingId(100L);
        laterBuyOrder.setAccountId(77L);
        laterBuyOrder.setDirection(OrderDirection.BUY);

        when(orderRepository.findByUserId(5L)).thenReturn(List.of(buyOrder, sellOrder, laterBuyOrder));
        when(transactionRepository.findByOrderIdInAndTimestampBefore(eq(List.of(10L, 11L, 12L)), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsBefore(
                        List.of(buyTx, sellTx, laterBuyTx),
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        lenient().when(orderRepository.findById(12L)).thenReturn(Optional.of(laterBuyOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTax();

        ArgumentCaptor<PaymentDto> paymentCaptor = ArgumentCaptor.forClass(PaymentDto.class);
        verify(accountClient).transaction(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFromAccountNumber()).isEqualTo("ACC-BUY-ORIGINAL");
        assertThat(paymentCaptor.getValue().getFromAmount()).isEqualByComparingTo("37.50");
    }

    @Test
    void collectMonthlyTaxManually_shouldDelegateToCollectMonthlyTax() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTaxManually();

        verify(accountClient).transaction(any(PaymentDto.class));
        verify(notificationProducer).sendTaxCollected(any());
    }

    @Test
    void collectMonthlyTax_isIdempotentAcrossRepeatedRuns() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTax();
        taxService.collectMonthlyTax();

        verify(accountClient).transaction(any(PaymentDto.class));
        verify(notificationProducer).sendTaxCollected(any());
        verify(exchangeClient).calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"));
    }

    @Test
    void collectMonthlyTaxManually_doesNotDoubleChargeAfterScheduledRun() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTax();
        taxService.collectMonthlyTaxManually();

        verify(accountClient).transaction(any(PaymentDto.class));
        verify(notificationProducer).sendTaxCollected(any());
    }

    @Test
    void collectMonthlyTax_doesNotDoubleChargeWhenNotificationFailsAfterSuccessfulDebit() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));
        doThrow(new RuntimeException("notification failed"))
                .when(notificationProducer).sendTaxCollected(any());

        taxService.collectMonthlyTax();
        taxService.collectMonthlyTax();

        verify(accountClient).transaction(any(PaymentDto.class));
        verify(notificationProducer).sendTaxCollected(any());

        ArgumentCaptor<TaxCharge> chargeCaptor = ArgumentCaptor.forClass(TaxCharge.class);
        verify(taxChargeRepository, times(2)).save(chargeCaptor.capture());
        assertThat(chargeCaptor.getAllValues().getLast().getStatus()).isEqualTo(TaxChargeStatus.CHARGED);
        assertThat(chargeCaptor.getAllValues().getLast().getChargedAt()).isNotNull();
    }

    @Test
    void collectMonthlyTax_retriesSafelyWhenFailureHappensBeforeDebit() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        when(accountClient.getAccountDetailsById(21L))
                .thenThrow(new RuntimeException("lookup failed"))
                .thenAnswer(invocation -> {
                    AccountDetailsDto buyAccount = new AccountDetailsDto();
                    buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
                    buyAccount.setCurrency("USD");
                    return buyAccount;
                });

        AccountDetailsDto government = new AccountDetailsDto();
        government.setAccountNumber("ACC-GOV-RSD");
        government.setCurrency("RSD");
        when(accountClient.getGovernmentBankAccountRsd()).thenReturn(government);

        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);
        when(accountClient.transaction(any(PaymentDto.class)))
                .thenReturn(new UpdatedBalanceResponseDto(new BigDecimal("900.00"), new BigDecimal("2925.00")));

        taxService.collectMonthlyTax();
        taxService.collectMonthlyTax();

        verify(taxChargeRepository).delete(any(TaxCharge.class));
        verify(accountClient).transaction(any(PaymentDto.class));
        verify(notificationProducer).sendTaxCollected(any());
    }

    @Test
    void getUserDebt_shouldCalculateHistoricalStockTaxOnly() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        TaxDebtResponse response = taxService.getUserDebt(5L);

        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getDebtRsd()).isEqualByComparingTo("37.50");
    }

    @Test
    void getAllDebts_shouldAggregateHistoricalStockTaxOnly() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        var result = taxService.getAllDebts(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(5L);
        assertThat(result.getContent().get(0).getDebtRsd()).isEqualByComparingTo("37.50");
    }

    @Test
    void shouldIgnoreBuyOrders() {
        sellOrder.setDirection(OrderDirection.BUY);
        lenient().when(orderRepository.findByDirection(OrderDirection.SELL)).thenReturn(List.of());
        when(orderRepository.findByUserIdAndDirection(5L, OrderDirection.SELL)).thenReturn(List.of());
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));

        TaxDebtResponse response = taxService.getUserDebt(5L);

        assertThat(response.getDebtRsd()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCurrentYearPaidTax_shouldSumTaxBeforeCurrentMonth() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        BigDecimal paidTax = taxService.getCurrentYearPaidTax(5L);

        assertThat(paidTax).isEqualByComparingTo("37.50");
    }

    @Test
    void getCurrentMonthUnpaidTax_shouldSumCurrentMonthTax() {
        sellTx.setTimestamp(LocalDate.now().atStartOfDay());
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        BigDecimal unpaidTax = taxService.getCurrentMonthUnpaidTax(5L);

        assertThat(unpaidTax).isEqualByComparingTo("37.50");
    }

    @Test
    void getCurrentYearPaidTax_shouldUseHistoricalBuyTransactionsInsteadOfCurrentPortfolioState() {
        Transaction expensiveBuyTx = new Transaction();
        expensiveBuyTx.setId(4L);
        expensiveBuyTx.setOrderId(12L);
        expensiveBuyTx.setQuantity(4);
        expensiveBuyTx.setPricePerUnit(new BigDecimal("300.00"));
        expensiveBuyTx.setTimestamp(LocalDate.now().minusDays(5).atStartOfDay());

        Order expensiveBuyOrder = new Order();
        expensiveBuyOrder.setId(12L);
        expensiveBuyOrder.setUserId(5L);
        expensiveBuyOrder.setListingId(100L);
        expensiveBuyOrder.setAccountId(55L);
        expensiveBuyOrder.setDirection(OrderDirection.BUY);

        when(orderRepository.findByUserId(5L)).thenReturn(List.of(buyOrder, sellOrder, expensiveBuyOrder));
        when(transactionRepository.findByOrderIdInAndTimestampBefore(eq(List.of(10L, 11L, 12L)), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsBefore(
                        List.of(expensiveBuyTx, sellTx, buyTx),
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        lenient().when(orderRepository.findById(12L)).thenReturn(Optional.of(expensiveBuyOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        BigDecimal paidTax = taxService.getCurrentYearPaidTax(5L);

        assertThat(paidTax).isEqualByComparingTo("37.50");
    }

    @Test
    void getCurrentMonthUnpaidTax_shouldUseHistoricalAverageCostWhenLaterBuysWouldMisleadCurrentAverage() {
        Transaction currentMonthSell = new Transaction();
        currentMonthSell.setId(5L);
        currentMonthSell.setOrderId(13L);
        currentMonthSell.setQuantity(2);
        currentMonthSell.setPricePerUnit(new BigDecimal("100.00"));
        currentMonthSell.setTimestamp(LocalDate.now().atStartOfDay());

        Order currentMonthSellOrder = new Order();
        currentMonthSellOrder.setId(13L);
        currentMonthSellOrder.setUserId(5L);
        currentMonthSellOrder.setListingId(200L);
        currentMonthSellOrder.setAccountId(99L);
        currentMonthSellOrder.setDirection(OrderDirection.SELL);

        Transaction olderBuy = new Transaction();
        olderBuy.setId(6L);
        olderBuy.setOrderId(14L);
        olderBuy.setQuantity(4);
        olderBuy.setPricePerUnit(new BigDecimal("80.00"));
        olderBuy.setTimestamp(LocalDate.now().minusMonths(1).atStartOfDay());

        Order olderBuyOrder = new Order();
        olderBuyOrder.setId(14L);
        olderBuyOrder.setUserId(5L);
        olderBuyOrder.setListingId(200L);
        olderBuyOrder.setAccountId(22L);
        olderBuyOrder.setDirection(OrderDirection.BUY);

        StockListingDto secondStock = new StockListingDto();
        secondStock.setId(200L);
        secondStock.setListingType(ListingType.STOCK);

        when(orderRepository.findByUserIdAndDirection(5L, OrderDirection.SELL)).thenReturn(List.of(currentMonthSellOrder));
        when(orderRepository.findByUserId(5L)).thenReturn(List.of(currentMonthSellOrder, olderBuyOrder));
        when(transactionRepository.findByOrderIdInAndTimestampBetween(eq(List.of(13L)), any(), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsAndRange(
                        List.of(currentMonthSell),
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(transactionRepository.findByOrderIdInAndTimestampBefore(eq(List.of(13L, 14L)), any()))
                .thenAnswer(invocation -> filterTransactionsByOrderIdsBefore(
                        List.of(currentMonthSell, olderBuy),
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(orderRepository.findById(13L)).thenReturn(Optional.of(currentMonthSellOrder));
        lenient().when(orderRepository.findById(14L)).thenReturn(Optional.of(olderBuyOrder));
        when(stockClient.getListing(200L)).thenReturn(secondStock);

        BigDecimal unpaidTax = taxService.getCurrentMonthUnpaidTax(5L);

        assertThat(unpaidTax).isEqualByComparingTo("6.00");
    }

    @Test
    void getTaxTracking_returnsClientAndActuaryRowsWithDebtInRsd() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);
        ExchangeRateDto conversion = new ExchangeRateDto();
        conversion.setConvertedAmount(new BigDecimal("2925.00"));
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"))).thenReturn(conversion);

        CustomerDto customer = new CustomerDto();
        customer.setId(5L);
        customer.setFirstName("Pera");
        customer.setLastName("Peric");
        CustomerPageResponse customerPage = new CustomerPageResponse();
        customerPage.setContent(List.of(customer));
        customerPage.setTotalPages(1);

        EmployeeDto actuary = new EmployeeDto();
        actuary.setId(6L);
        actuary.setIme("Mika");
        actuary.setPrezime("Mikic");
        actuary.setRole("AGENT");
        EmployeePageResponse employeePage = new EmployeePageResponse();
        employeePage.setContent(List.of(actuary));
        employeePage.setTotalPages(1);

        when(clientClient.searchCustomers(null, null, 0, 100)).thenReturn(customerPage);
        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(employeePage);

        var result = taxService.getTaxTracking(null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(TaxTrackingRowResponse::getUserType).containsExactlyInAnyOrder("CLIENT", "ACTUARY");
        assertThat(result.getContent()).filteredOn(row -> "CLIENT".equals(row.getUserType()))
                .first()
                .satisfies(row -> {
                    assertThat(row.getFirstName()).isEqualTo("Pera");
                    assertThat(row.getLastName()).isEqualTo("Peric");
                    assertThat(row.getTaxDebtRsd()).isEqualByComparingTo("2925.00");
                });
        assertThat(result.getContent()).filteredOn(row -> "ACTUARY".equals(row.getUserType()))
                .first()
                .satisfies(row -> assertThat(row.getTaxDebtRsd()).isEqualByComparingTo(BigDecimal.ZERO));
        verify(exchangeClient).calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"));
        verify(clientClient).searchCustomers(null, null, 0, 100);
        verify(employeeClient).searchEmployees(null, null, null, null, 0, 100);
        verify(transactionRepository, never()).findByTimestampBetween(any(), any());
    }

    @Test
    void getTaxTracking_filtersByUserTypeAndNames() {
        CustomerPageResponse customerPage = new CustomerPageResponse();
        customerPage.setContent(List.of());

        EmployeeDto actuary = new EmployeeDto();
        actuary.setId(6L);
        actuary.setIme("Ana");
        actuary.setPrezime("Agentic");
        actuary.setRole("AGENT");
        EmployeePageResponse employeePage = new EmployeePageResponse();
        employeePage.setContent(List.of(actuary));
        employeePage.setTotalPages(1);

        when(employeeClient.searchEmployees(null, "Ana", "Agentic", null, 0, 100)).thenReturn(employeePage);

        var result = taxService.getTaxTracking("ACTUARY", "Ana", "Agentic", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getUserType()).isEqualTo("ACTUARY");
        verify(employeeClient).searchEmployees(null, "Ana", "Agentic", null, 0, 100);
        verify(clientClient, never()).searchCustomers(any(), any(), anyInt(), anyInt());
    }

    @Test
    void getTaxTracking_fetchesAllPagesInsteadOfOnlyFirst200Users() {
        when(orderRepository.findByDirection(OrderDirection.SELL)).thenReturn(List.of());

        CustomerDto customerPage0 = new CustomerDto();
        customerPage0.setId(1L);
        customerPage0.setFirstName("Client1");
        customerPage0.setLastName("Page0");
        CustomerDto customerPage1 = new CustomerDto();
        customerPage1.setId(2L);
        customerPage1.setFirstName("Client2");
        customerPage1.setLastName("Page1");

        CustomerPageResponse customers0 = new CustomerPageResponse();
        customers0.setContent(List.of(customerPage0));
        customers0.setTotalPages(2);
        CustomerPageResponse customers1 = new CustomerPageResponse();
        customers1.setContent(List.of(customerPage1));
        customers1.setTotalPages(2);

        EmployeeDto actuaryPage0 = new EmployeeDto();
        actuaryPage0.setId(3L);
        actuaryPage0.setIme("Actuary1");
        actuaryPage0.setPrezime("Page0");
        actuaryPage0.setRole("AGENT");
        EmployeeDto actuaryPage1 = new EmployeeDto();
        actuaryPage1.setId(4L);
        actuaryPage1.setIme("Actuary2");
        actuaryPage1.setPrezime("Page1");
        actuaryPage1.setRole("AGENT");

        EmployeePageResponse employees0 = new EmployeePageResponse();
        employees0.setContent(List.of(actuaryPage0));
        employees0.setTotalPages(2);
        EmployeePageResponse employees1 = new EmployeePageResponse();
        employees1.setContent(List.of(actuaryPage1));
        employees1.setTotalPages(2);

        when(clientClient.searchCustomers(null, null, 0, 100)).thenReturn(customers0);
        when(clientClient.searchCustomers(null, null, 1, 100)).thenReturn(customers1);
        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(employees0);
        when(employeeClient.searchEmployees(null, null, null, null, 1, 100)).thenReturn(employees1);

        var result = taxService.getTaxTracking(null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(4);
        verify(clientClient).searchCustomers(null, null, 0, 100);
        verify(clientClient).searchCustomers(null, null, 1, 100);
        verify(employeeClient).searchEmployees(null, null, null, null, 0, 100);
        verify(employeeClient).searchEmployees(null, null, null, null, 1, 100);
        verify(clientClient, never()).searchCustomers(any(), any(), anyInt(), eq(200));
        verify(employeeClient, never()).searchEmployees(any(), any(), any(), any(), anyInt(), eq(200));
    }

    @Test
    void getTaxTracking_failsInsteadOfReturningNonRsdAmountWhenConversionFails() {
        lenient().when(orderRepository.findById(10L)).thenReturn(Optional.of(buyOrder));
        lenient().when(orderRepository.findById(11L)).thenReturn(Optional.of(sellOrder));
        when(stockClient.getListing(100L)).thenReturn(stockListing);

        AccountDetailsDto buyAccount = new AccountDetailsDto();
        buyAccount.setAccountNumber("ACC-BUY-ORIGINAL");
        buyAccount.setCurrency("USD");
        when(accountClient.getAccountDetailsById(21L)).thenReturn(buyAccount);
        when(exchangeClient.calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50")))
                .thenThrow(new RuntimeException("exchange unavailable"));

        assertThatThrownBy(() -> taxService.getTaxTracking(null, null, null, Pageable.unpaged()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to convert tax tracking debt to RSD");

        verify(exchangeClient).calculateWithoutCommission("USD", "RSD", new BigDecimal("37.50"));
    }

    private List<Transaction> filterTransactionsByOrderIdsAndRange(
            List<Transaction> source,
            Collection<Long> orderIds,
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(transaction -> orderIds.contains(transaction.getOrderId()))
                .filter(transaction -> !transaction.getTimestamp().isBefore(start))
                .filter(transaction -> transaction.getTimestamp().isBefore(end))
                .toList();
    }

    private List<Transaction> filterTransactionsByOrderIdsBefore(
            List<Transaction> source,
            Collection<Long> orderIds,
            LocalDateTime end
    ) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(transaction -> orderIds.contains(transaction.getOrderId()))
                .filter(transaction -> transaction.getTimestamp().isBefore(end))
                .toList();
    }
}
