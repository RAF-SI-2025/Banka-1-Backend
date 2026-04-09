package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.ClientClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.CustomerDto;
import com.banka1.order.dto.CustomerPageResponse;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.TaxDebtResponse;
import com.banka1.order.dto.TaxTrackingRowResponse;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.entity.TaxCharge;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.TaxChargeStatus;
import com.banka1.order.rabbitmq.OrderNotificationProducer;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.TaxChargeRepository;
import com.banka1.order.repository.TransactionRepository;
import com.banka1.order.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxServiceImpl implements TaxService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.15");
    private static final LocalDateTime HISTORY_START = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int TRACKING_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final AccountClient accountClient;
    private final ClientClient clientClient;
    private final EmployeeClient employeeClient;
    private final ExchangeClient exchangeClient;
    private final StockClient stockClient;
    private final OrderNotificationProducer notificationProducer;
    private final TaxChargeRepository taxChargeRepository;

    @Override
    public void collectMonthlyTax() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        LocalDate firstDayOfThisMonth = now.withDayOfMonth(1);
        LocalDate firstDayOfPrevMonth = firstDayOfThisMonth.minusMonths(1);

        LocalDateTime start = firstDayOfPrevMonth.atStartOfDay();
        LocalDateTime end = firstDayOfThisMonth.atStartOfDay();

        log.info("Collecting taxes for period {} - {}", start, end);

        List<TaxChargeEntry> taxEntries = buildTaxChargeEntries(start, end, null);

        for (TaxChargeEntry entry : taxEntries) {
            TaxCharge reservation = reserveTaxCharge(entry, start, end);
            if (reservation == null) {
                continue;
            }
            boolean debitSucceeded = false;
            try {
                AccountDetailsDto sourceAccount = accountClient.getAccountDetailsById(entry.sourceAccountId());
                AccountDetailsDto governmentAccount = accountClient.getGovernmentBankAccountRsd();
                BigDecimal taxInRsd = convertTaxToRsd(sourceAccount.getCurrency(), entry.taxAmount());

                PaymentDto payment = new PaymentDto(
                        sourceAccount.getAccountNumber(),
                        governmentAccount.getAccountNumber(),
                        entry.taxAmount(),
                        taxInRsd,
                        BigDecimal.ZERO,
                        entry.userId()
                );

                var updatedBalances = accountClient.transaction(payment);
                debitSucceeded = true;
                reservation.setTaxAmountRsd(taxInRsd);
                reservation.setStatus(TaxChargeStatus.CHARGED);
                reservation.setChargedAt(LocalDateTime.now());
                taxChargeRepository.save(reservation);

                var payload = createTaxNotificationPayload(entry, taxInRsd, updatedBalances);

                notificationProducer.sendTaxCollected(payload);
                log.info("Collected stock tax {} (RSD {}) for transaction {}.", entry.taxAmount(), taxInRsd, entry.transactionId());
            } catch (Exception ex) {
                handleFailedChargeAttempt(reservation, debitSucceeded);
                log.error("Failed to collect tax for transaction {}", entry.transactionId(), ex);
            }
        }
    }

    @Override
    public void collectMonthlyTaxManually() {
        log.info("Manually triggering monthly tax collection");
        collectMonthlyTax();
    }

    @Override
    public List<TaxDebtResponse> getAllDebts() {
        log.info("Fetching all tax debts");
        Map<Long, BigDecimal> debtMap = new HashMap<>();

        for (TaxChargeEntry entry : buildTaxChargeEntries(HISTORY_START, LocalDateTime.now(), null)) {
            debtMap.merge(entry.userId(), entry.taxAmount(), BigDecimal::add);
        }

        return debtMap.entrySet().stream()
                .map(entry -> new TaxDebtResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public TaxDebtResponse getUserDebt(Long userId) {
        log.info("Fetching tax debt for user {}", userId);

        BigDecimal totalDebt = buildTaxChargeEntries(HISTORY_START, LocalDateTime.now(), userId).stream()
                .map(TaxChargeEntry::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TaxDebtResponse(userId, totalDebt);
    }

    @Override
    public BigDecimal getCurrentYearPaidTax(Long userId) {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        LocalDate firstDayOfYear = now.withDayOfYear(1);
        LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
        return calculateTaxForRange(userId, firstDayOfYear.atStartOfDay(), firstDayOfCurrentMonth.atStartOfDay());
    }

    @Override
    public BigDecimal getCurrentMonthUnpaidTax(Long userId) {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        LocalDate firstDayOfMonth = now.withDayOfMonth(1);
        return calculateTaxForRange(userId, firstDayOfMonth.atStartOfDay(), now.plusDays(1).atStartOfDay());
    }

    @Override
    public List<TaxTrackingRowResponse> getTaxTracking(String userType, String firstName, String lastName) {
        Map<Long, BigDecimal> debtMap = calculateDebtMapInRsd(null, HISTORY_START, LocalDateTime.now());
        List<TaxTrackingRowResponse> rows = new ArrayList<>();

        if (userType == null || "CLIENT".equalsIgnoreCase(userType)) {
            rows.addAll(loadClientTrackingRows(firstName, lastName, debtMap));
        }
        if (userType == null || "ACTUARY".equalsIgnoreCase(userType)) {
            rows.addAll(loadActuaryTrackingRows(firstName, lastName, debtMap));
        }

        return rows;
    }

    private BigDecimal calculateTaxForRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return buildTaxChargeEntries(start, end, userId).stream()
                .map(TaxChargeEntry::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> calculateDebtMapInRsd(Long userIdFilter, LocalDateTime start, LocalDateTime end) {
        Map<Long, BigDecimal> debtMap = new HashMap<>();
        Map<Long, String> currencyCache = new HashMap<>();
        for (TaxChargeEntry entry : buildTaxChargeEntries(start, end, userIdFilter)) {
            BigDecimal taxInRsd = convertTaxEntryToRsd(entry, currencyCache);
            debtMap.merge(entry.userId(), taxInRsd, BigDecimal::add);
        }
        return debtMap;
    }

    private List<TaxTrackingRowResponse> loadClientTrackingRows(String firstName, String lastName, Map<Long, BigDecimal> debtMap) {
        List<TaxTrackingRowResponse> rows = new ArrayList<>();
        int page = 0;
        while (true) {
            CustomerPageResponse customers = clientClient.searchCustomers(firstName, lastName, page, TRACKING_PAGE_SIZE);
            if (customers == null || customers.getContent() == null || customers.getContent().isEmpty()) {
                break;
            }
            rows.addAll(customers.getContent().stream()
                    .map(customer -> new TaxTrackingRowResponse(
                            customer.getFirstName(),
                            customer.getLastName(),
                            "CLIENT",
                            debtMap.getOrDefault(customer.getId(), BigDecimal.ZERO)
                    ))
                    .toList());
            page++;
            if (page >= customers.getTotalPages()) {
                break;
            }
        }
        return rows;
    }

    private List<TaxTrackingRowResponse> loadActuaryTrackingRows(String firstName, String lastName, Map<Long, BigDecimal> debtMap) {
        List<TaxTrackingRowResponse> rows = new ArrayList<>();
        int page = 0;
        while (true) {
            var employees = employeeClient.searchEmployees(null, firstName, lastName, null, page, TRACKING_PAGE_SIZE);
            if (employees == null || employees.getContent() == null || employees.getContent().isEmpty()) {
                break;
            }
            rows.addAll(employees.getContent().stream()
                    .filter(employee -> "AGENT".equals(employee.getRole()))
                    .map(employee -> new TaxTrackingRowResponse(
                            employee.getIme(),
                            employee.getPrezime(),
                            "ACTUARY",
                            debtMap.getOrDefault(employee.getId(), BigDecimal.ZERO)
                    ))
                    .toList());
            page++;
            if (page >= employees.getTotalPages()) {
                break;
            }
        }
        return rows;
    }

    private List<TaxChargeEntry> buildTaxChargeEntries(LocalDateTime start, LocalDateTime end, Long userIdFilter) {
        Map<Long, Order> orderCache = new HashMap<>();
        Map<Long, Boolean> stockListingCache = new HashMap<>();
        List<Transaction> relevantSellTransactions = loadRelevantSellTransactions(start, end, userIdFilter, orderCache, stockListingCache);
        if (relevantSellTransactions.isEmpty()) {
            return List.of();
        }

        List<Transaction> transactions = loadHistoricalTransactionsForRelevantKeys(end, relevantSellTransactions, orderCache, stockListingCache);
        if (transactions.isEmpty()) {
            return List.of();
        }

        Map<UserListingKey, Deque<BuyLot>> buyLots = new HashMap<>();
        List<TaxChargeEntry> charges = new ArrayList<>();

        transactions.sort(Comparator
                .comparing(Transaction::getTimestamp)
                .thenComparing((Transaction tx) -> orderDirectionRank(resolveOrder(orderCache, tx.getOrderId())))
                .thenComparing(Transaction::getId, Comparator.nullsLast(Long::compareTo)));

        for (Transaction tx : transactions) {
            try {
                Order order = resolveOrder(orderCache, tx.getOrderId());
                if (order == null || order.getUserId() == null) {
                    continue;
                }
                if (userIdFilter != null && !userIdFilter.equals(order.getUserId())) {
                    continue;
                }
                if (!isStockOrder(order, stockListingCache)) {
                    continue;
                }
                if (tx.getQuantity() == null || tx.getQuantity() <= 0 || tx.getPricePerUnit() == null) {
                    continue;
                }

                UserListingKey key = new UserListingKey(order.getUserId(), order.getListingId());
                Deque<BuyLot> lots = buyLots.computeIfAbsent(key, ignored -> new ArrayDeque<>());

                if (order.getDirection() == OrderDirection.BUY) {
                    lots.addLast(new BuyLot(tx.getId(), tx.getQuantity(), tx.getPricePerUnit(), order.getAccountId()));
                    continue;
                }
                if (order.getDirection() != OrderDirection.SELL) {
                    continue;
                }

                allocateSellTaxLots(charges, lots, order, tx, start, end);
            } catch (Exception ex) {
                log.error("Error building tax charge entries for transaction {}", tx.getId(), ex);
            }
        }

        return charges;
    }

    private List<Transaction> loadRelevantSellTransactions(
            LocalDateTime start,
            LocalDateTime end,
            Long userIdFilter,
            Map<Long, Order> orderCache,
            Map<Long, Boolean> stockListingCache
    ) {
        List<Order> sellOrders = userIdFilter == null
                ? orderRepository.findByDirection(OrderDirection.SELL)
                : orderRepository.findByUserIdAndDirection(userIdFilter, OrderDirection.SELL);
        if (sellOrders.isEmpty()) {
            return List.of();
        }

        sellOrders.forEach(order -> orderCache.put(order.getId(), order));
        List<Long> sellOrderIds = sellOrders.stream()
                .map(Order::getId)
                .toList();

        return transactionRepository.findByOrderIdInAndTimestampBetween(sellOrderIds, start, end).stream()
                .filter(tx -> {
                    Order order = resolveOrder(orderCache, tx.getOrderId());
                    return order != null && isStockOrder(order, stockListingCache);
                })
                .toList();
    }

    private List<Transaction> loadHistoricalTransactionsForRelevantKeys(
            LocalDateTime end,
            List<Transaction> relevantSellTransactions,
            Map<Long, Order> orderCache,
            Map<Long, Boolean> stockListingCache
    ) {
        Map<Long, Set<Long>> listingsByUser = new HashMap<>();
        Set<Long> relevantUserIds = new HashSet<>();

        for (Transaction sellTransaction : relevantSellTransactions) {
            Order sellOrder = resolveOrder(orderCache, sellTransaction.getOrderId());
            if (sellOrder == null || sellOrder.getUserId() == null || sellOrder.getListingId() == null) {
                continue;
            }
            relevantUserIds.add(sellOrder.getUserId());
            listingsByUser.computeIfAbsent(sellOrder.getUserId(), ignored -> new HashSet<>())
                    .add(sellOrder.getListingId());
        }

        if (relevantUserIds.isEmpty()) {
            return List.of();
        }

        List<Order> candidateOrders = relevantUserIds.size() == 1
                ? orderRepository.findByUserId(relevantUserIds.iterator().next())
                : orderRepository.findByUserIdIn(relevantUserIds);
        candidateOrders.forEach(order -> orderCache.put(order.getId(), order));

        List<Long> orderIds = candidateOrders.stream()
                .filter(order -> belongsToRelevantTaxScope(order, listingsByUser))
                .filter(order -> isStockOrder(order, stockListingCache))
                .map(Order::getId)
                .toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(transactionRepository.findByOrderIdInAndTimestampBefore(orderIds, end));
    }

    private boolean belongsToRelevantTaxScope(Order order, Map<Long, Set<Long>> listingsByUser) {
        if (order == null || order.getUserId() == null || order.getListingId() == null) {
            return false;
        }
        Set<Long> listingIds = listingsByUser.get(order.getUserId());
        return listingIds != null && listingIds.contains(order.getListingId());
    }

    private void allocateSellTaxLots(List<TaxChargeEntry> charges, Deque<BuyLot> lots, Order sellOrder,
                                     Transaction sellTransaction, LocalDateTime start, LocalDateTime end) {
        int quantityToMatch = sellTransaction.getQuantity();

        while (quantityToMatch > 0 && !lots.isEmpty()) {
            BuyLot lot = lots.peekFirst();
            int matchedQuantity = Math.min(quantityToMatch, lot.remainingQuantity());
            BigDecimal gainPerShare = sellTransaction.getPricePerUnit().subtract(lot.purchasePricePerUnit());

            if (!sellTransaction.getTimestamp().isBefore(start)
                    && sellTransaction.getTimestamp().isBefore(end)
                    && gainPerShare.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxableGain = gainPerShare.multiply(BigDecimal.valueOf(matchedQuantity));
                charges.add(new TaxChargeEntry(
                        sellOrder.getUserId(),
                        sellOrder.getListingId(),
                        sellTransaction.getId(),
                        lot.buyTransactionId(),
                        lot.sourceAccountId(),
                        taxableGain.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP)
                ));
            }

            quantityToMatch -= matchedQuantity;
            lot.remainingQuantity(lot.remainingQuantity() - matchedQuantity);
            if (lot.remainingQuantity() == 0) {
                lots.removeFirst();
            }
        }

        if (quantityToMatch > 0) {
            log.warn("Unable to fully match sold stock quantity for transaction {}. Unmatched quantity: {}", sellTransaction.getId(), quantityToMatch);
        }
    }

    private Order resolveOrder(Map<Long, Order> orderCache, Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderCache.computeIfAbsent(orderId, id -> orderRepository.findById(id).orElse(null));
    }

    private boolean isStockOrder(Order order, Map<Long, Boolean> stockListingCache) {
        if (order == null || order.getListingId() == null) {
            return false;
        }
        return stockListingCache.computeIfAbsent(order.getListingId(), listingId -> {
            try {
                return stockClient.getListing(listingId).getListingType() == ListingType.STOCK;
            } catch (Exception ex) {
                log.warn("Unable to resolve listing type for listing {} during tax calculation", listingId, ex);
                return false;
            }
        });
    }

    private BigDecimal convertTaxToRsd(String fromCurrency, BigDecimal taxAmount) {
        if (taxAmount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency == null || "RSD".equalsIgnoreCase(fromCurrency)) {
            return taxAmount;
        }

        try {
            ExchangeRateDto conversion = exchangeClient.calculateWithoutCommission(fromCurrency, "RSD", taxAmount);
            return conversion != null && conversion.getConvertedAmount() != null
                    ? conversion.getConvertedAmount()
                    : taxAmount;
        } catch (Exception ex) {
            log.warn("Exchange failed while converting tax from {} to RSD. Falling back to original amount.", fromCurrency, ex);
            return taxAmount;
        }
    }

    private BigDecimal convertTaxEntryToRsd(TaxChargeEntry entry, Map<Long, String> currencyCache) {
        String currency = currencyCache.computeIfAbsent(entry.sourceAccountId(), accountId -> {
            AccountDetailsDto account = accountClient.getAccountDetailsById(accountId);
            return account != null ? account.getCurrency() : null;
        });
        return convertTaxToRsdStrict(currency, entry.taxAmount(), entry);
    }

    private BigDecimal convertTaxToRsdStrict(String fromCurrency, BigDecimal taxAmount, TaxChargeEntry entry) {
        if (taxAmount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency == null || fromCurrency.isBlank()) {
            throw new IllegalStateException("Missing account currency for tax tracking entry " + entry.transactionId());
        }
        if ("RSD".equalsIgnoreCase(fromCurrency)) {
            return taxAmount;
        }

        try {
            ExchangeRateDto conversion = exchangeClient.calculateWithoutCommission(fromCurrency, "RSD", taxAmount);
            if (conversion == null || conversion.getConvertedAmount() == null) {
                throw new IllegalStateException("Missing converted RSD amount for tax tracking entry " + entry.transactionId());
            }
            return conversion.getConvertedAmount();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to convert tax tracking debt to RSD for transaction " + entry.transactionId(),
                    ex
            );
        }
    }

    private TaxCharge reserveTaxCharge(TaxChargeEntry entry, LocalDateTime start, LocalDateTime end) {
        if (taxChargeRepository.existsBySellTransactionIdAndBuyTransactionId(entry.transactionId(), entry.buyTransactionId())) {
            log.info("Skipping already processed tax charge for sell transaction {} and buy transaction {}.",
                    entry.transactionId(), entry.buyTransactionId());
            return null;
        }

        TaxCharge taxCharge = new TaxCharge();
        taxCharge.setSellTransactionId(entry.transactionId());
        taxCharge.setBuyTransactionId(entry.buyTransactionId());
        taxCharge.setUserId(entry.userId());
        taxCharge.setListingId(entry.listingId());
        taxCharge.setSourceAccountId(entry.sourceAccountId());
        taxCharge.setTaxPeriodStart(start);
        taxCharge.setTaxPeriodEnd(end);
        taxCharge.setTaxAmount(entry.taxAmount());
        taxCharge.setStatus(TaxChargeStatus.RESERVED);

        try {
            return taxChargeRepository.saveAndFlush(taxCharge);
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate tax charge reservation for sell transaction {} and buy transaction {}.",
                    entry.transactionId(), entry.buyTransactionId());
            return null;
        }
    }

    private void handleFailedChargeAttempt(TaxCharge reservation, boolean debitSucceeded) {
        if (reservation == null) {
            return;
        }
        if (!debitSucceeded) {
            taxChargeRepository.delete(reservation);
            return;
        }
        try {
            reservation.setStatus(TaxChargeStatus.CHARGED);
            if (reservation.getChargedAt() == null) {
                reservation.setChargedAt(LocalDateTime.now());
            }
            taxChargeRepository.save(reservation);
        } catch (Exception persistenceEx) {
            log.error("Failed to persist charged tax reservation {} after successful debit; keeping existing reservation state.",
                    reservation.getId(), persistenceEx);
        }
    }

    private Map<String, Object> createTaxNotificationPayload(
            TaxChargeEntry entry,
            BigDecimal taxInRsd,
            com.banka1.order.dto.response.UpdatedBalanceResponseDto updatedBalances
    ) {
        var payload = new HashMap<String, Object>();
        var templateVariables = new HashMap<String, String>();
        templateVariables.put("listingId", String.valueOf(entry.listingId()));
        templateVariables.put("transactionId", String.valueOf(entry.transactionId()));
        templateVariables.put("tax", entry.taxAmount().toPlainString());
        templateVariables.put("taxRsd", taxInRsd.toPlainString());
        if (updatedBalances != null && updatedBalances.getSenderBalance() != null) {
            templateVariables.put("senderBalance", updatedBalances.getSenderBalance().toPlainString());
        }
        if (updatedBalances != null && updatedBalances.getReceiverBalance() != null) {
            templateVariables.put("receiverBalance", updatedBalances.getReceiverBalance().toPlainString());
        }
        payload.put("templateVariables", templateVariables);
        enrichTaxNotificationPayload(payload, entry.userId());
        return payload;
    }

    private void enrichTaxNotificationPayload(Map<String, Object> payload, Long userId) {
        try {
            CustomerDto customer = clientClient.getCustomer(userId);
            if (customer != null) {
                payload.put("username", buildFullName(customer.getFirstName(), customer.getLastName()));
                payload.put("userEmail", customer.getEmail());
                return;
            }
        } catch (Exception ignored) {
            log.debug("User {} not resolved via client-service for tax notification", userId);
        }

        try {
            EmployeeDto employee = employeeClient.getEmployee(userId);
            if (employee != null) {
                payload.put("username", buildFullName(employee.getIme(), employee.getPrezime()));
                payload.put("userEmail", employee.getEmail());
            }
        } catch (Exception ignored) {
            log.debug("User {} not resolved via employee-service for tax notification", userId);
        }
    }

    private String buildFullName(String firstName, String lastName) {
        String safeFirstName = firstName == null ? "" : firstName.trim();
        String safeLastName = lastName == null ? "" : lastName.trim();
        return (safeFirstName + " " + safeLastName).trim();
    }

    private int orderDirectionRank(Order order) {
        if (order == null || order.getDirection() == null) {
            return 2;
        }
        return order.getDirection() == OrderDirection.BUY ? 0 : 1;
    }

    private record UserListingKey(Long userId, Long listingId) {
    }

    private static final class BuyLot {
        private final Long buyTransactionId;
        private int remainingQuantity;
        private final BigDecimal purchasePricePerUnit;
        private final Long sourceAccountId;

        private BuyLot(Long buyTransactionId, int remainingQuantity, BigDecimal purchasePricePerUnit, Long sourceAccountId) {
            this.buyTransactionId = buyTransactionId;
            this.remainingQuantity = remainingQuantity;
            this.purchasePricePerUnit = purchasePricePerUnit;
            this.sourceAccountId = sourceAccountId;
        }

        Long buyTransactionId() {
            return buyTransactionId;
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

        Long sourceAccountId() {
            return sourceAccountId;
        }
    }

    private record TaxChargeEntry(Long userId, Long listingId, Long transactionId, Long buyTransactionId,
                                  Long sourceAccountId, BigDecimal taxAmount) {
    }
}
