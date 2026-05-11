package com.banka1.order.service;

import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.EmployeePageResponse;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.entity.ActuaryProfitRollup;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.repository.ActuaryProfitRollupRepository;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.TransactionRepository;
import com.banka1.order.service.impl.BankProfitServiceImpl;
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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankProfitServiceTest {

    @Mock
    private ActuaryProfitRollupRepository rollupRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private EmployeeClient employeeClient;
    @Mock
    private StockClient stockClient;
    @Mock
    private ExchangeClient exchangeClient;

    @InjectMocks
    private BankProfitServiceImpl bankProfitService;

    @Test
    void refreshActuaryProfitRollup_calculatesMultiCurrencyRealizedProfitInRsd() {
        EmployeeDto agent = employee(1L, "Pera", "Peric", "AGENT");
        EmployeeDto supervisor = employee(2L, "Mika", "Mikic", "SUPERVISOR");
        when(employeeClient.searchEmployees(null, null, null, null, 0, 100))
                .thenReturn(employeePage(List.of(agent, supervisor)));

        Order usdBuy = order(10L, 1L, 100L, OrderDirection.BUY, 1);
        Order usdSell = order(11L, 1L, 100L, OrderDirection.SELL, 1);
        Order eurBuy = order(12L, 1L, 200L, OrderDirection.BUY, 1);
        Order eurSell = order(13L, 1L, 200L, OrderDirection.SELL, 1);
        when(orderRepository.findByUserIdIn(any())).thenReturn(List.of(usdBuy, usdSell, eurBuy, eurSell));

        LocalDateTime usdBuyTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime usdSellTime = LocalDateTime.of(2026, 1, 3, 10, 0);
        LocalDateTime eurBuyTime = LocalDateTime.of(2026, 2, 1, 10, 0);
        LocalDateTime eurSellTime = LocalDateTime.of(2026, 2, 5, 10, 0);
        when(transactionRepository.findByOrderIdIn(any())).thenReturn(List.of(
                transaction(10L, 10, "10.00", usdBuyTime),
                transaction(11L, 4, "15.00", usdSellTime),
                transaction(12L, 2, "20.00", eurBuyTime),
                transaction(13L, 2, "30.00", eurSellTime)
        ));

        when(stockClient.getListing(100L)).thenReturn(listing("USD"));
        when(stockClient.getListing(200L)).thenReturn(listing("EUR"));
        when(exchangeClient.calculateWithoutCommission(eq("USD"), eq("RSD"), amountEq("20.00"), eq(LocalDate.of(2026, 1, 3))))
                .thenReturn(conversion("2000.00"));
        when(exchangeClient.calculateWithoutCommission(eq("EUR"), eq("RSD"), amountEq("20.00"), eq(LocalDate.of(2026, 2, 5))))
                .thenReturn(conversion("2400.00"));
        when(rollupRepository.findAll()).thenReturn(List.of());

        bankProfitService.refreshActuaryProfitRollup();

        List<ActuaryProfitRollup> savedRows = savedRows();
        assertThat(savedRows).hasSize(2);
        assertThat(savedRows)
                .filteredOn(row -> row.getEmployeeId().equals(1L))
                .singleElement()
                .satisfies(row -> assertThat(row.getTotalProfitRsd()).isEqualByComparingTo("4400.00"));
        assertThat(savedRows)
                .filteredOn(row -> row.getEmployeeId().equals(2L))
                .singleElement()
                .satisfies(row -> assertThat(row.getTotalProfitRsd()).isEqualByComparingTo("0"));
    }

    @Test
    void refreshActuaryProfitRollup_includesAgentsAndSupervisorsOnly() {
        when(employeeClient.searchEmployees(null, null, null, null, 0, 100)).thenReturn(employeePage(List.of(
                employee(1L, "Agent", "Jedan", "AGENT"),
                employee(2L, "Supervisor", "Jedan", "SUPERVISOR"),
                employee(3L, "Admin", "Jedan", "ADMIN"),
                employee(4L, "Client", "Jedan", "CLIENT")
        )));
        when(orderRepository.findByUserIdIn(any())).thenReturn(List.of());
        when(rollupRepository.findAll()).thenReturn(List.of());

        bankProfitService.refreshActuaryProfitRollup();

        List<ActuaryProfitRollup> savedRows = savedRows();
        assertThat(savedRows).extracting(ActuaryProfitRollup::getEmployeeId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(savedRows)
                .filteredOn(row -> row.getEmployeeId().equals(1L))
                .singleElement()
                .satisfies(row -> assertThat(row.getRole()).isEqualTo("AGENT"));
        assertThat(savedRows)
                .filteredOn(row -> row.getEmployeeId().equals(2L))
                .singleElement()
                .satisfies(row -> assertThat(row.getRole()).isEqualTo("SUPERVISOR"));
        assertThat(savedRows)
                .filteredOn(row -> row.getEmployeeId().equals(3L))
                .singleElement()
                .satisfies(row -> assertThat(row.getRole()).isEqualTo("SUPERVISOR"));
        verifyNoInteractions(transactionRepository, stockClient, exchangeClient);
    }

    @SuppressWarnings("unchecked")
    private List<ActuaryProfitRollup> savedRows() {
        ArgumentCaptor<Iterable<ActuaryProfitRollup>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(rollupRepository).saveAll(captor.capture());
        return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
    }

    private EmployeePageResponse employeePage(List<EmployeeDto> employees) {
        EmployeePageResponse response = new EmployeePageResponse();
        response.setContent(employees);
        response.setTotalPages(1);
        response.setTotalElements(employees.size());
        return response;
    }

    private EmployeeDto employee(Long id, String ime, String prezime, String role) {
        EmployeeDto employee = new EmployeeDto();
        employee.setId(id);
        employee.setIme(ime);
        employee.setPrezime(prezime);
        employee.setRole(role);
        return employee;
    }

    private Order order(Long id, Long userId, Long listingId, OrderDirection direction, int contractSize) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setListingId(listingId);
        order.setDirection(direction);
        order.setContractSize(contractSize);
        return order;
    }

    private Transaction transaction(Long orderId, int quantity, String pricePerUnit, LocalDateTime timestamp) {
        Transaction transaction = new Transaction();
        transaction.setOrderId(orderId);
        transaction.setQuantity(quantity);
        transaction.setPricePerUnit(new BigDecimal(pricePerUnit));
        transaction.setTimestamp(timestamp);
        return transaction;
    }

    private StockListingDto listing(String currency) {
        StockListingDto listing = new StockListingDto();
        listing.setCurrency(currency);
        return listing;
    }

    private ExchangeRateDto conversion(String convertedAmount) {
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setConvertedAmount(new BigDecimal(convertedAmount));
        return dto;
    }

    private BigDecimal amountEq(String expected) {
        return argThat(actual -> actual != null && actual.compareTo(new BigDecimal(expected)) == 0);
    }
}
