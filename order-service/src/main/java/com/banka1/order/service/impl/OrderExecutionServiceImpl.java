package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementation of OrderExecutionService.
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

    @Override
    public void executeOrderAsync(Long orderId) {
        scheduleExecution(orderId, 0L);
    }

    private void processExecutionAttempt(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.APPROVED || order.getRemainingPortions() <= 0 || order.getIsDone()) {
            return;
        }

        selfProvider.getObject().executeOrderPortion(order);

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
        BigDecimal executionPricePerUnit = calculateExecutionPricePerUnit(managedOrder, listing);
        BigDecimal grossChunkAmount = executionPricePerUnit
                .multiply(BigDecimal.valueOf(managedOrder.getContractSize()))
                .multiply(BigDecimal.valueOf(quantityToExecute));
        BigDecimal commission = calculateCommission(orderPricingFamily(managedOrder.getOrderType()), grossChunkAmount, listing.getCurrency());

        createTransaction(managedOrder, quantityToExecute, executionPricePerUnit, grossChunkAmount, commission);
        updatePortfolio(managedOrder, listing, quantityToExecute, executionPricePerUnit);
        transferFunds(managedOrder, listing.getCurrency(), grossChunkAmount);
        finalizeActuaryExposure(managedOrder, listing.getCurrency(), grossChunkAmount);

        managedOrder.setRemainingPortions(managedOrder.getRemainingPortions() - quantityToExecute);
        if (managedOrder.getRemainingPortions() == 0) {
            managedOrder.setIsDone(true);
            managedOrder.setStatus(OrderStatus.DONE);
        }
        orderRepository.save(managedOrder);
    }

    private boolean activateIfEligible(Order order, StockListingDto listing) {
        if (order.getOrderType() == OrderType.STOP) {
            boolean activated = order.getDirection() == OrderDirection.BUY
                    ? listing.getAsk().compareTo(order.getStopValue()) >= 0
                    : listing.getBid().compareTo(order.getStopValue()) < 0;
            if (activated) {
                order.setOrderType(OrderType.MARKET);
                orderRepository.save(order);
            }
            return activated;
        }
        if (order.getOrderType() == OrderType.STOP_LIMIT) {
            boolean activated = order.getDirection() == OrderDirection.BUY
                    ? listing.getAsk().compareTo(order.getStopValue()) >= 0
                    : listing.getBid().compareTo(order.getStopValue()) < 0;
            if (activated) {
                order.setOrderType(OrderType.LIMIT);
                orderRepository.save(order);
            }
            return activated;
        }
        return isExecutableAtCurrentMarket(order, listing);
    }

    private boolean isExecutableAtCurrentMarket(Order order, StockListingDto listing) {
        if (order.getOrderType() == OrderType.MARKET) {
            return true;
        }
        if (order.getOrderType() == OrderType.LIMIT && order.getDirection() == OrderDirection.BUY) {
            return listing.getAsk().compareTo(order.getLimitValue()) <= 0;
        }
        if (order.getOrderType() == OrderType.LIMIT) {
            return listing.getBid().compareTo(order.getLimitValue()) >= 0;
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

    private BigDecimal calculateExecutionPricePerUnit(Order order, StockListingDto listing) {
        return switch (order.getOrderType()) {
            case MARKET -> order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
            case LIMIT -> order.getDirection() == OrderDirection.BUY
                    ? order.getLimitValue().min(listing.getAsk())
                    : order.getLimitValue().max(listing.getBid());
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
        if (portfolio.getQuantity() == 0 && defaultInteger(portfolio.getReservedQuantity()) == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolioRepository.save(portfolio);
        }
    }

    private void transferFunds(Order order, String currency, BigDecimal amount) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        boolean actuaryOrder = actuaryInfoRepository.findByEmployeeId(order.getUserId()).isPresent();

        if (order.getDirection() == OrderDirection.BUY) {
            if (actuaryOrder) {
                return;
            }
            transferForClient(order.getAccountId(), bankAccount.getAccountId(), amount, currency, "Order execution");
            return;
        }

        transferWithoutConversionFee(bankAccount.getAccountId(), order.getAccountId(), amount, currency, "Order execution");
    }

    private void finalizeActuaryExposure(Order order, String currency, BigDecimal amount) {
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
        BigDecimal capUsd = isMarketFamily(orderType) ? new BigDecimal("7") : new BigDecimal("12");
        BigDecimal cap = convertAmount(USD, currency, capUsd);
        return baseAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP).min(cap);
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

    private void transferWithoutConversionFee(Long fromAccountId, Long toAccountId, BigDecimal amount, String currency, String description) {
        if (fromAccountId != null && fromAccountId.equals(toAccountId)) {
            throw new IllegalStateException("Transfer source and destination must differ");
        }
        AccountTransactionRequest request = new AccountTransactionRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setDescription(description);
        accountClient.transfer(request);
    }

    private void transferForClient(Long fromAccountId, Long toAccountId, BigDecimal amount, String currency, String description) {
        AccountDetailsDto fromAccount = accountClient.getAccountDetails(fromAccountId);
        AccountDetailsDto toAccount = accountClient.getAccountDetails(toAccountId);
        if (fromAccount.getCurrency() == null || fromAccount.getCurrency().equalsIgnoreCase(currency)) {
            transferWithoutConversionFee(fromAccountId, toAccountId, amount, currency, description);
            return;
        }

        ExchangeRateDto conversion = exchangeClient.calculate(fromAccount.getCurrency(), currency, amount);
        PaymentDto payment = new PaymentDto(
                fromAccount.getAccountNumber(),
                toAccount.getAccountNumber(),
                amount,
                conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount(),
                conversion.getCommission() == null ? BigDecimal.ZERO : conversion.getCommission(),
                orderOwnerId(fromAccount)
        );
        accountClient.transaction(payment);
    }

    private Long orderOwnerId(AccountDetailsDto account) {
        return account.getOwnerId() == null ? 0L : account.getOwnerId();
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
