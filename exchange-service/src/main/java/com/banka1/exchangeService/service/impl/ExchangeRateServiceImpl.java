package com.banka1.exchangeService.service.impl;

import com.banka1.exchangeService.client.TwelveDataClient;
import com.banka1.exchangeService.config.ExchangeRateProperties;
import com.banka1.exchangeService.domain.ExchangeRateEntity;
import com.banka1.exchangeService.domain.SupportedCurrency;
import com.banka1.exchangeService.dto.ConversionRequestDto;
import com.banka1.exchangeService.dto.ConversionResponseDto;
import com.banka1.exchangeService.dto.ExchangeRateDto;
import com.banka1.exchangeService.dto.ExchangeRateFetchResponseDto;
import com.banka1.exchangeService.dto.TwelveDataRateResponse;
import com.banka1.exchangeService.exception.BusinessException;
import com.banka1.exchangeService.exception.ErrorCode;
import com.banka1.exchangeService.repository.ExchangeRateRepository;
import com.banka1.exchangeService.service.ExchangeRateService;
import com.banka1.exchangeService.service.ExchangeRateSnapshotPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Implementation of the exchange rate service for managing daily rates and client conversions.
 * Handles fetching rates from external providers, storing them locally, retrieving rates,
 * and converting amounts between currencies using RSD as the base currency.
 */
@Slf4j
@Service
public class ExchangeRateServiceImpl implements ExchangeRateService {

    /**
     * Number of decimal places used for division in conversion calculations.
     */
    private static final int CALCULATION_SCALE = 8;
    /**
     * Number of decimal places for displaying commission amounts.
     */
    private static final int COMMISSION_SCALE = 2;
    /**
     * Number of decimal places used when storing exchange rates in the database.
     */
    private static final int RATE_SCALE = 8;
    /**
     * Constant 100 for converting margin percentage to decimal factor.
     */
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Client for fetching exchange rates from external provider (Twelve Data).
     */
    private final TwelveDataClient twelveDataClient;
    /**
     * Configuration properties for fetch rules and margin percentage.
     */
    private final ExchangeRateProperties exchangeRateProperties;

    /**
     * Repository for local storage and retrieval of rate snapshots.
     */
    private final ExchangeRateRepository exchangeRateRepository;
    /**
     * Service for atomic replacement of entire rate snapshots.
     */
    private final ExchangeRateSnapshotPersistenceService snapshotPersistenceService;

    /**
     * Clock used to determine the target fallback date when fetch fails.
     */
    private final Clock clock;

    /**
     * Creates a service instance with the default UTC clock for production use.
     *
     * @param twelveDataClient              client for external rate fetching
     * @param exchangeRateProperties        configuration for margin and fetch rules
     * @param exchangeRateRepository        repository for local rates
     * @param snapshotPersistenceService    service for atomic snapshot persistence
     */
    @Autowired
    public ExchangeRateServiceImpl(
            TwelveDataClient twelveDataClient,
            ExchangeRateProperties exchangeRateProperties,
            ExchangeRateRepository exchangeRateRepository,
            ExchangeRateSnapshotPersistenceService snapshotPersistenceService
    ) {
        this(
                twelveDataClient,
                exchangeRateProperties,
                exchangeRateRepository,
                snapshotPersistenceService,
                Clock.systemUTC()
        );
    }

    /**
     * Creates a service instance with an explicit clock, useful for tests and deterministic fallback dates.
     *
     * @param twelveDataClient              client for external rate fetching
     * @param exchangeRateProperties        configuration for margin and fetch rules
     * @param exchangeRateRepository        repository for local rates
     * @param snapshotPersistenceService    service for atomic snapshot persistence
     * @param clock                         time source for fallback logic
     */
    ExchangeRateServiceImpl(
            TwelveDataClient twelveDataClient,
            ExchangeRateProperties exchangeRateProperties,
            ExchangeRateRepository exchangeRateRepository,
            ExchangeRateSnapshotPersistenceService snapshotPersistenceService,
            Clock clock
    ) {
        this.twelveDataClient = twelveDataClient;
        this.exchangeRateProperties = exchangeRateProperties;
        this.exchangeRateRepository = exchangeRateRepository;
        this.snapshotPersistenceService = snapshotPersistenceService;
        this.clock = clock;
    }

    @Override
    public ExchangeRateFetchResponseDto fetchAndStoreDailyRates() {
        try {
            List<TwelveDataRateResponse> fetchedRates = SupportedCurrency.trackedCurrencyCodes().stream()
                    .map(this::fetchRate)
                    .toList();

            LocalDate snapshotDate = resolveFetchedSnapshotDate(fetchedRates);
            List<ExchangeRateDto> storedRates = snapshotPersistenceService.replaceSnapshot(
                            snapshotDate,
                            fetchedRates.stream()
                                    .map(this::toPreparedRate)
                                    .toList()
                    ).stream()
                    .map(this::toDto)
                    .toList();

            return new ExchangeRateFetchResponseDto(storedRates.size(), storedRates, false, snapshotDate);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.EXCHANGE_RATE_FETCH_FAILED) {
                throw ex;
            }
            return fallbackToLatestSnapshot(ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExchangeRateDto> getRates(LocalDate date) {
        LocalDate snapshotDate = resolveSnapshotDate(date);
        return exchangeRateRepository.findAllByDateOrderByCurrencyCodeAsc(snapshotDate)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangeRateDto getRate(String currencyCode, LocalDate date) {
        LocalDate snapshotDate = resolveSnapshotDate(date);
        SupportedCurrency currency = SupportedCurrency.from(currencyCode);
        if (currency == SupportedCurrency.RSD) {
            return ExchangeRateDto.baseCurrency(snapshotDate);
        }

        ExchangeRateEntity entity = exchangeRateRepository.findByCurrencyCodeAndDate(currency.name(), snapshotDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EXCHANGE_RATE_NOT_FOUND,
                        "Kurs za valutu %s nije pronadjen za datum %s.".formatted(currency.name(), snapshotDate)
                ));
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionResponseDto convert(ConversionRequestDto request) {
        return convertInternal(request, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionResponseDto convertWithoutCommission(ConversionRequestDto request) {
        return convertInternal(request, false);
    }

    private ConversionResponseDto convertInternal(ConversionRequestDto request, boolean includeCommission) {
        SupportedCurrency sourceCurrency = SupportedCurrency.from(request.fromCurrency());
        SupportedCurrency targetCurrency = SupportedCurrency.from(request.toCurrency());
        LocalDate snapshotDate = resolveSnapshotDate(request.date());

        if (sourceCurrency == targetCurrency) {
            return new ConversionResponseDto(
                    sourceCurrency.name(),
                    targetCurrency.name(),
                    request.amount(),
                    request.amount(),
                    BigDecimal.ONE.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(COMMISSION_SCALE, RoundingMode.HALF_UP),
                    snapshotDate
            );
        }

        BigDecimal sourceBuyingRate = sourceCurrency == SupportedCurrency.RSD
                ? BigDecimal.ONE
                : findRate(sourceCurrency, snapshotDate).getBuyingRate();
        BigDecimal targetSellingRate = targetCurrency == SupportedCurrency.RSD
                ? BigDecimal.ONE
                : findRate(targetCurrency, snapshotDate).getSellingRate();

        BigDecimal amountInRsd = sourceCurrency == SupportedCurrency.RSD
                ? request.amount()
                : request.amount().multiply(sourceBuyingRate);

        BigDecimal convertedAmount = targetCurrency == SupportedCurrency.RSD
                ? amountInRsd.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP)
                : amountInRsd.divide(targetSellingRate, CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal effectiveRate = convertedAmount.divide(request.amount(), CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal commission = includeCommission
                ? request.amount()
                .multiply(resolveCommissionFactor())
                .setScale(COMMISSION_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(COMMISSION_SCALE, RoundingMode.HALF_UP);

        return new ConversionResponseDto(
                sourceCurrency.name(),
                targetCurrency.name(),
                request.amount(),
                convertedAmount,
                effectiveRate,
                commission,
                snapshotDate
        );
    }
    /**
     * Fetches the exchange rate for one supported currency relative to the base RSD currency.
     *
     * @param currencyCode three-letter ISO code of the source currency
     * @return parsed response from the Twelve Data provider
     */
    private TwelveDataRateResponse fetchRate(String currencyCode) {
        return twelveDataClient.fetchExchangeRate(
                currencyCode,
                SupportedCurrency.RSD.name()
        );
    }

    /**
     * Converts a provider response into a fully prepared row for the local snapshot.
     *
     * @param response parsed response from the external provider
     * @return prepared rate ready for atomic persistence
     */
    private ExchangeRateSnapshotPersistenceService.PreparedExchangeRate toPreparedRate(TwelveDataRateResponse response) {
        return new ExchangeRateSnapshotPersistenceService.PreparedExchangeRate(
                response.fromCurrency(),
                calculateBuyingRate(response.rate()),
                calculateSellingRate(response.rate())
        );
    }

    /**
     * Calculates the bank's buying rate from the market rate and configured margin percentage.
     * Formula: {@code buyingRate = marketRate * (1 - margin/100)}.
     * For example, with market rate {@code 117.40} and margin {@code 1.0},
     * the bank buys at {@code 117.40 * 0.99 = 116.22600000}.
     *
     * @param marketRate market rate obtained from the provider
     * @return bank's buying rate for storage in the snapshot
     */
    private BigDecimal calculateBuyingRate(BigDecimal marketRate) {
        BigDecimal marginFactor = resolveMarginFactor();
        return marketRate.multiply(BigDecimal.ONE.subtract(marginFactor))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the bank's selling rate from the market rate and configured margin percentage.
     * Formula: {@code sellingRate = marketRate * (1 + margin/100)}.
     * For example, with market rate {@code 117.40} and margin {@code 1.0},
     * the bank sells at {@code 117.40 * 1.01 = 118.57400000}.
     *
     * @param marketRate market rate obtained from the provider
     * @return bank's selling rate for storage in the snapshot
     */
    private BigDecimal calculateSellingRate(BigDecimal marketRate) {
        BigDecimal marginFactor = resolveMarginFactor();
        return marketRate.multiply(BigDecimal.ONE.add(marginFactor))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Converts the percentage margin from configuration into a decimal factor.
     * For example, {@code 1.0} becomes {@code 0.01}.
     *
     * @return decimal margin factor suitable for mathematical calculations
     */
    private BigDecimal resolveMarginFactor() {
        return exchangeRateProperties.marginPercentage()
                .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Converts the percentage commission from configuration into a decimal factor for calculation.
     *
     * @return decimal commission factor
     */
    private BigDecimal resolveCommissionFactor() {
        return exchangeRateProperties.commissionPercentage()
                .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Activates fallback to the latest local snapshot when external fetch fails.
     *
     * @param rootCause original error from the fetch operation
     * @return result with fallback snapshot for the target date
     */
    private ExchangeRateFetchResponseDto fallbackToLatestSnapshot(BusinessException rootCause) {
        LocalDate targetDate = LocalDate.now(clock);
        LocalDate latestSnapshotDate = exchangeRateRepository.findLatestDate();
        if (latestSnapshotDate == null) {
            log.error("Exchange-rate fetch failed and no local snapshot exists for fallback. Root cause: {}", rootCause.getMessage());
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Exchange-rate fetch failed and no local snapshot exists for fallback."
            );
        }
        List<ExchangeRateEntity> previousRates = exchangeRateRepository.findAllByDateOrderByCurrencyCodeAsc(latestSnapshotDate);
        if (previousRates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Exchange-rate fetch failed and latest snapshot %s has no rates.".formatted(latestSnapshotDate)
            );
        }

        log.warn("Exchange-rate fetch failed; copying latest snapshot from {} to {}. Cause: {}",
                latestSnapshotDate, targetDate, rootCause.getMessage());
        List<ExchangeRateDto> fallbackRates = snapshotPersistenceService.replaceSnapshot(
                        targetDate,
                        previousRates.stream()
                                .map(this::toPreparedRate)
                                .toList()
                ).stream()
                .map(this::toDto)
                .toList();
        return new ExchangeRateFetchResponseDto(fallbackRates.size(), fallbackRates, true, latestSnapshotDate);
    }

    /**
     * Converts a locally stored rate into a prepared row for a new snapshot date.
     *
     * @param previousRate previously stored exchange rate
     * @return prepared rate ready for atomic persistence in fallback snapshot
     */
    private ExchangeRateSnapshotPersistenceService.PreparedExchangeRate toPreparedRate(ExchangeRateEntity previousRate) {
        return new ExchangeRateSnapshotPersistenceService.PreparedExchangeRate(
                previousRate.getCurrencyCode(),
                previousRate.getBuyingRate(),
                previousRate.getSellingRate()
        );
    }

    /**
     * Requires that all fetched provider responses belong to the same snapshot date.
     *
     * @param fetchedRates complete set of fetched rates
     * @return unique snapshot date that will be stored locally
     */
    private LocalDate resolveFetchedSnapshotDate(List<TwelveDataRateResponse> fetchedRates) {
        if (fetchedRates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Exchange-rate fetch returned no supported currencies."
            );
        }

        LocalDate snapshotDate = fetchedRates.getFirst().date();
        boolean mixedDates = fetchedRates.stream()
                .map(TwelveDataRateResponse::date)
                .anyMatch(date -> !snapshotDate.equals(date));
        if (mixedDates) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Exchange-rate fetch returned inconsistent snapshot dates."
            );
        }
        return snapshotDate;
    }

    /**
     * Determines the snapshot date for reading exchange rates.
     *
     * @param date explicitly requested date or {@code null}
     * @return requested date or the latest available local snapshot
     */
    private LocalDate resolveSnapshotDate(LocalDate date) {
        if (date != null) {
            return date;
        }
        LocalDate latestDate = exchangeRateRepository.findLatestDate();
        if (latestDate == null) {
            throw new BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND, "Lokalna baza kurseva je prazna.");
        }
        return latestDate;
    }

    /**
     * Finds the exchange rate for a specific currency and date.
     *
     * @param currency currency to search for
     * @param date snapshot date
     * @return locally stored exchange rate entity
     */
    private ExchangeRateEntity findRate(SupportedCurrency currency, LocalDate date) {
        return exchangeRateRepository.findByCurrencyCodeAndDate(currency.name(), date)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EXCHANGE_RATE_NOT_FOUND,
                        "Kurs za valutu %s nije pronadjen za datum %s.".formatted(currency.name(), date)
                ));
    }

    /**
     * Maps a database entity to a public DTO response.
     *
     * @param entity locally stored exchange rate entity
     * @return DTO for the REST/API layer
     */
    private ExchangeRateDto toDto(ExchangeRateEntity entity) {
        return new ExchangeRateDto(
                entity.getCurrencyCode(),
                entity.getBuyingRate(),
                entity.getSellingRate(),
                entity.getDate(),
                entity.getCreatedAt()
        );
    }
}
