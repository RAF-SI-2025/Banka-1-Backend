package com.banka1.stock_service.service;

import com.banka1.stock_service.client.AlphaVantageClient;
import com.banka1.stock_service.domain.ForexPair;
import com.banka1.stock_service.domain.Liquidity;
import com.banka1.stock_service.domain.Listing;
import com.banka1.stock_service.domain.ListingType;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.AlphaVantageForexExchangeRateResponse;
import com.banka1.stock_service.dto.ForexPairImportResponse;
import com.banka1.stock_service.repository.ForexPairRepository;
import com.banka1.stock_service.repository.ListingRepository;
import com.banka1.stock_service.repository.StockExchangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Imports supported FX pairs from the external market-data API and upserts them into the database.
 *
 * <p>The importer generates the full ordered pair set for the supported currencies,
 * requests each pair independently from the provider, and skips pairs that are not available.
 * One provider response creates or updates:
 *
 * <ul>
 *     <li>one {@link ForexPair} entity</li>
 *     <li>one linked {@link Listing} snapshot with {@code listingType=FOREX}</li>
 * </ul>
 *
 * <p>The import is idempotent and keyed by the FX ticker in {@code BASE/QUOTE} format.
 * If one pair fails to load from the provider, that pair is skipped without aborting the whole import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForexPairApiImportService {

    /**
     * Stable source label used in startup logs and import responses.
     */
    private static final String SOURCE = "alpha-vantage:supported-forex-pairs";

    private static final String PREFERRED_FOREX_EXCHANGE_MIC = "XNAS";
    private static final BigDecimal ZERO_CHANGE = new BigDecimal("0.00000000");

    private final AlphaVantageClient alphaVantageClient;
    private final ForexPairRepository forexPairRepository;
    private final ListingRepository listingRepository;
    private final StockExchangeRepository stockExchangeRepository;

    /**
     * Imports all supported ordered FX pairs from the external provider.
     *
     * <p>The supported-currency set produces {@code 8 x 7 = 56} ordered pairs.
     * Pairs that are unavailable from the provider are logged and skipped.
     *
     * @return import summary covering only successfully fetched pairs
     */
    @Transactional
    public ForexPairImportResponse importSupportedPairs() {
        Map<String, StockExchange> exchangesByMic = loadStockExchangesByMic();
        StockExchange forexExchange = resolveForexExchange(exchangesByMic);

        int processedRows = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (String baseCurrency : ForexSupportedCurrencies.values()) {
            for (String quoteCurrency : ForexSupportedCurrencies.values()) {
                if (baseCurrency.equals(quoteCurrency)) {
                    continue;
                }

                AlphaVantageForexExchangeRateResponse exchangeRateResponse = fetchPairOrNull(baseCurrency, quoteCurrency);
                if (exchangeRateResponse == null) {
                    continue;
                }

                processedRows++;

                ImportOutcome outcome = persistPair(exchangeRateResponse, forexExchange);
                if (outcome == ImportOutcome.CREATED) {
                    createdCount++;
                } else if (outcome == ImportOutcome.UPDATED) {
                    updatedCount++;
                } else {
                    unchangedCount++;
                }
            }
        }

        return new ForexPairImportResponse(
                SOURCE,
                processedRows,
                createdCount,
                updatedCount,
                unchangedCount
        );
    }

    /**
     * Requests one ordered FX pair from the provider and converts unavailable pairs into skips.
     *
     * @param baseCurrency base currency code
     * @param quoteCurrency quote currency code
     * @return provider response, or {@code null} when the pair should be skipped
     */
    private AlphaVantageForexExchangeRateResponse fetchPairOrNull(String baseCurrency, String quoteCurrency) {
        try {
            return alphaVantageClient.fetchExchangeRate(baseCurrency, quoteCurrency);
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Skipping FX pair {}/{} because the provider request failed: {}",
                    baseCurrency,
                    quoteCurrency,
                    exception.getReason()
            );
            return null;
        }
    }

    /**
     * Persists one successfully fetched FX pair together with its listing snapshot.
     *
     * @param exchangeRateResponse normalized provider response
     * @param forexExchange exchange assigned to the generated listing
     * @return row import outcome
     */
    private ImportOutcome persistPair(
            AlphaVantageForexExchangeRateResponse exchangeRateResponse,
            StockExchange forexExchange
    ) {
        boolean created = false;
        boolean updated = false;

        String ticker = buildTicker(exchangeRateResponse.baseCurrency(), exchangeRateResponse.quoteCurrency());
        ForexPairRow row = new ForexPairRow(
                ticker,
                exchangeRateResponse.baseCurrency(),
                exchangeRateResponse.quoteCurrency(),
                exchangeRateResponse.exchangeRate(),
                Liquidity.HIGH,
                buildListingName(exchangeRateResponse.baseCurrency(), exchangeRateResponse.quoteCurrency()),
                exchangeRateResponse.lastRefreshed(),
                exchangeRateResponse.exchangeRate(),
                exchangeRateResponse.exchangeRate(),
                exchangeRateResponse.exchangeRate(),
                ZERO_CHANGE,
                (long) ForexPair.CONTRACT_SIZE
        );

        ForexPair forexPair = forexPairRepository.findByTicker(row.ticker()).orElse(null);
        if (forexPair == null) {
            forexPair = new ForexPair();
            applyForexPairRow(forexPair, row);
            forexPair = forexPairRepository.saveAndFlush(forexPair);
            created = true;
        } else if (applyForexPairRowIfChanged(forexPair, row)) {
            forexPairRepository.save(forexPair);
            updated = true;
        }

        Listing listing = listingRepository.findByListingTypeAndSecurityId(ListingType.FOREX, forexPair.getId())
                .orElse(null);
        if (listing == null) {
            listing = new Listing();
            applyListingRow(listing, forexPair, forexExchange, row);
            listingRepository.save(listing);
            created = true;
        } else if (applyListingRowIfChanged(listing, forexPair, forexExchange, row)) {
            listingRepository.save(listing);
            updated = true;
        }

        if (created) {
            return ImportOutcome.CREATED;
        }
        if (updated) {
            return ImportOutcome.UPDATED;
        }
        return ImportOutcome.UNCHANGED;
    }

    /**
     * Loads all persisted exchanges indexed by MIC code.
     *
     * @return MIC-indexed stock exchanges
     */
    private Map<String, StockExchange> loadStockExchangesByMic() {
        return stockExchangeRepository.findAllByOrderByExchangeNameAsc()
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, exchange) -> map.put(exchange.getExchangeMICCode(), exchange),
                        Map::putAll
                );
    }

    /**
     * Resolves the exchange assigned to generated FX listings.
     *
     * <p>The current model stores every listing on one exchange, so the importer prefers
     * {@code XNAS} when present and otherwise falls back to the first available exchange.
     * This is compatibility metadata required by the current schema, not a signal that should
     * drive FX market-open checks or scheduler refresh decisions.
     *
     * @param exchangesByMic MIC-indexed stock exchanges
     * @return resolved exchange for FX listings
     */
    private StockExchange resolveForexExchange(Map<String, StockExchange> exchangesByMic) {
        if (exchangesByMic.isEmpty()) {
            throw new IllegalStateException(
                    "At least one stock exchange must exist before FX pairs can be imported from the API."
            );
        }
        return exchangesByMic.getOrDefault(PREFERRED_FOREX_EXCHANGE_MIC, exchangesByMic.values().iterator().next());
    }

    /**
     * Formats the stable ticker for one ordered pair.
     *
     * @param baseCurrency base currency code
     * @param quoteCurrency quote currency code
     * @return ticker in {@code BASE/QUOTE} format
     */
    private String buildTicker(String baseCurrency, String quoteCurrency) {
        return baseCurrency + "/" + quoteCurrency;
    }

    /**
     * Formats a display name for the generated listing.
     *
     * @param baseCurrency base currency code
     * @param quoteCurrency quote currency code
     * @return human-readable pair name
     */
    private String buildListingName(String baseCurrency, String quoteCurrency) {
        return baseCurrency + " / " + quoteCurrency;
    }

    /**
     * Copies imported FX data into a persistent {@link ForexPair} entity.
     *
     * @param entity target FX pair
     * @param row imported row data
     */
    private void applyForexPairRow(ForexPair entity, ForexPairRow row) {
        entity.setTicker(row.ticker());
        entity.setBaseCurrency(row.baseCurrency());
        entity.setQuoteCurrency(row.quoteCurrency());
        entity.setExchangeRate(row.exchangeRate());
        entity.setLiquidity(row.liquidity());
    }

    /**
     * Updates an existing FX pair only when at least one imported value changed.
     *
     * @param entity existing FX pair
     * @param row imported row data
     * @return {@code true} if the entity changed
     */
    private boolean applyForexPairRowIfChanged(ForexPair entity, ForexPairRow row) {
        if (forexPairMatches(entity, row)) {
            return false;
        }

        applyForexPairRow(entity, row);
        return true;
    }

    /**
     * Checks whether the stored FX pair already matches the imported state.
     *
     * @param entity existing FX pair
     * @param row imported row data
     * @return {@code true} when all imported fields already match
     */
    private boolean forexPairMatches(ForexPair entity, ForexPairRow row) {
        return Objects.equals(entity.getTicker(), row.ticker())
                && Objects.equals(entity.getBaseCurrency(), row.baseCurrency())
                && Objects.equals(entity.getQuoteCurrency(), row.quoteCurrency())
                && Objects.equals(entity.getExchangeRate(), row.exchangeRate())
                && Objects.equals(entity.getLiquidity(), row.liquidity());
    }

    /**
     * Copies imported FX market data into a persistent {@link Listing} entity.
     *
     * @param entity target listing
     * @param forexPair linked FX pair
     * @param stockExchange exchange assigned to the listing
     * @param row imported row data
     */
    private void applyListingRow(
            Listing entity,
            ForexPair forexPair,
            StockExchange stockExchange,
            ForexPairRow row
    ) {
        entity.setSecurityId(forexPair.getId());
        entity.setListingType(ListingType.FOREX);
        entity.setStockExchange(stockExchange);
        entity.setTicker(row.ticker());
        entity.setName(row.name());
        entity.setLastRefresh(row.lastRefresh());
        entity.setPrice(row.price());
        entity.setAsk(row.ask());
        entity.setBid(row.bid());
        entity.setChange(row.change());
        entity.setVolume(row.volume());
    }

    /**
     * Updates an existing FX listing only when at least one imported value changed.
     *
     * @param entity existing listing
     * @param forexPair linked FX pair
     * @param stockExchange exchange assigned to the listing
     * @param row imported row data
     * @return {@code true} if the listing changed
     */
    private boolean applyListingRowIfChanged(
            Listing entity,
            ForexPair forexPair,
            StockExchange stockExchange,
            ForexPairRow row
    ) {
        if (listingMatches(entity, forexPair, stockExchange, row)) {
            return false;
        }

        applyListingRow(entity, forexPair, stockExchange, row);
        return true;
    }

    /**
     * Checks whether the stored FX listing already matches the imported state.
     *
     * @param entity existing listing
     * @param forexPair linked FX pair
     * @param stockExchange exchange assigned to the listing
     * @param row imported row data
     * @return {@code true} when all imported listing fields already match
     */
    private boolean listingMatches(
            Listing entity,
            ForexPair forexPair,
            StockExchange stockExchange,
            ForexPairRow row
    ) {
        return Objects.equals(entity.getSecurityId(), forexPair.getId())
                && entity.getListingType() == ListingType.FOREX
                && entity.getStockExchange() != null
                && Objects.equals(entity.getStockExchange().getId(), stockExchange.getId())
                && Objects.equals(entity.getTicker(), row.ticker())
                && Objects.equals(entity.getName(), row.name())
                && Objects.equals(entity.getLastRefresh(), row.lastRefresh())
                && Objects.equals(entity.getPrice(), row.price())
                && Objects.equals(entity.getAsk(), row.ask())
                && Objects.equals(entity.getBid(), row.bid())
                && Objects.equals(entity.getChange(), row.change())
                && Objects.equals(entity.getVolume(), row.volume());
    }

    /**
     * Intermediate immutable representation of one imported FX pair row.
     *
     * @param ticker FX ticker in {@code BASE/QUOTE} format
     * @param baseCurrency base currency code
     * @param quoteCurrency quote currency code
     * @param exchangeRate latest exchange rate
     * @param liquidity liquidity classification
     * @param name listing display name
     * @param lastRefresh provider refresh timestamp
     * @param price current listing price
     * @param ask current ask price
     * @param bid current bid price
     * @param change absolute change
     * @param volume current listing volume
     */
    private record ForexPairRow(
            String ticker,
            String baseCurrency,
            String quoteCurrency,
            BigDecimal exchangeRate,
            Liquidity liquidity,
            String name,
            LocalDateTime lastRefresh,
            BigDecimal price,
            BigDecimal ask,
            BigDecimal bid,
            BigDecimal change,
            Long volume
    ) {
    }

    /**
     * Outcome classification for one successfully imported pair.
     */
    private enum ImportOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
