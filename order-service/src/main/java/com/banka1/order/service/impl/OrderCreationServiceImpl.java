package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.CreateBuyOrderRequest;
import com.banka1.order.dto.CreateSellOrderRequest;
import com.banka1.order.dto.EmployeeDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.ExchangeStatusDto;
import com.banka1.order.dto.OrderNotificationPayload;
import com.banka1.order.dto.OrderOverviewResponse;
import com.banka1.order.dto.OrderResponse;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderOverviewStatusFilter;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import com.banka1.order.exception.BadRequestException;
import com.banka1.order.exception.BusinessConflictException;
import com.banka1.order.exception.ForbiddenOperationException;
import com.banka1.order.exception.ResourceNotFoundException;
import com.banka1.order.rabbitmq.OrderNotificationProducer;
import com.banka1.order.repository.ActuaryInfoRepository;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.service.OrderCreationService;
import com.banka1.order.service.OrderExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of OrderCreationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCreationServiceImpl implements OrderCreationService {

    static final long NO_APPROVAL_REQUIRED = -1L;
    static final long SYSTEM_APPROVAL = -2L;
    private static final String LIMIT_CURRENCY = "RSD";
    private static final String USD = "USD";

    private final OrderRepository orderRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockClient stockClient;
    private final AccountClient accountClient;
    private final EmployeeClient employeeClient;
    private final ExchangeClient exchangeClient;
    private final OrderExecutionService orderExecutionService;
    private final OrderNotificationProducer orderNotificationProducer;

    @Override
    @Transactional
    public OrderResponse createBuyOrder(AuthenticatedUser user, CreateBuyOrderRequest request) {
        validateBuyOrderRequest(request);

        StockListingDto listing = stockClient.getListing(request.getListingId());
        validateTradingAccess(user, listing);
        ExchangeWindow exchangeWindow = resolveExchangeWindow(listing);
        Long accountId = initialBuyAccountId(user, request.getAccountId(), listing.getCurrency());
        OrderType orderType = determineOrderType(request.getLimitValue(), request.getStopValue());
        BigDecimal approximatePrice = calculateApproximatePrice(orderType, OrderDirection.BUY, listing, request.getQuantity(),
                request.getLimitValue(), request.getStopValue());
        BigDecimal fee = calculateFee(orderType, approximatePrice, listing.getCurrency());

        Order order = buildBaseOrder(user.userId(), request.getListingId(), orderType, request.getQuantity(), listing,
                request.getLimitValue(), request.getStopValue(), OrderDirection.BUY, request.getAllOrNone(),
                request.getMargin(), accountId, exchangeWindow);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setApprovedBy(null);

        order = orderRepository.save(order);
        return mapToResponse(order, approximatePrice, fee, exchangeWindow.closed());
    }

    @Override
    @Transactional
    public OrderResponse createSellOrder(AuthenticatedUser user, CreateSellOrderRequest request) {
        validateSellOrderRequest(request);

        StockListingDto listing = stockClient.getListing(request.getListingId());
        validateTradingAccess(user, listing);
        ensurePortfolioOwnership(user.userId(), request.getListingId(), request.getQuantity());
        ExchangeWindow exchangeWindow = resolveExchangeWindow(listing);
        OrderType orderType = determineOrderType(request.getLimitValue(), request.getStopValue());
        BigDecimal approximatePrice = calculateApproximatePrice(orderType, OrderDirection.SELL, listing, request.getQuantity(),
                request.getLimitValue(), request.getStopValue());
        BigDecimal fee = calculateFee(orderType, approximatePrice, listing.getCurrency());

        Order order = buildBaseOrder(user.userId(), request.getListingId(), orderType, request.getQuantity(), listing,
                request.getLimitValue(), request.getStopValue(), OrderDirection.SELL, request.getAllOrNone(),
                request.getMargin(), request.getAccountId(), exchangeWindow);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setApprovedBy(null);

        order = orderRepository.save(order);
        return mapToResponse(order, approximatePrice, fee, exchangeWindow.closed());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderOverviewResponse> getOrders(OrderOverviewStatusFilter statusFilter, Pageable pageable) {
        List<Order> orders = statusFilter == null || statusFilter == OrderOverviewStatusFilter.ALL
                ? orderRepository.findAll().stream()
                .filter(order -> order.getStatus() != OrderStatus.PENDING_CONFIRMATION)
                .toList()
                : orderRepository.findByStatus(OrderStatus.valueOf(statusFilter.name()));

        Set<Long> listingIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Order order : orders) {
            if (order.getListingId() != null) {
                listingIds.add(order.getListingId());
            }
            if (order.getUserId() != null) {
                userIds.add(order.getUserId());
            }
        }

        Map<Long, StockListingDto> listingCache = new HashMap<>();
        for (Long listingId : listingIds) {
            listingCache.put(listingId, stockClient.getListing(listingId));
        }

        Set<Long> actuaryUserIds = actuaryInfoRepository.findByEmployeeIdIn(userIds).stream()
                .map(ActuaryInfo::getEmployeeId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, EmployeeDto> employeeCache = new HashMap<>();

        List<OrderOverviewResponse> all = orders.stream()
                .map(order -> mapToOverviewResponse(order, listingCache, employeeCache, actuaryUserIds))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(all, pageable, all.size());
        }
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<OrderOverviewResponse> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(AuthenticatedUser user, Long orderId) {
        Order order = getOwnedOrder(user.userId(), orderId);
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
            throw new BusinessConflictException("Only draft orders can be confirmed");
        }

        StockListingDto listing = stockClient.getListing(order.getListingId());
        validateTradingAccess(user, listing);

        BigDecimal approximatePrice = calculateApproximatePrice(order.getOrderType(), order.getDirection(), listing,
                order.getQuantity(), order.getLimitValue(), order.getStopValue());
        BigDecimal fee = calculateFee(orderPricingFamily(order.getOrderType()), approximatePrice, listing.getCurrency());

        if (hasPastSettlementDate(listing)) {
            order.setStatus(OrderStatus.DECLINED);
            order.setApprovedBy(SYSTEM_APPROVAL);
            order.setRemainingPortions(0);
            order.setIsDone(true);
            order = orderRepository.save(order);
            return mapToResponse(order, approximatePrice, fee, order.getExchangeClosed());
        }

        Long fundingAccountId = determineFundingAccountId(user.userId(), order.getAccountId(), listing.getCurrency());
        if (order.getDirection() == OrderDirection.BUY && !user.isClient()) {
            order.setAccountId(fundingAccountId);
        }
        if (Boolean.TRUE.equals(order.getMargin())) {
            checkMarginRequirements(user, fundingAccountId, listing, order.getQuantity());
        } else if (order.getDirection() == OrderDirection.BUY) {
            checkFunds(fundingAccountId, approximatePrice.add(fee));
        }
        ApprovalReservationDecision decision = determineOrderStatusAndReserveExposure(user.userId(), approximatePrice, listing.getCurrency());
        reserveSellQuantityIfNeeded(order);
        if (decision.status() == OrderStatus.APPROVED) {
            transferFee(user, fundingAccountId, fee, listing.getCurrency());
        }

        order.setStatus(decision.status());
        order.setApprovedBy(decision.status() == OrderStatus.APPROVED ? NO_APPROVAL_REQUIRED : null);
        order.setReservedLimitExposure(decision.reservedExposure());
        order = orderRepository.save(order);

        if (decision.status() == OrderStatus.APPROVED) {
            orderExecutionService.executeOrderAsync(order.getId());
        }
        return mapToResponse(order, approximatePrice, fee, order.getExchangeClosed());
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(AuthenticatedUser user, Long orderId) {
        Order order = getOwnedOrderForUpdate(user.userId(), orderId);
        return cancelOrderInternal(order, null);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        return cancelOrder(orderId, null);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Integer quantityToCancel) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return cancelOrderInternal(order, quantityToCancel);
    }

    @Override
    @Transactional
    public OrderResponse approveOrder(Long supervisorId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessConflictException("Only pending orders can be approved");
        }

        StockListingDto listing = stockClient.getListing(order.getListingId());
        if (hasPastSettlementDate(listing)) {
            throw new BusinessConflictException("Orders with past settlement date can only be declined");
        }

        BigDecimal approximatePrice = calculateApproximatePrice(order.getOrderType(), order.getDirection(), listing,
                order.getQuantity(), order.getLimitValue(), order.getStopValue());
        BigDecimal fee = calculateFee(orderPricingFamily(order.getOrderType()), approximatePrice, listing.getCurrency());
        Long fundingAccountId = determineFundingAccountId(order.getUserId(), order.getAccountId(), listing.getCurrency());
        transferFee(order.getUserId(), fundingAccountId, fee, listing.getCurrency());

        if (order.getDirection() == OrderDirection.BUY && !Boolean.TRUE.equals(order.getMargin())) {
            checkFunds(fundingAccountId, approximatePrice);
        }

        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy(supervisorId);
        order = orderRepository.save(order);
        publishOrderDecisionNotification(order, supervisorId, OrderStatus.APPROVED);
        orderExecutionService.executeOrderAsync(order.getId());

        return mapToResponse(order, approximatePrice, fee, order.getExchangeClosed());
    }

    @Override
    @Transactional
    public OrderResponse declineOrder(Long supervisorId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessConflictException("Only pending orders can be declined");
        }
        order = declinePendingOrder(order, supervisorId, true);

        StockListingDto listing = stockClient.getListing(order.getListingId());
        BigDecimal approximatePrice = calculateApproximatePrice(order.getOrderType(), order.getDirection(), listing,
                order.getQuantity(), order.getLimitValue(), order.getStopValue());
        return mapToResponse(order, approximatePrice, calculateFee(orderPricingFamily(order.getOrderType()), approximatePrice, listing.getCurrency()), order.getExchangeClosed());
    }

    @Override
    @Transactional
    public void autoDeclineExpiredPendingOrders() {
        for (Order pendingOrder : orderRepository.findByStatus(OrderStatus.PENDING)) {
            Order lockedOrder = orderRepository.findByIdForUpdate(pendingOrder.getId()).orElse(null);
            if (lockedOrder == null || lockedOrder.getStatus() != OrderStatus.PENDING) {
                continue;
            }

            StockListingDto listing = stockClient.getListing(lockedOrder.getListingId());
            if (!hasPastSettlementDate(listing)) {
                continue;
            }

            declinePendingOrder(lockedOrder, SYSTEM_APPROVAL, true);
        }
    }

    private Order buildBaseOrder(Long userId, Long listingId, OrderType orderType, Integer quantity, StockListingDto listing,
                                 BigDecimal limitValue, BigDecimal stopValue, OrderDirection direction, Boolean allOrNone,
                                 Boolean margin, Long accountId, ExchangeWindow exchangeWindow) {
        Order order = new Order();
        order.setUserId(userId);
        order.setListingId(listingId);
        order.setOrderType(orderType);
        order.setQuantity(quantity);
        order.setContractSize(listing.getContractSize());
        order.setPricePerUnit(getReferencePricePerUnit(orderType, direction, listing, limitValue, stopValue));
        order.setLimitValue(limitValue);
        order.setStopValue(stopValue);
        order.setDirection(direction);
        order.setIsDone(false);
        order.setRemainingPortions(quantity);
        order.setAfterHours(exchangeWindow.afterHours());
        order.setExchangeClosed(exchangeWindow.closed());
        order.setAllOrNone(Boolean.TRUE.equals(allOrNone));
        order.setMargin(Boolean.TRUE.equals(margin));
        order.setAccountId(accountId);
        return order;
    }

    private void validateBuyOrderRequest(CreateBuyOrderRequest request) {
        validateCommonRequest(request.getListingId(), request.getQuantity(),
                request.getLimitValue(), request.getStopValue());
    }

    private void validateSellOrderRequest(CreateSellOrderRequest request) {
        if (request.getAccountId() == null) {
            throw new BadRequestException("Invalid request parameters");
        }
        validateCommonRequest(request.getListingId(), request.getQuantity(),
                request.getLimitValue(), request.getStopValue());
    }

    private void validateCommonRequest(Long listingId, Integer quantity, BigDecimal limitValue, BigDecimal stopValue) {
        if (listingId == null || quantity == null || quantity <= 0) {
            throw new BadRequestException("Invalid request parameters");
        }
        if (limitValue != null && limitValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Limit value must be positive");
        }
        if (stopValue != null && stopValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Stop value must be positive");
        }
    }

    private OrderResponse cancelOrderInternal(Order order, Integer quantityToCancel) {
        if (order.getStatus() == OrderStatus.DONE || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DECLINED) {
            throw new BusinessConflictException("Order can no longer be cancelled");
        }
        StockListingDto listing = stockClient.getListing(order.getListingId());
        if (order.getStatus() == OrderStatus.PENDING && hasPastSettlementDate(listing)) {
            throw new BusinessConflictException("Expired pending orders can only be declined");
        }
        int cancelQuantity = quantityToCancel == null ? order.getRemainingPortions() : quantityToCancel;
        if (cancelQuantity <= 0 || cancelQuantity > order.getRemainingPortions()) {
            throw new BadRequestException("Invalid cancellation quantity");
        }

        releaseReservedState(order, cancelQuantity);
        order.setRemainingPortions(order.getRemainingPortions() - cancelQuantity);
        if (order.getRemainingPortions() == 0) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setIsDone(true);
        }
        order = orderRepository.save(order);

        BigDecimal approximatePrice = calculateApproximatePrice(order.getOrderType(), order.getDirection(), listing,
                order.getQuantity(), order.getLimitValue(), order.getStopValue());
        return mapToResponse(order, approximatePrice,
                calculateFee(orderPricingFamily(order.getOrderType()), approximatePrice, listing.getCurrency()), order.getExchangeClosed());
    }

    private Order declinePendingOrder(Order order, Long approverId, boolean publishNotification) {
        releaseReservedState(order, order.getRemainingPortions());
        order.setStatus(OrderStatus.DECLINED);
        order.setApprovedBy(approverId);
        order.setRemainingPortions(0);
        order.setIsDone(true);
        Order savedOrder = orderRepository.save(order);
        if (publishNotification) {
            publishOrderDecisionNotification(savedOrder, approverId, OrderStatus.DECLINED);
        }
        return savedOrder;
    }

    private Order getOwnedOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Order does not belong to the authenticated user");
        }
        return order;
    }

    private Order getOwnedOrderForUpdate(Long userId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Order does not belong to the authenticated user");
        }
        return order;
    }

    private void ensurePortfolioOwnership(Long userId, Long listingId, Integer requestedQuantity) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingId(userId, listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio position not found"));
        int availableQuantity = portfolio.getQuantity() - defaultInteger(portfolio.getReservedQuantity());
        if (availableQuantity < requestedQuantity) {
            throw new BusinessConflictException("Insufficient portfolio quantity");
        }
    }

    private OrderType determineOrderType(BigDecimal limitValue, BigDecimal stopValue) {
        if (limitValue == null && stopValue == null) {
            return OrderType.MARKET;
        }
        if (limitValue != null && stopValue == null) {
            return OrderType.LIMIT;
        }
        if (limitValue == null) {
            return OrderType.STOP;
        }
        return OrderType.STOP_LIMIT;
    }

    private BigDecimal calculateApproximatePrice(OrderType orderType, OrderDirection direction, StockListingDto listing, Integer quantity,
                                                 BigDecimal limitValue, BigDecimal stopValue) {
        BigDecimal pricePerUnit = getReferencePricePerUnit(orderType, direction, listing, limitValue, stopValue);
        return pricePerUnit.multiply(BigDecimal.valueOf(listing.getContractSize())).multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal getReferencePricePerUnit(OrderType orderType, OrderDirection direction, StockListingDto listing,
                                                BigDecimal limitValue, BigDecimal stopValue) {
        return switch (orderType) {
            case MARKET -> direction == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
            case LIMIT, STOP_LIMIT -> limitValue;
            case STOP -> stopValue;
        };
    }

    private ApprovalReservationDecision determineOrderStatusAndReserveExposure(Long userId, BigDecimal approximatePrice, String currency) {
        ActuaryInfo actuaryInfo = actuaryInfoRepository.findByEmployeeIdForUpdate(userId).orElse(null);
        if (actuaryInfo == null) {
            return new ApprovalReservationDecision(OrderStatus.APPROVED, BigDecimal.ZERO);
        }

        BigDecimal orderAmountInLimitCurrency = convertAmountWithoutCommission(currency, LIMIT_CURRENCY, approximatePrice);
        BigDecimal limit = actuaryInfo.getLimit();
        BigDecimal usedLimit = actuaryInfo.getUsedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getUsedLimit();
        BigDecimal reservedLimit = actuaryInfo.getReservedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getReservedLimit();
        boolean exhausted = limit != null && usedLimit.add(reservedLimit).compareTo(limit) >= 0;
        boolean exceeds = limit != null && usedLimit.add(reservedLimit).add(orderAmountInLimitCurrency).compareTo(limit) > 0;
        if (limit != null) {
            actuaryInfo.setReservedLimit(reservedLimit.add(orderAmountInLimitCurrency));
            actuaryInfoRepository.save(actuaryInfo);
        }
        OrderStatus status = Boolean.TRUE.equals(actuaryInfo.getNeedApproval()) || exhausted || exceeds
                ? OrderStatus.PENDING
                : OrderStatus.APPROVED;
        return new ApprovalReservationDecision(status, limit == null ? BigDecimal.ZERO : orderAmountInLimitCurrency);
    }

    private void checkMarginRequirements(AuthenticatedUser user, Long fundingAccountId, StockListingDto listing, Integer quantity) {
        if (!user.hasMarginPermission()) {
            throw new BusinessConflictException("User does not have margin permission");
        }

        BigDecimal initialMarginCost = calculateInitialMarginCost(listing, quantity);
        AccountDetailsDto account = accountClient.getAccountDetails(fundingAccountId);
        BigDecimal availableCredit = account.getAvailableCredit() == null ? BigDecimal.ZERO : account.getAvailableCredit();
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        boolean hasCredit = availableCredit.compareTo(initialMarginCost) > 0;
        boolean hasFunds = balance.compareTo(initialMarginCost) > 0;
        if (!hasCredit && !hasFunds) {
            throw new BusinessConflictException("Margin requirements are not satisfied");
        }
    }

    private BigDecimal calculateInitialMarginCost(StockListingDto listing, Integer quantity) {
        BigDecimal price = listing.getPrice();
        BigDecimal maintenanceMargin;
        if (listing.getMaintenanceMargin() != null) {
            maintenanceMargin = listing.getMaintenanceMargin();
        } else {
            ListingType listingType = listing.getListingType() == null ? ListingType.STOCK : listing.getListingType();
            maintenanceMargin = switch (listingType) {
                case STOCK -> price.multiply(new BigDecimal("0.50"));
                case FOREX, FUTURES -> BigDecimal.valueOf(listing.getContractSize()).multiply(price).multiply(new BigDecimal("0.10"));
                case OPTION -> BigDecimal.valueOf(listing.getContractSize())
                        .multiply(resolveOptionUnderlyingPrice(listing))
                        .multiply(new BigDecimal("0.50"));
            };
        }

        return maintenanceMargin.multiply(new BigDecimal("1.10"))
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveOptionUnderlyingPrice(StockListingDto listing) {
        return listing.getUnderlyingPrice() != null ? listing.getUnderlyingPrice() : listing.getPrice();
    }

    private void checkFunds(Long accountId, BigDecimal totalAmount) {
        AccountDetailsDto account = accountClient.getAccountDetails(accountId);
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        if (balance.compareTo(totalAmount) < 0) {
            throw new BusinessConflictException("Insufficient funds");
        }
    }

    private BigDecimal calculateFee(OrderType orderType, BigDecimal approximatePrice, String currency) {
        BigDecimal rate = isMarketFamily(orderType) ? new BigDecimal("0.14") : new BigDecimal("0.24");
        BigDecimal maxFeeUsd = isMarketFamily(orderType) ? new BigDecimal("7") : new BigDecimal("12");
        BigDecimal maxFee = convertAmount(USD, currency, maxFeeUsd);
        BigDecimal fee = approximatePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return fee.min(maxFee);
    }

    private boolean isMarketFamily(OrderType orderType) {
        return orderType == OrderType.MARKET || orderType == OrderType.STOP;
    }

    private OrderType orderPricingFamily(OrderType orderType) {
        return orderType == OrderType.STOP_LIMIT ? OrderType.LIMIT : orderType;
    }

    private void transferFee(AuthenticatedUser user, Long fundingAccountId, BigDecimal fee, String currency) {
        transferFee(fundingAccountId, fee, currency, user.isClient());
    }

    private void transferFee(Long userId, Long fundingAccountId, BigDecimal fee, String currency) {
        transferFee(fundingAccountId, fee, currency, !isEmployeeUser(userId));
    }

    private void transferFee(Long fundingAccountId, BigDecimal fee, String currency, boolean applyConversionFee) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        if (fundingAccountId != null && fundingAccountId.equals(bankAccount.getAccountId())) {
            return;
        }
        transferWithConversionIfNeeded(fundingAccountId, bankAccount.getAccountId(), fee, currency, applyConversionFee, "Order fee");
    }

    private boolean hasPastSettlementDate(StockListingDto listing) {
        LocalDate settlementDate = listing.getSettlementDate();
        return settlementDate != null && settlementDate.isBefore(LocalDate.now());
    }

    private Long determineFundingAccountId(Long userId, Long selectedAccountId, String currency) {
        if (actuaryInfoRepository.findByEmployeeId(userId).isPresent()) {
            return employeeClient.getBankAccount(currency).getAccountId(); // actuaries → bank account
        }
        try {
            EmployeeDto employee = employeeClient.getEmployee(userId);
            if (employee != null) {
                return selectedAccountId; // non-actuary employees → own account
            }
        } catch (RuntimeException ignored) {
            // Non-employee users are expected to fund orders from their selected account.
        }
        return selectedAccountId;
    }

    private Long initialBuyAccountId(AuthenticatedUser user, Long selectedAccountId, String currency) {
        if (user.isClient()) {
            if (selectedAccountId == null) {
                throw new BadRequestException("Account is required for client buy orders");
            }
            return selectedAccountId;
        }
        return determineFundingAccountId(user.userId(), selectedAccountId, currency);
    }

    private BigDecimal convertAmount(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        ExchangeRateDto conversion = exchangeClient.calculate(fromCurrency, toCurrency, amount);
        return conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount();
    }

    private BigDecimal convertAmountWithoutCommission(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        ExchangeRateDto conversion = exchangeClient.calculateWithoutCommission(fromCurrency, toCurrency, amount);
        return conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount();
    }

    private OrderOverviewResponse mapToOverviewResponse(Order order, Map<Long, StockListingDto> listingCache,
                                                        Map<Long, EmployeeDto> employeeCache, Set<Long> actuaryUserIds) {
        OrderOverviewResponse response = new OrderOverviewResponse();
        response.setOrderId(order.getId());
        response.setAgentName(resolveAgentName(order.getUserId(), employeeCache, actuaryUserIds));
        response.setOrderType(order.getOrderType());
        response.setListingType(resolveListingType(order.getListingId(), listingCache));
        response.setQuantity(order.getQuantity());
        response.setContractSize(order.getContractSize());
        response.setPricePerUnit(order.getPricePerUnit());
        response.setDirection(order.getDirection());
        response.setRemainingPortions(order.getRemainingPortions());
        response.setStatus(order.getStatus());
        return response;
    }

    private ListingType resolveListingType(Long listingId, Map<Long, StockListingDto> listingCache) {
        StockListingDto listing = listingCache.get(listingId);
        return listing == null ? null : listing.getListingType();
    }

    private String resolveAgentName(Long userId, Map<Long, EmployeeDto> employeeCache, Set<Long> actuaryUserIds) {
        if (!actuaryUserIds.contains(userId)) {
            return null;
        }
        try {
            EmployeeDto employee = employeeCache.computeIfAbsent(userId, employeeClient::getEmployee);
            if (employee == null) {
                return null;
            }
            return formatEmployeeName(employee);
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve employee name for order owner {}", userId, ex);
            return null;
        }
    }

    private String formatEmployeeName(EmployeeDto employee) {
        String firstName = employee.getIme() == null ? "" : employee.getIme().trim();
        String lastName = employee.getPrezime() == null ? "" : employee.getPrezime().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? employee.getUsername() : fullName;
    }

    private void publishOrderDecisionNotification(Order order, Long supervisorId, OrderStatus status) {
        EmployeeDto employee = null;
        try {
            employee = employeeClient.getEmployee(order.getUserId());
        } catch (RuntimeException ex) {
            log.warn("Could not resolve employee data for order owner {} — notification will be sent without employee details", order.getUserId(), ex);
        }
        OrderNotificationPayload payload = new OrderNotificationPayload();
        payload.setOrderId(order.getId());
        payload.setStatus(status);
        payload.setUserId(order.getUserId());
        payload.setSupervisorId(supervisorId);
        payload.setListingId(order.getListingId());
        payload.setOrderType(order.getOrderType());
        payload.setDirection(order.getDirection());
        payload.setUsername(employee != null ? formatEmployeeName(employee) : null);
        payload.setUserEmail(employee != null ? employee.getEmail() : null);
        payload.setTemplateVariables(Map.of(
                "orderId", String.valueOf(order.getId()),
                "status", status.name(),
                "userId", String.valueOf(order.getUserId()),
                "supervisorId", String.valueOf(supervisorId),
                "listingId", String.valueOf(order.getListingId()),
                "orderType", order.getOrderType().name(),
                "direction", order.getDirection().name()
        ));

        if (status == OrderStatus.APPROVED) {
            orderNotificationProducer.sendOrderApproved(payload);
            return;
        }
        orderNotificationProducer.sendOrderDeclined(payload);
    }

    private OrderResponse mapToResponse(Order order, BigDecimal approximatePrice, BigDecimal fee, Boolean exchangeClosed) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setListingId(order.getListingId());
        response.setOrderType(order.getOrderType());
        response.setQuantity(order.getQuantity());
        response.setContractSize(order.getContractSize());
        response.setPricePerUnit(order.getPricePerUnit());
        response.setLimitValue(order.getLimitValue());
        response.setStopValue(order.getStopValue());
        response.setDirection(order.getDirection());
        response.setStatus(order.getStatus());
        response.setApprovedBy(order.getApprovedBy());
        response.setIsDone(order.getIsDone());
        response.setLastModification(order.getLastModification());
        response.setRemainingPortions(order.getRemainingPortions());
        response.setAfterHours(order.getAfterHours());
        response.setExchangeClosed(exchangeClosed);
        response.setAllOrNone(order.getAllOrNone());
        response.setMargin(order.getMargin());
        response.setAccountId(order.getAccountId());
        response.setApproximatePrice(approximatePrice);
        response.setFee(fee);
        return response;
    }

    private void validateTradingAccess(AuthenticatedUser user, StockListingDto listing) {
        if (!user.isClient()) {
            return;
        }
        if (!user.hasTradingPermission()) {
            throw new ForbiddenOperationException("Client does not have trading permission");
        }
        ListingType listingType = listing.getListingType() == null ? ListingType.STOCK : listing.getListingType();
        if (listingType != ListingType.STOCK && listingType != ListingType.FUTURES) {
            throw new ForbiddenOperationException("Clients can trade only stocks and futures");
        }
    }

    private ExchangeWindow resolveExchangeWindow(StockListingDto listing) {
        ExchangeStatusDto exchangeStatus = stockClient.getExchangeStatus(listing.getExchangeId());
        boolean closed = Boolean.TRUE.equals(exchangeStatus.getClosed()) || Boolean.FALSE.equals(exchangeStatus.getOpen());
        boolean afterHours = exchangeStatus.isAfterHours();
        return new ExchangeWindow(closed, afterHours);
    }

    private void reserveSellQuantityIfNeeded(Order order) {
        if (order.getDirection() != OrderDirection.SELL) {
            return;
        }
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingIdForUpdate(order.getUserId(), order.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio position not found"));
        int availableQuantity = portfolio.getQuantity() - defaultInteger(portfolio.getReservedQuantity());
        if (availableQuantity < order.getRemainingPortions()) {
            throw new BusinessConflictException("Insufficient portfolio quantity");
        }
        portfolio.setReservedQuantity(defaultInteger(portfolio.getReservedQuantity()) + order.getRemainingPortions());
        portfolioRepository.save(portfolio);
    }

    private void releaseReservedState(Order order, int quantityToRelease) {
        releaseSellReservation(order, quantityToRelease);
        releaseAgentExposure(order, quantityToRelease);
    }

    private void releaseSellReservation(Order order, int quantityToRelease) {
        if (order.getDirection() != OrderDirection.SELL || quantityToRelease <= 0) {
            return;
        }
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingIdForUpdate(order.getUserId(), order.getListingId()).orElse(null);
        if (portfolio == null) {
            return;
        }
        int newReserved = Math.max(0, defaultInteger(portfolio.getReservedQuantity()) - quantityToRelease);
        portfolio.setReservedQuantity(newReserved);
        portfolioRepository.save(portfolio);
    }

    private void releaseAgentExposure(Order order, int quantityToRelease) {
        if (quantityToRelease <= 0 || order.getReservedLimitExposure() == null || order.getReservedLimitExposure().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ActuaryInfo actuaryInfo = actuaryInfoRepository.findByEmployeeIdForUpdate(order.getUserId()).orElse(null);
        if (actuaryInfo == null || order.getQuantity() == null || order.getQuantity() <= 0) {
            order.setReservedLimitExposure(BigDecimal.ZERO);
            return;
        }
        if (order.getRemainingPortions() == null || order.getRemainingPortions() <= 0) {
            BigDecimal currentReserved = actuaryInfo.getReservedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getReservedLimit();
            BigDecimal releasable = order.getReservedLimitExposure().min(currentReserved);
            actuaryInfo.setReservedLimit(currentReserved.subtract(releasable).max(BigDecimal.ZERO));
            actuaryInfoRepository.save(actuaryInfo);
            order.setReservedLimitExposure(order.getReservedLimitExposure().subtract(releasable).max(BigDecimal.ZERO));
            return;
        }

        BigDecimal releasable = order.getReservedLimitExposure()
                .multiply(BigDecimal.valueOf(quantityToRelease))
                .divide(BigDecimal.valueOf(order.getRemainingPortions()), 4, RoundingMode.HALF_UP)
                .min(order.getReservedLimitExposure());

        BigDecimal currentReserved = actuaryInfo.getReservedLimit() == null ? BigDecimal.ZERO : actuaryInfo.getReservedLimit();
        actuaryInfo.setReservedLimit(currentReserved.subtract(releasable).max(BigDecimal.ZERO));
        actuaryInfoRepository.save(actuaryInfo);
        order.setReservedLimitExposure(order.getReservedLimitExposure().subtract(releasable).max(BigDecimal.ZERO));
    }

    private void transferWithConversionIfNeeded(Long fromAccountId, Long toAccountId, BigDecimal targetAmount, String targetCurrency,
                                                boolean applyConversionFee, String description) {
        AccountDetailsDto fromAccount = accountClient.getAccountDetails(fromAccountId);
        AccountDetailsDto toAccount = accountClient.getAccountDetails(toAccountId);
        if (fromAccount.getCurrency() == null || fromAccount.getCurrency().equalsIgnoreCase(targetCurrency)) {
            AccountTransactionRequest transferRequest = new AccountTransactionRequest();
            transferRequest.setFromAccountId(fromAccountId);
            transferRequest.setToAccountId(toAccountId);
            transferRequest.setAmount(targetAmount);
            transferRequest.setCurrency(targetCurrency);
            transferRequest.setDescription(description);
            accountClient.transfer(transferRequest);
            return;
        }

        ExchangeRateDto conversion = applyConversionFee
                ? exchangeClient.calculate(fromAccount.getCurrency(), targetCurrency, targetAmount)
                : exchangeClient.calculateWithoutCommission(fromAccount.getCurrency(), targetCurrency, targetAmount);

        PaymentDto payment = new PaymentDto(
                fromAccount.getAccountNumber(),
                toAccount.getAccountNumber(),
                targetAmount,
                conversion.getConvertedAmount() == null ? targetAmount : conversion.getConvertedAmount(),
                applyConversionFee && conversion.getCommission() != null ? conversion.getCommission() : BigDecimal.ZERO,
                fromAccount.getOwnerId()
        );
        accountClient.transaction(payment);
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isEmployeeUser(Long userId) {
        if (actuaryInfoRepository.findByEmployeeId(userId).isPresent()) {
            return true;
        }
        try {
            return employeeClient.getEmployee(userId) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record ApprovalReservationDecision(OrderStatus status, BigDecimal reservedExposure) {
    }

    private record ExchangeWindow(boolean closed, boolean afterHours) {
    }
}
