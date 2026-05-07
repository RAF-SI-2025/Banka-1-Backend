package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.CreditDebitAccountDto;
import com.banka1.order.dto.client.CreditDebitBankDto;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import com.banka1.order.exception.FinancialTransferException;
import com.banka1.order.repository.ActuaryInfoRepository;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.repository.TransactionRepository;
import com.banka1.order.service.OrderExecutionService;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of OrderExecutionService.
 *
 * Handles the execution of approved/pending orders when market conditions are met.
 * Processes orders asynchronously with retry logic for partial executions.
 *
 * Key Responsibilities:
 * <ul>
 *   <li>Execute orders when market conditions match order parameters</li>
 *   <li>Evaluate order conditions (MARKET, LIMIT, STOP, STOP_LIMIT)</li>
 *   <li>Process partial and full fills</li>
 *   <li>Update portfolio positions on successful execution</li>
 *   <li>Create transaction records for executed trades</li>
 *   <li>Calculate and deduct trading fees</li>
 *   <li>Update order status and remaining portions</li>
 *   <li>Release or consume reserved limits</li>
 *   <li>Schedule async executions with retry logic</li>
 * </ul>
 *
 * Execution Logic:
 * <ol>
 *   <li>Validate order is in APPROVED or PENDING_EXECUTION status</li>
 *   <li>Fetch current market data for the listing</li>
 *   <li>Evaluate if order conditions are met (price targets, order type)</li>
 *   <li>Determine fill quantity based on market conditions</li>
 *   <li>Verify account has sufficient balance/margin (buy) or portfolio quantity (sell)</li>
 *   <li>Calculate execution price and fee</li>
 *   <li>Process settlement (debit/credit account)</li>
 *   <li>Update portfolio positions</li>
 *   <li>Create transaction record</li>
 *   <li>Update order status (PARTIALLY_FILLED or EXECUTED)</li>
 *   <li>Schedule retry if partially filled</li>
 * </ol>
 *
 * Service Integrations:
 * <ul>
 *   <li>stock-service: Get current market prices and order execution data</li>
 *   <li>account-service: Settlement and balance verification</li>
 *   <li>employee-service: Get employee and actuary data</li>
 *   <li>exchange-service: Currency conversion for foreign trades</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionServiceImpl implements OrderExecutionService {

    private static final String LIMIT_CURRENCY = "RSD";
    private static final String USD = "USD";
    private static final long MISSING_QUOTE_RETRY_DELAY_MILLIS = 1000L;

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final StockClient stockClient;
    private final AccountClient accountClient;
    private final EmployeeClient employeeClient;
    private final ExchangeClient exchangeClient;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final ObjectProvider<OrderExecutionService> selfProvider;
    private final TaskScheduler orderExecutionTaskScheduler;

    private static final long INITIAL_EXECUTION_DELAY_MILLIS = 60_000L;

    @Override
    public void executeOrderAsync(Long orderId) {
        scheduleExecution(orderId, INITIAL_EXECUTION_DELAY_MILLIS);
    }

    private static final long RETRY_DELAY_ON_ERROR_MILLIS = 5000L;

    private void processExecutionAttempt(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.APPROVED || order.getRemainingPortions() <= 0 || order.getIsDone()) {
            return;
        }

        try {
            selfProvider.getObject().executeOrderPortion(order);
        } catch (FinancialTransferException e) {
            log.error("Order {} execution reached a non-retryable financial transfer state; automatic retries are stopped", orderId, e);
            return;
        } catch (Exception e) {
            log.error("Order {} execution attempt failed, retrying in {}ms", orderId, RETRY_DELAY_ON_ERROR_MILLIS, e);
            scheduleExecution(orderId, RETRY_DELAY_ON_ERROR_MILLIS);
            return;
        }

        Order updatedOrder = orderRepository.findById(orderId).orElse(null);
        if (updatedOrder == null || updatedOrder.getStatus() != OrderStatus.APPROVED
                || updatedOrder.getRemainingPortions() <= 0 || updatedOrder.getIsDone()) {
            return;
        }

        scheduleExecution(orderId, calculateExecutionDelay(updatedOrder));
    }

    private void scheduleExecution(Long orderId, long delayMillis) {
        Instant scheduledTime = Instant.now().plusMillis(Math.max(0L, delayMillis));
        orderExecutionTaskScheduler.schedule(() -> processExecutionAttempt(orderId), scheduledTime);
    }

    @Override
    @Transactional
    public void executeOrderPortion(Order order) {
        Order managedOrder = orderRepository.findByIdForUpdate(order.getId()).orElse(null);
        if (managedOrder == null
                || managedOrder.getRemainingPortions() <= 0
                || managedOrder.getIsDone()
                || managedOrder.getStatus() != OrderStatus.APPROVED) {
            return;
        }

        StockListingDto listing = stockClient.getListing(managedOrder.getListingId());
        if (!hasRequiredQuoteData(managedOrder, listing)) {
            log.warn("Skipping execution attempt for order {} due to missing quote data", managedOrder.getId());
            return;
        }
        if (!activateIfEligible(managedOrder, listing)) {
            return;
        }

        Integer executableCapacity = currentExecutableCapacity(managedOrder, listing);
        if (executableCapacity <= 0) {
            return;
        }
        if (Boolean.TRUE.equals(managedOrder.getAllOrNone()) && executableCapacity < managedOrder.getRemainingPortions()) {
            return;
        }

        int quantityToExecute = determineExecutionQuantity(managedOrder, executableCapacity);
        Optional<BigDecimal> executionPriceOpt = calculateExecutionPricePerUnit(managedOrder, listing);
        if (executionPriceOpt.isEmpty()) {
            return;
        }
        BigDecimal executionPricePerUnit = executionPriceOpt.get();
        BigDecimal grossChunkAmount = executionPricePerUnit
                .multiply(BigDecimal.valueOf(managedOrder.getContractSize()))
                .multiply(BigDecimal.valueOf(quantityToExecute));
        BigDecimal commission = calculateCommission(orderPricingFamily(managedOrder.getOrderType()), grossChunkAmount, listing.getCurrency());

        createTransaction(managedOrder, quantityToExecute, executionPricePerUnit, grossChunkAmount, commission);
        updatePortfolio(managedOrder, listing, quantityToExecute, executionPricePerUnit);
        boolean fundsTransferred = false;
        try {
            transferFunds(managedOrder, listing.getCurrency(), grossChunkAmount);
            fundsTransferred = true;
            finalizeActuaryExposure(managedOrder, listing.getCurrency(), grossChunkAmount);

            managedOrder.setRemainingPortions(managedOrder.getRemainingPortions() - quantityToExecute);
            if (managedOrder.getRemainingPortions() == 0) {
                managedOrder.setIsDone(true);
                managedOrder.setStatus(OrderStatus.DONE);
            }
            orderRepository.save(managedOrder);
        } catch (FinancialTransferException e) {
            throw e;
        } catch (Exception e) {
            if (fundsTransferred) {
                compensateCompletedTransfer(managedOrder, listing.getCurrency(), grossChunkAmount);
                throw new FinancialTransferException(
                        "Order " + managedOrder.getId() + " failed after funds were transferred; automatic retry blocked",
                        e
                );
            }
            throw e;
        }
    }

    private boolean activateIfEligible(Order order, StockListingDto listing) {
        if (order.getOrderType() == OrderType.STOP) {
            if (!canEvaluateStop(order, listing)) {
                return false;
            }
            boolean activated = isStopActivated(order, listing);
            if (activated) {
                order.setOrderType(OrderType.MARKET);
                orderRepository.save(order);
            }
            return activated;
        }
        if (order.getOrderType() == OrderType.STOP_LIMIT) {
            if (!canEvaluateStop(order, listing)) {
                return false;
            }
            boolean activated = isStopActivated(order, listing);
            if (activated) {
                order.setOrderType(OrderType.LIMIT);
                orderRepository.save(order);
            }
            return activated;
        }
        return isExecutableAtCurrentMarket(order, listing);
    }

    private boolean canEvaluateStop(Order order, StockListingDto listing) {
        BigDecimal quote = order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
        if (quote == null) {
            log.warn("Skipping STOP activation for order {}: {} quote is unavailable",
                    order.getId(),
                    order.getDirection() == OrderDirection.BUY ? "ask" : "bid");
            return false;
        }
        return true;
    }

    private boolean isStopActivated(Order order, StockListingDto listing) {
        BigDecimal quote = order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
        return order.getDirection() == OrderDirection.BUY
                ? quote.compareTo(order.getStopValue()) >= 0
                : quote.compareTo(order.getStopValue()) < 0;
    }

    private boolean isExecutableAtCurrentMarket(Order order, StockListingDto listing) {
        if (order.getOrderType() == OrderType.MARKET) {
            return true;
        }
        if (order.getOrderType() == OrderType.LIMIT && order.getDirection() == OrderDirection.BUY) {
            BigDecimal ask = listing.getAsk();
            if (ask == null) {
                log.warn("Skipping BUY LIMIT eligibility check for order {}: ask quote is unavailable", order.getId());
                return false;
            }
            return ask.compareTo(order.getLimitValue()) <= 0;
        }
        if (order.getOrderType() == OrderType.LIMIT) {
            BigDecimal bid = listing.getBid();
            if (bid == null) {
                log.warn("Skipping SELL LIMIT eligibility check for order {}: bid quote is unavailable", order.getId());
                return false;
            }
            return bid.compareTo(order.getLimitValue()) >= 0;
        }
        return false;
    }

    private Integer currentExecutableCapacity(Order order, StockListingDto listing) {
        long volume = listing.getVolume() == null ? order.getRemainingPortions() : listing.getVolume();
        return (int) Math.max(0L, Math.min(volume, order.getRemainingPortions().longValue()));
    }

    private int determineExecutionQuantity(Order order, Integer executableCapacity) {
        if (Boolean.TRUE.equals(order.getAllOrNone())) {
            return order.getRemainingPortions();
        }
        return ThreadLocalRandom.current().nextInt(executableCapacity) + 1;
    }

    private Optional<BigDecimal> calculateExecutionPricePerUnit(Order order, StockListingDto listing) {
        return switch (order.getOrderType()) {
            case MARKET -> {
                BigDecimal marketQuote = order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
                if (marketQuote == null) {
                    log.warn("Skipping MARKET execution for order {}: {} quote is unavailable",
                            order.getId(),
                            order.getDirection() == OrderDirection.BUY ? "ask" : "bid");
                    yield Optional.empty();
                }
                yield Optional.of(marketQuote);
            }
            case LIMIT -> {
                if (order.getDirection() == OrderDirection.BUY) {
                    BigDecimal ask = listing.getAsk();
                    if (ask == null) {
                        log.warn("Skipping BUY LIMIT execution for order {}: ask quote is unavailable", order.getId());
                        yield Optional.empty();
                    }
                    yield Optional.of(order.getLimitValue().min(ask));
                }
                BigDecimal bid = listing.getBid();
                if (bid == null) {
                    log.warn("Skipping SELL LIMIT execution for order {}: bid quote is unavailable", order.getId());
                    yield Optional.empty();
                }
                yield Optional.of(order.getLimitValue().max(bid));
            }
            case STOP, STOP_LIMIT -> throw new IllegalStateException("Stop-family orders must be activated before execution");
        };
    }

    private void createTransaction(Order order, int quantity, BigDecimal executionPricePerUnit, BigDecimal grossChunkAmount, BigDecimal commission) {
        Transaction transaction = new Transaction();
        transaction.setOrderId(order.getId());
        transaction.setQuantity(quantity);
        transaction.setPricePerUnit(executionPricePerUnit);
        transaction.setTotalPrice(grossChunkAmount);
        transaction.setCommission(commission);
        transaction.setTimestamp(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    private void updatePortfolio(Order order, StockListingDto listing, int quantity, BigDecimal executionPricePerUnit) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingIdForUpdate(order.getUserId(), order.getListingId()).orElse(null);

        if (order.getDirection() == OrderDirection.BUY) {
            if (portfolio == null) {
                portfolio = new Portfolio();
                portfolio.setUserId(order.getUserId());
                portfolio.setListingId(order.getListingId());
                portfolio.setListingType(listing.getListingType() == null ? ListingType.STOCK : listing.getListingType());
                portfolio.setQuantity(quantity);
                portfolio.setReservedQuantity(0);
                portfolio.setAveragePurchasePrice(executionPricePerUnit);
            } else {
                BigDecimal totalValue = portfolio.getAveragePurchasePrice()
                        .multiply(BigDecimal.valueOf(portfolio.getQuantity()))
                        .add(executionPricePerUnit.multiply(BigDecimal.valueOf(quantity)));
                int newQuantity = portfolio.getQuantity() + quantity;
                portfolio.setQuantity(newQuantity);
                portfolio.setAveragePurchasePrice(totalValue.divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP));
            }
            portfolioRepository.save(portfolio);
            return;
        }

        if (portfolio == null || portfolio.getQuantity() < quantity) {
            throw new IllegalStateException("Cannot execute sell order without owned quantity");
        }

        portfolio.setReservedQuantity(Math.max(0, defaultInteger(portfolio.getReservedQuantity()) - quantity));
        portfolio.setQuantity(portfolio.getQuantity() - quantity);
        portfolio.setPublicQuantity(Math.min(defaultInteger(portfolio.getPublicQuantity()), portfolio.getQuantity()));
        if (portfolio.getQuantity() == 0 && defaultInteger(portfolio.getReservedQuantity()) == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolioRepository.save(portfolio);
        }
    }

    private void transferFunds(Order order, String currency, BigDecimal amount) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        boolean actuaryOrder = bankAccount.getAccountId() != null && bankAccount.getAccountId().equals(order.getAccountId());

        if (order.getDirection() == OrderDirection.BUY) {
            if (actuaryOrder) {
                accountClient.debitBank(new CreditDebitBankDto(currency, amount));
                return;
            }
            AccountDetailsDto clientAccount = accountClient.getAccountDetails(order.getAccountId());
            accountClient.debit(new CreditDebitAccountDto(
                    clientAccount.getAccountNumber(),
                    amount,
                    order.getUserId()
            ));
            try {
                accountClient.creditBank(new CreditDebitBankDto(currency, amount));
            } catch (Exception e) {
                compensateClientAccountCredit(clientAccount.getAccountNumber(), amount, order.getUserId());
                throw new FinancialTransferException(
                        "Client BUY transfer could not be completed after debiting the client account",
                        e
                );
            }
            return;
        }

        accountClient.debitBank(new CreditDebitBankDto(currency, amount));
        try {
            AccountDetailsDto clientAccount = accountClient.getAccountDetails(order.getAccountId());
            accountClient.credit(new CreditDebitAccountDto(
                    clientAccount.getAccountNumber(),
                    amount,
                    order.getUserId()
            ));
        } catch (Exception e) {
            compensateBankCredit(currency, amount);
            throw new FinancialTransferException(
                    "SELL payout could not be completed after debiting the bank account",
                    e
            );
        }
    }

    private void compensateCompletedTransfer(Order order, String currency, BigDecimal amount) {
        try {
            if (order.getDirection() == OrderDirection.BUY) {
                compensateBuyTransfer(order, currency, amount);
                return;
            }
            compensateSellTransfer(order, currency, amount);
        } catch (Exception compensationError) {
            log.error("Compensation failed for order {}", order.getId(), compensationError);
        }
    }

    private void compensateBuyTransfer(Order order, String currency, BigDecimal amount) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        boolean bankFundedBuy = bankAccount.getAccountId() != null && bankAccount.getAccountId().equals(order.getAccountId());
        if (bankFundedBuy) {
            compensateBankCredit(currency, amount);
            return;
        }

        AccountDetailsDto clientAccount = accountClient.getAccountDetails(order.getAccountId());
        compensateBankDebit(currency, amount);
        compensateClientAccountCredit(clientAccount.getAccountNumber(), amount, order.getUserId());
    }

    private void compensateSellTransfer(Order order, String currency, BigDecimal amount) {
        AccountDetailsDto clientAccount = accountClient.getAccountDetails(order.getAccountId());
        compensateClientAccountDebit(clientAccount.getAccountNumber(), amount, order.getUserId());
        compensateBankCredit(currency, amount);
    }

    private void compensateClientAccountCredit(String accountNumber, BigDecimal amount, Long clientId) {
        accountClient.credit(new CreditDebitAccountDto(accountNumber, amount, clientId));
    }

    private void compensateClientAccountDebit(String accountNumber, BigDecimal amount, Long clientId) {
        accountClient.debit(new CreditDebitAccountDto(accountNumber, amount, clientId));
    }

    private void compensateBankCredit(String currency, BigDecimal amount) {
        accountClient.creditBank(new CreditDebitBankDto(currency, amount));
    }

    private void compensateBankDebit(String currency, BigDecimal amount) {
        accountClient.debitBank(new CreditDebitBankDto(currency, amount));
    }

    private void finalizeActuaryExposure(Order order, String currency, BigDecimal amount) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        if (bankAccount.getAccountId() == null || !bankAccount.getAccountId().equals(order.getAccountId())) {
            return;
        }
        ActuaryInfo actuaryInfo = actuaryInfoRepository.findByEmployeeIdForUpdate(order.getUserId()).orElse(null);
        if (actuaryInfo == null) {
            return;
        }
        BigDecimal converted = convertAmountWithoutCommission(currency, LIMIT_CURRENCY, amount);
        BigDecimal reservedLimit = actuaryInfo.getReservedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getReservedLimit();
        actuaryInfo.setReservedLimit(reservedLimit.subtract(converted).max(BigDecimal.ZERO));
        BigDecimal usedLimit = actuaryInfo.getUsedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getUsedLimit();
        actuaryInfo.setUsedLimit(usedLimit.add(converted));
        actuaryInfoRepository.save(actuaryInfo);
        BigDecimal orderReserved = order.getReservedLimitExposure() == null ? BigDecimal.ZERO : order.getReservedLimitExposure();
        order.setReservedLimitExposure(orderReserved.subtract(converted).max(BigDecimal.ZERO));
    }

    private long calculateExecutionDelay(Order order) {
        StockListingDto listing = stockClient.getListing(order.getListingId());
        if (listing == null || !hasRequiredQuoteData(order, listing)) {
            return MISSING_QUOTE_RETRY_DELAY_MILLIS;
        }
        long volume = listing.getVolume() == null || listing.getVolume() <= 0 ? 1L : listing.getVolume();
        double maxSeconds = (24d * 60d) / (volume / (double) Math.max(1, order.getRemainingPortions()));
        double delaySeconds = ThreadLocalRandom.current().nextDouble() * maxSeconds;
        if (Boolean.TRUE.equals(order.getAfterHours())) {
            delaySeconds += 30d * 60d;
        }
        return (long) (delaySeconds * 1000);
    }

    private BigDecimal calculateCommission(OrderType orderType, BigDecimal baseAmount, String currency) {
        BigDecimal rate = isMarketFamily(orderType) ? new BigDecimal("0.14") : new BigDecimal("0.24");
        BigDecimal commission = baseAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        try {
            BigDecimal capUsd = isMarketFamily(orderType) ? new BigDecimal("7") : new BigDecimal("12");
            BigDecimal cap = convertAmount(USD, currency, capUsd);
            return commission.min(cap);
        } catch (Exception e) {
            log.warn("Cannot convert commission cap from USD to {}, applying uncapped commission rate", currency);
            return commission;
        }
    }

    private boolean isMarketFamily(OrderType orderType) {
        return orderType == OrderType.MARKET || orderType == OrderType.STOP;
    }

    private OrderType orderPricingFamily(OrderType orderType) {
        return orderType == OrderType.STOP_LIMIT ? OrderType.LIMIT : orderType;
    }

    private boolean hasRequiredQuoteData(Order order, StockListingDto listing) {
        if (listing == null || listing.getPrice() == null) {
            return false;
        }
        if (order.getDirection() == OrderDirection.BUY) {
            return listing.getAsk() != null;
        }
        return listing.getBid() != null;
    }

    private BigDecimal convertAmount(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (amount == null || fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        ExchangeRateDto conversion = exchangeClient.calculate(fromCurrency, toCurrency, amount);
        return conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount();
    }

    private BigDecimal convertAmountWithoutCommission(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (amount == null || fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        ExchangeRateDto conversion = exchangeClient.calculateWithoutCommission(fromCurrency, toCurrency, amount);
        return conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount();
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
