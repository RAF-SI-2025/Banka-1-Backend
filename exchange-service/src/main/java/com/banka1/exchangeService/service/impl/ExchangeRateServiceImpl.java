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
 * Implementacija servisa za rad sa dnevnim kursevima i klijentskim konverzijama.
 */
@Slf4j
@Service
public class ExchangeRateServiceImpl implements ExchangeRateService {

    /**
     * Broj decimala koji se koristi pri deljenju za konverzije.
     */
    private static final int CALCULATION_SCALE = 8;
    /**
     * Broj decimala za prikaz provizije.
     */
    private static final int COMMISSION_SCALE = 2;
    /**
     * Broj decimala koji se koristi pri cuvanju kursa.
     */
    private static final int RATE_SCALE = 8;
    /**
     * Konstanta 100 za pretvaranje procenta marze u decimalni faktor.
     */
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Klijent za spoljasnji fetch deviznih kurseva.
     */
    private final TwelveDataClient twelveDataClient;
    /**
     * Konfiguracija pravila za fetch i marzu.
     */
    private final ExchangeRateProperties exchangeRateProperties;

    /**
     * Repozitorijum za lokalno skladistenje i citanje snapshot-a.
     */
    private final ExchangeRateRepository exchangeRateRepository;
    /**
     * Transakciona komponenta za atomsku zamenu celog snapshot-a.
     */
    private final ExchangeRateSnapshotPersistenceService snapshotPersistenceService;

    /**
     * Sat koji se koristi za odredjivanje ciljnog fallback datuma.
     */
    private final Clock clock;

    /**
     * Kreira servis sa podrazumevanim UTC satom za produkcionu upotrebu.
     *
     * @param twelveDataClient       klijent za eksterni fetch kurseva
     * @param exchangeRateProperties konfiguracija marze i fetch pravila
     * @param exchangeRateRepository repozitorijum lokalnih kurseva
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
     * Kreira servis sa eksplicitnim satom, korisno za testove i deterministicki fallback datum.
     *
     * @param twelveDataClient       klijent za eksterni fetch kurseva
     * @param exchangeRateProperties konfiguracija marze i fetch pravila
     * @param exchangeRateRepository repozitorijum lokalnih kurseva
     * @param clock                  izvor vremena za fallback logiku
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
        BigDecimal commission = request.amount()
                .multiply(resolveCommissionFactor())
                .setScale(COMMISSION_SCALE, RoundingMode.HALF_UP);

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
     * Dohvata kurs za jednu podrzanu valutu prema baznoj RSD valuti.
     *
     * @param currencyCode troslovni ISO kod izvora
     * @return parsiran odgovor Twelve Data providera
     */
    private TwelveDataRateResponse fetchRate(String currencyCode) {
        return twelveDataClient.fetchExchangeRate(
                currencyCode,
                SupportedCurrency.RSD.name()
        );
    }

    /**
     * Pretvara provider odgovor u potpuno pripremljen red lokalnog snapshot-a.
     *
     * @param response parsiran odgovor spoljnog providera
     * @return pripremljen red za atomsko persistiranje
     */
    private ExchangeRateSnapshotPersistenceService.PreparedExchangeRate toPreparedRate(TwelveDataRateResponse response) {
        return new ExchangeRateSnapshotPersistenceService.PreparedExchangeRate(
                response.fromCurrency(),
                calculateBuyingRate(response.rate()),
                calculateSellingRate(response.rate())
        );
    }

    /**
     * Racuna kupovni kurs banke iz market kursa i konfigurisanog procenta marze.
     * Formula je {@code buyingRate = marketRate * (1 - margin/100)}.
     * Na primer, za market kurs {@code 117.40} i marzu {@code 1.0},
     * banka kupuje po {@code 117.40 * 0.99 = 116.22600000}.
     *
     * @param marketRate market kurs dobijen od providera
     * @return bankin kupovni kurs za cuvanje u snapshot-u
     */
    private BigDecimal calculateBuyingRate(BigDecimal marketRate) {
        BigDecimal marginFactor = resolveMarginFactor();
        return marketRate.multiply(BigDecimal.ONE.subtract(marginFactor))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Racuna prodajni kurs banke iz market kursa i konfigurisanog procenta marze.
     * Formula je {@code sellingRate = marketRate * (1 + margin/100)}.
     * Na primer, za market kurs {@code 117.40} i marzu {@code 1.0},
     * banka prodaje po {@code 117.40 * 1.01 = 118.57400000}.
     *
     * @param marketRate market kurs dobijen od providera
     * @return bankin prodajni kurs za cuvanje u snapshot-u
     */
    private BigDecimal calculateSellingRate(BigDecimal marketRate) {
        BigDecimal marginFactor = resolveMarginFactor();
        return marketRate.multiply(BigDecimal.ONE.add(marginFactor))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Pretvara procentualnu marzu iz konfiguracije u decimalni faktor.
     * Na primer, {@code 1.0} postaje {@code 0.01}.
     *
     * @return decimalni faktor marze pogodan za matematicke proracune
     */
    private BigDecimal resolveMarginFactor() {
        return exchangeRateProperties.marginPercentage()
                .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Pretvara procentualnu proviziju u decimalni faktor za obracun.
     *
     * @return decimalni faktor provizije
     */
    private BigDecimal resolveCommissionFactor() {
        return exchangeRateProperties.commissionPercentage()
                .divide(ONE_HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Aktivira fallback na poslednji lokalni snapshot kada eksterni fetch ne uspe.
     *
     * @param rootCause originalna greska fetch operacije
     * @return rezultat sa fallback snapshot-om za ciljni datum
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
     * Pretvara lokalno sacuvan kurs u pripremljen red za novi datum snapshot-a.
     *
     * @param previousRate prethodno sacuvan kurs
     * @return pripremljen red za atomsko persistiranje fallback snapshot-a
     */
    private ExchangeRateSnapshotPersistenceService.PreparedExchangeRate toPreparedRate(ExchangeRateEntity previousRate) {
        return new ExchangeRateSnapshotPersistenceService.PreparedExchangeRate(
                previousRate.getCurrencyCode(),
                previousRate.getBuyingRate(),
                previousRate.getSellingRate()
        );
    }

    /**
     * Zahteva da svi fetched provider odgovori pripadaju istom datumskom snapshot-u.
     *
     * @param fetchedRates potpuni skup fetched kurseva
     * @return jedinstveni datum snapshot-a koji ce biti lokalno sacuvan
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
     * Odredjuje datum snapshot-a za citanje kurseva.
     *
     * @param date eksplicitno trazeni datum ili {@code null}
     * @return trazeni datum ili poslednji raspolozivi lokalni snapshot
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
     * Pronalazi kurs za konkretnu valutu i datum.
     *
     * @param currency valuta koja se trazi
     * @param date     datum snapshot-a
     * @return lokalno sacuvan entitet kursa
     */
    private ExchangeRateEntity findRate(SupportedCurrency currency, LocalDate date) {
        return exchangeRateRepository.findByCurrencyCodeAndDate(currency.name(), date)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.EXCHANGE_RATE_NOT_FOUND,
                        "Kurs za valutu %s nije pronadjen za datum %s.".formatted(currency.name(), date)
                ));
    }

    /**
     * Mapira entitet baze u javni DTO odgovor.
     *
     * @param entity lokalno sacuvan entitet kursa
     * @return DTO za REST/API sloj
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
