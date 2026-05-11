package com.banka1.order.service.impl;

import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.ActuaryProfitResponse;
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
import com.banka1.order.service.BankProfitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankProfitServiceImpl implements BankProfitService {

    private static final int EMPLOYEE_PAGE_SIZE = 100;
    private static final String RSD = "RSD";
    private static final Set<String> ACTUARY_ROLES = Set.of("AGENT", "SUPERVISOR", "ADMIN");

    private final ActuaryProfitRollupRepository rollupRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final EmployeeClient employeeClient;
    private final StockClient stockClient;
    private final ExchangeClient exchangeClient;

    @Override
    @Transactional(readOnly = true)
    public List<ActuaryProfitResponse> getActuaryProfits() {
        return rollupRepository.findAllByOrderByEmployeeIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void refreshActuaryProfitRollup() {
        Map<Long, EmployeeDto> actuaries = loadActuaries();
        Map<Long, BigDecimal> profitsByEmployee = calculateOrderProfits(actuaries.keySet());
        LocalDateTime refreshedAt = LocalDateTime.now();

        Map<Long, ActuaryProfitRollup> existing = rollupRepository.findAll().stream()
                .collect(Collectors.toMap(ActuaryProfitRollup::getEmployeeId, Function.identity()));
        List<ActuaryProfitRollup> refreshed = new ArrayList<>();

        for (EmployeeDto employee : actuaries.values()) {
            ActuaryProfitRollup rollup = existing.remove(employee.getId());
            if (rollup == null) {
                rollup = new ActuaryProfitRollup();
                rollup.setEmployeeId(employee.getId());
            }
            rollup.setIme(defaultString(employee.getIme()));
            rollup.setPrezime(defaultString(employee.getPrezime()));
            rollup.setRole(normalizeRole(employee.getRole()));
            rollup.setTotalProfitRsd(profitsByEmployee.getOrDefault(employee.getId(), BigDecimal.ZERO));
            rollup.setRefreshedAt(refreshedAt);
            refreshed.add(rollup);
        }

        if (!existing.isEmpty()) {
            rollupRepository.deleteAll(existing.values());
        }
        rollupRepository.saveAll(refreshed);
    }

    private Map<Long, EmployeeDto> loadActuaries() {
        Map<Long, EmployeeDto> actuaries = new LinkedHashMap<>();
        int pageIndex = 0;
        while (true) {
            EmployeePageResponse page = employeeClient.searchEmployees(null, null, null, null, pageIndex, EMPLOYEE_PAGE_SIZE);
            if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
                break;
            }
            page.getContent().stream()
                    .filter(employee -> employee != null && employee.getId() != null)
                    .filter(employee -> ACTUARY_ROLES.contains(upper(employee.getRole())))
                    .forEach(employee -> actuaries.putIfAbsent(employee.getId(), employee));

            pageIndex++;
            if (pageIndex >= page.getTotalPages()) {
                break;
            }
        }
        return actuaries;
    }

    private Map<Long, BigDecimal> calculateOrderProfits(Set<Long> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        List<Order> orders = orderRepository.findByUserIdIn(new HashSet<>(employeeIds));
        if (orders.isEmpty()) {
            return Map.of();
        }

        Map<Long, Order> ordersById = orders.stream()
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<Long> orderIds = new ArrayList<>(ordersById.keySet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }

        List<Transaction> transactions = new ArrayList<>(transactionRepository.findByOrderIdIn(orderIds));
        transactions.sort(Comparator
                .comparing(Transaction::getTimestamp, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(transaction -> orderDirectionRank(ordersById.get(transaction.getOrderId())))
                .thenComparing(Transaction::getId, Comparator.nullsLast(Long::compareTo)));

        Map<UserListingKey, Deque<BuyLot>> lotsByUserListing = new HashMap<>();
        Map<Long, BigDecimal> profitByEmployee = new HashMap<>();
        Map<Long, String> currencyByListing = new HashMap<>();

        for (Transaction transaction : transactions) {
            Order order = ordersById.get(transaction.getOrderId());
            if (!valid(order, transaction)) {
                continue;
            }
            UserListingKey key = new UserListingKey(order.getUserId(), order.getListingId());
            Deque<BuyLot> lots = lotsByUserListing.computeIfAbsent(key, ignored -> new ArrayDeque<>());

            if (order.getDirection() == OrderDirection.BUY) {
                lots.addLast(new BuyLot(transaction.getQuantity(), transaction.getPricePerUnit()));
                continue;
            }
            if (order.getDirection() == OrderDirection.SELL) {
                BigDecimal profit = calculateSellProfit(lots, order, transaction);
                if (profit.compareTo(BigDecimal.ZERO) != 0) {
                    String currency = currencyByListing.computeIfAbsent(order.getListingId(), this::resolveCurrency);
                    BigDecimal profitRsd = convertToRsd(currency, profit, transaction.getTimestamp().toLocalDate());
                    profitByEmployee.merge(order.getUserId(), profitRsd, BigDecimal::add);
                }
            }
        }

        // TODO: Add otc-service OptionContract.realizedProfit to this rollup once that service/API exists.
        return profitByEmployee;
    }

    private BigDecimal calculateSellProfit(Deque<BuyLot> lots, Order sellOrder, Transaction sellTransaction) {
        int quantityToMatch = sellTransaction.getQuantity();
        BigDecimal profit = BigDecimal.ZERO;

        while (quantityToMatch > 0 && !lots.isEmpty()) {
            BuyLot lot = lots.peekFirst();
            int matchedQuantity = Math.min(quantityToMatch, lot.remainingQuantity());
            BigDecimal matchedProfit = sellTransaction.getPricePerUnit()
                    .subtract(lot.purchasePricePerUnit())
                    .multiply(BigDecimal.valueOf(matchedQuantity))
                    .multiply(BigDecimal.valueOf(sellOrder.getContractSize()));
            profit = profit.add(matchedProfit);

            quantityToMatch -= matchedQuantity;
            lot.remainingQuantity(lot.remainingQuantity() - matchedQuantity);
            if (lot.remainingQuantity() == 0) {
                lots.removeFirst();
            }
        }

        if (quantityToMatch > 0) {
            log.warn("Unable to fully match sell transaction {} while calculating actuary profit", sellTransaction.getId());
        }
        return profit;
    }

    private BigDecimal convertToRsd(String currency, BigDecimal amount, LocalDate date) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (currency == null || RSD.equalsIgnoreCase(currency)) {
            return amount;
        }
        BigDecimal sign = amount.signum() < 0 ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal convertibleAmount = amount.abs();
        ExchangeRateDto conversion = exchangeClient.calculateWithoutCommission(currency, RSD, convertibleAmount, date);
        BigDecimal converted = conversion == null || conversion.getConvertedAmount() == null
                ? convertibleAmount
                : conversion.getConvertedAmount();
        return converted.multiply(sign);
    }

    private String resolveCurrency(Long listingId) {
        try {
            StockListingDto listing = stockClient.getListing(listingId);
            return listing == null ? RSD : listing.getCurrency();
        } catch (Exception ex) {
            log.warn("Unable to resolve listing currency for {} while calculating actuary profit", listingId, ex);
            return RSD;
        }
    }

    private boolean valid(Order order, Transaction transaction) {
        return order != null
                && order.getUserId() != null
                && order.getListingId() != null
                && order.getDirection() != null
                && order.getContractSize() != null
                && transaction.getQuantity() != null
                && transaction.getQuantity() > 0
                && transaction.getPricePerUnit() != null
                && transaction.getTimestamp() != null;
    }

    private int orderDirectionRank(Order order) {
        if (order == null || order.getDirection() == null) {
            return 2;
        }
        return order.getDirection() == OrderDirection.BUY ? 0 : 1;
    }

    private ActuaryProfitResponse toResponse(ActuaryProfitRollup rollup) {
        return new ActuaryProfitResponse(
                rollup.getEmployeeId(),
                rollup.getIme(),
                rollup.getPrezime(),
                rollup.getRole(),
                rollup.getTotalProfitRsd()
        );
    }

    private String normalizeRole(String role) {
        String value = upper(role);
        return "ADMIN".equals(value) ? "SUPERVISOR" : value;
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record UserListingKey(Long userId, Long listingId) {
    }

    private static final class BuyLot {
        private int remainingQuantity;
        private final BigDecimal purchasePricePerUnit;

        private BuyLot(int remainingQuantity, BigDecimal purchasePricePerUnit) {
            this.remainingQuantity = remainingQuantity;
            this.purchasePricePerUnit = purchasePricePerUnit;
        }

        int remainingQuantity() {
            return remainingQuantity;
        }

        void remainingQuantity(int remainingQuantity) {
            this.remainingQuantity = remainingQuantity;
        }

        BigDecimal purchasePricePerUnit() {
            return purchasePricePerUnit;
        }
    }
}
