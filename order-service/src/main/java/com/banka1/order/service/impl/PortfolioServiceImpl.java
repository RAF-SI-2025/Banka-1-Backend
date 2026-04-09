package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AuthenticatedUser;
import com.banka1.order.dto.BankAccountDto;
import com.banka1.order.dto.ExchangeRateDto;
import com.banka1.order.dto.PortfolioSummaryResponse;
import com.banka1.order.dto.SetPublicQuantityRequestDto;
import com.banka1.order.dto.PortfolioResponse;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OptionType;
import com.banka1.order.exception.BadRequestException;
import com.banka1.order.exception.BusinessConflictException;
import com.banka1.order.exception.ForbiddenOperationException;
import com.banka1.order.exception.ResourceNotFoundException;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.service.PortfolioService;
import com.banka1.order.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service implementation for managing user portfolios.
 *
 * Responsibilities:
 * - Aggregates portfolio positions per user
 * - Manages public exposure of stock positions (OTC visibility)
 * - Handles option exercise operations
 * - Maps internal Portfolio entities to API responses
 *
 * Integrations:
 * - stock-service (market data, listings)
 * - account-service (future/optional financial operations)
 */
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final int OPTION_CONTRACT_SHARES = 100;
    private static final String RSD = "RSD";

    private final PortfolioRepository portfolioRepository;
    private final StockClient stockClient;
    private final AccountClient accountClient;
    private final EmployeeClient employeeClient;
    private final ExchangeClient exchangeClient;
    private final TaxService taxService;

    /**
     * Retrieves all portfolio positions for a given user and maps them
     * into response DTOs enriched with market and profit information.
     *
     * @param user ID of the user whose portfolio is being fetched
     * @return list of portfolio positions in response format
     */
    @Override
    public PortfolioSummaryResponse getPortfolio(AuthenticatedUser user) {

        List<Portfolio> holdings = portfolioRepository.findByUserId(user.userId());
        Map<Long, StockListingDto> listingsById = holdings.stream()
                .map(Portfolio::getListingId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), stockClient::getListing));

        List<PortfolioResponse> responses = holdings.stream()
                .map(portfolio -> mapToResponse(portfolio, listingsById.get(portfolio.getListingId())))
                .toList();

        BigDecimal totalProfit = responses.stream()
                .map(PortfolioResponse::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PortfolioSummaryResponse summary = new PortfolioSummaryResponse();
        summary.setHoldings(responses);
        summary.setTotalProfit(totalProfit);
        summary.setYearlyTaxPaid(taxService.getCurrentYearPaidTax(user.userId()));
        summary.setMonthlyTaxDue(taxService.getCurrentMonthUnpaidTax(user.userId()));
        return summary;
    }

    /**
     * Sets the number of shares that will be publicly visible for OTC trading.
     * Only applicable to STOCK positions.
     *
     * Business rules:
     * - Only STOCK positions can be made public
     * - publicQuantity cannot exceed total quantity
     * - If publicQuantity > 0, position becomes publicly visible
     *
     * @param portfolioId portfolio position identifier
     * @param request request containing desired public quantity
     * @throws IllegalArgumentException if portfolio not found or invalid type/quantity
     */
    @Override
    public void setPublicQuantity(AuthenticatedUser user, Long portfolioId, SetPublicQuantityRequestDto request) {
        Portfolio portfolio = getOwnedPortfolio(user.userId(), portfolioId);

        if (portfolio.getListingType() != ListingType.STOCK) {
            throw new BadRequestException("Only STOCK positions can be made public");
        }
        if (request.getPublicQuantity() == null || request.getPublicQuantity() < 0) {
            throw new BadRequestException("Public quantity cannot be negative");
        }

        if (request.getPublicQuantity() > portfolio.getQuantity()) {
            throw new BadRequestException("Public quantity cannot exceed total quantity");
        }

        portfolio.setPublicQuantity(request.getPublicQuantity());
        portfolio.setIsPublic(request.getPublicQuantity() > 0);

        portfolioRepository.save(portfolio);
    }

    /**
     * Executes an OPTION position from the portfolio.
     * This system does not directly modify account balances;
     * instead, it calculates realized profit and updates the portfolio state.
     *
     * Business rules:
     * - Only OPTION type positions can be exercised
     * - Uses current market price from stock-service
     * - Option is in-the-money if market price > average purchase price
     * - Each contract represents quantity * contractSize shares
     *
     * Result:
     * - Calculates realized profit for reporting purposes
     * - Marks the position as closed (quantity = 0)
     *
     * @param portfolioId portfolio position ID
     * @param user  executing the option
     * @throws IllegalArgumentException if portfolio is invalid or not in-the-money
     */
    @Override
    @Transactional
    public void exerciseOption(AuthenticatedUser user, Long portfolioId) {
        if (!user.isAgent()) {
            throw new ForbiddenOperationException("Only actuaries can exercise options");
        }

        Portfolio optionPortfolio = getOwnedPortfolio(user.userId(), portfolioId);

        if (optionPortfolio.getListingType() != ListingType.OPTION) {
            throw new BadRequestException("Only OPTION positions can be exercised");
        }

        StockListingDto optionListing = stockClient.getListing(optionPortfolio.getListingId());
        validateOptionListing(optionListing);

        BigDecimal marketPrice = optionListing.getPrice();
        BigDecimal strikePrice = optionListing.getStrikePrice();
        LocalDateTime settlementDate = optionListing.getSettlementDate().atStartOfDay();

        if (settlementDate.isBefore(LocalDateTime.now())) {
            throw new BusinessConflictException("Option already expired");
        }

        boolean inTheMoney;

        if (optionListing.getOptionType() == OptionType.CALL) {
            inTheMoney = marketPrice.compareTo(strikePrice) > 0;
        } else {
            inTheMoney = marketPrice.compareTo(strikePrice) < 0;
        }

        if (!inTheMoney) {
            throw new BusinessConflictException("Option is not in-the-money");
        }

        int exercisedShares = optionPortfolio.getQuantity() * OPTION_CONTRACT_SHARES;
        if (exercisedShares <= 0) {
            throw new BusinessConflictException("Option position has no exercisable contracts");
        }

        StockListingDto underlyingListing = resolveUnderlyingListing(optionListing);
        BigDecimal settlementAmount = strikePrice.multiply(BigDecimal.valueOf(exercisedShares))
                .setScale(2, RoundingMode.HALF_UP);

        validateUnderlyingPositionForExercise(user.userId(), underlyingListing.getId(), exercisedShares, optionListing.getOptionType());
        moveExerciseFunds(user.userId(), optionListing.getCurrency(), settlementAmount, optionListing.getOptionType());
        updateUnderlyingPortfolio(user.userId(), underlyingListing, exercisedShares, strikePrice, optionListing.getOptionType());

        portfolioRepository.delete(optionPortfolio);
    }

    /**
     * Maps a Portfolio entity into a PortfolioResponse DTO.
     *
     * This method currently returns partial data only:
     * - Basic portfolio information
     * - Placeholder values for market-dependent fields
     *
     * TODO:
     * - Fetch current price and ticker from stock-service
     * - Calculate profit using:
     *   (currentPrice - averagePurchasePrice) * quantity
     *
     * @param portfolio entity to be mapped
     * @return mapped response DTO
     */
    private PortfolioResponse mapToResponse(Portfolio portfolio, StockListingDto listing) {

        BigDecimal currentPrice = listing.getPrice();
        BigDecimal avgPrice = portfolio.getAveragePurchasePrice();

        BigDecimal profit = currentPrice
                .subtract(avgPrice)
                .multiply(BigDecimal.valueOf(portfolio.getQuantity()));

        PortfolioResponse response = new PortfolioResponse();

        response.setListingType(portfolio.getListingType());
        response.setQuantity(portfolio.getQuantity());
        response.setPublicQuantity(portfolio.getPublicQuantity());
        response.setLastModified(portfolio.getLastModified());

        response.setTicker(listing.getTicker());
        response.setCurrentPrice(currentPrice);
        response.setAveragePurchasePrice(avgPrice);
        response.setProfit(profit);
        response.setExercisable(portfolio.getListingType() == ListingType.OPTION ? isOptionExercisable(listing) : null);

        return response;
    }

    private Portfolio getOwnedPortfolio(Long userId, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        if (!portfolio.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Portfolio does not belong to the authenticated user");
        }
        return portfolio;
    }

    private boolean isOptionExercisable(StockListingDto listing) {
        if (listing == null || listing.getSettlementDate() == null || listing.getOptionType() == null
                || listing.getPrice() == null || listing.getStrikePrice() == null) {
            return false;
        }
        if (!listing.getSettlementDate().isAfter(LocalDate.now())) {
            return false;
        }
        return listing.getOptionType() == OptionType.CALL
                ? listing.getPrice().compareTo(listing.getStrikePrice()) > 0
                : listing.getPrice().compareTo(listing.getStrikePrice()) < 0;
    }

    private void validateOptionListing(StockListingDto optionListing) {
        if (optionListing.getSettlementDate() == null || optionListing.getStrikePrice() == null || optionListing.getOptionType() == null) {
            throw new BusinessConflictException("Option listing is missing required exercise metadata");
        }
    }

    private StockListingDto resolveUnderlyingListing(StockListingDto optionListing) {
        if (optionListing.getUnderlyingListingId() == null) {
            throw new BusinessConflictException("Option listing is missing underlying listing metadata");
        }
        return stockClient.getListing(optionListing.getUnderlyingListingId());
    }

    private void moveExerciseFunds(Long userId, String currency, BigDecimal settlementAmount, OptionType optionType) {
        BankAccountDto bankAccount = employeeClient.getBankAccount(currency);
        AccountDetailsDto userSettlementAccount = accountClient.getAccountDetails(bankAccount.getAccountId());
        AccountDetailsDto marketAccount = accountClient.getGovernmentBankAccountRsd();
        BigDecimal targetAmount = convertAmount(currency, marketAccount.getCurrency(), settlementAmount);

        PaymentDto payment;
        if (optionType == OptionType.CALL) {
            payment = new PaymentDto(
                    userSettlementAccount.getAccountNumber(),
                    marketAccount.getAccountNumber(),
                    settlementAmount,
                    targetAmount,
                    BigDecimal.ZERO,
                    userId
            );
        } else {
            payment = new PaymentDto(
                    marketAccount.getAccountNumber(),
                    userSettlementAccount.getAccountNumber(),
                    targetAmount,
                    settlementAmount,
                    BigDecimal.ZERO,
                    userId
            );
        }
        accountClient.transaction(payment);
    }

    private void updateUnderlyingPortfolio(Long userId, StockListingDto underlyingListing, int exercisedShares,
                                           BigDecimal strikePrice, OptionType optionType) {
        Portfolio underlyingPortfolio = portfolioRepository.findByUserIdAndListingId(userId, underlyingListing.getId()).orElse(null);

        if (optionType == OptionType.CALL) {
            if (underlyingPortfolio == null) {
                underlyingPortfolio = new Portfolio();
                underlyingPortfolio.setUserId(userId);
                underlyingPortfolio.setListingId(underlyingListing.getId());
                underlyingPortfolio.setListingType(underlyingListing.getListingType() == null ? ListingType.STOCK : underlyingListing.getListingType());
                underlyingPortfolio.setQuantity(exercisedShares);
                underlyingPortfolio.setAveragePurchasePrice(strikePrice);
                portfolioRepository.save(underlyingPortfolio);
                return;
            }

            BigDecimal totalValue = underlyingPortfolio.getAveragePurchasePrice()
                    .multiply(BigDecimal.valueOf(underlyingPortfolio.getQuantity()))
                    .add(strikePrice.multiply(BigDecimal.valueOf(exercisedShares)));
            int newQuantity = underlyingPortfolio.getQuantity() + exercisedShares;
            underlyingPortfolio.setQuantity(newQuantity);
            underlyingPortfolio.setAveragePurchasePrice(totalValue.divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP));
            portfolioRepository.save(underlyingPortfolio);
            return;
        }

        if (underlyingPortfolio == null || underlyingPortfolio.getQuantity() < exercisedShares) {
            throw new BusinessConflictException("Insufficient underlying stock quantity for PUT exercise");
        }
        underlyingPortfolio.setQuantity(underlyingPortfolio.getQuantity() - exercisedShares);
        if (underlyingPortfolio.getQuantity() == 0) {
            portfolioRepository.delete(underlyingPortfolio);
        } else {
            portfolioRepository.save(underlyingPortfolio);
        }
    }

    private void validateUnderlyingPositionForExercise(Long userId, Long underlyingListingId, int exercisedShares,
                                                       OptionType optionType) {
        if (optionType != OptionType.PUT) {
            return;
        }

        Portfolio underlyingPortfolio = portfolioRepository.findByUserIdAndListingId(userId, underlyingListingId).orElse(null);
        if (underlyingPortfolio == null || underlyingPortfolio.getQuantity() < exercisedShares) {
            throw new BusinessConflictException("Insufficient underlying stock quantity for PUT exercise");
        }
    }

    private BigDecimal convertAmount(String fromCurrency, String toCurrency, BigDecimal amount) {
        if (amount == null || fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        ExchangeRateDto conversion = exchangeClient.calculate(fromCurrency, toCurrency, amount);
        return conversion == null || conversion.getConvertedAmount() == null ? amount : conversion.getConvertedAmount();
    }
}
