package com.banka1.stock_service.service;

import com.banka1.stock_service.domain.ForexPair;
import com.banka1.stock_service.domain.Liquidity;
import com.banka1.stock_service.domain.Listing;
import com.banka1.stock_service.domain.ListingType;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.ForexPairImportResponse;
import com.banka1.stock_service.repository.ForexPairRepository;
import com.banka1.stock_service.repository.ListingRepository;
import com.banka1.stock_service.repository.StockExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds the built-in FX pair catalog required by FX listing queries and refresh flows.
 *
 * <p>The service guarantees that the full ordered-pair catalog exists locally without any
 * live provider dependency during startup. Market prices remain placeholder values until the
 * dedicated FX refresh flow updates them later.
 */
@Service
@RequiredArgsConstructor
public class ForexPairSeedService {

    private static final String SOURCE = "built-in supported forex pairs";
    private static final String DEFAULT_FOREX_EXCHANGE_MIC = "XNAS";
    private static final LocalDateTime SEEDED_LAST_REFRESH = LocalDateTime.of(2026, 4, 8, 12, 0);
    private static final BigDecimal ZERO_RATE = new BigDecimal("0.00000000");
    private static final long ZERO_VOLUME = 0L;

    private final ForexPairRepository forexPairRepository;
    private final ListingRepository listingRepository;
    private final StockExchangeRepository stockExchangeRepository;

    /**
     * Seeds all supported ordered FX pairs into the database.
     *
     * <p>Each supported ordered pair guarantees the presence of:
     *
     * <ul>
     *     <li>one {@link ForexPair} entity keyed by {@code BASE/QUOTE}</li>
     *     <li>one linked {@link Listing} entity with {@link ListingType#FOREX}</li>
     * </ul>
     *
     * <p>The seed is idempotent and only creates missing rows. Existing pair metadata and existing
     * market data are intentionally preserved so startup does not overwrite previously refreshed values.
     *
     * @return seed summary
     */
    @Transactional
    public ForexPairImportResponse seedSupportedPairs() {
        StockExchange forexExchange = resolveForexExchange(loadStockExchangesByMic());

        int processedRows = 0;
        int createdCount = 0;
        int unchangedCount = 0;

        for (String baseCurrency : ForexSupportedCurrencies.values()) {
            for (String quoteCurrency : ForexSupportedCurrencies.values()) {
                if (baseCurrency.equals(quoteCurrency)) {
                    continue;
                }

                processedRows++;

                boolean rowCreated = false;
                ForexPair forexPair = forexPairRepository.findByTicker(buildTicker(baseCurrency, quoteCurrency))
                        .orElse(null);

                if (forexPair == null) {
                    forexPair = forexPairRepository.saveAndFlush(createForexPair(baseCurrency, quoteCurrency));
                    rowCreated = true;
                }

                Listing listing = listingRepository.findByListingTypeAndSecurityId(ListingType.FOREX, forexPair.getId())
                        .orElse(null);
                if (listing == null) {
                    listingRepository.save(createListing(forexPair, forexExchange));
                    rowCreated = true;
                }

                if (rowCreated) {
                    createdCount++;
                } else {
                    unchangedCount++;
                }
            }
        }

        return new ForexPairImportResponse(
                SOURCE,
                processedRows,
                createdCount,
                0,
                unchangedCount
        );
    }

    private Map<String, StockExchange> loadStockExchangesByMic() {
        return stockExchangeRepository.findAllByOrderByExchangeNameAsc()
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, exchange) -> map.put(exchange.getExchangeMICCode(), exchange),
                        Map::putAll
                );
    }

    private StockExchange resolveForexExchange(Map<String, StockExchange> exchangesByMic) {
        if (exchangesByMic.isEmpty()) {
            throw new IllegalStateException(
                    "At least one stock exchange must exist before forex pair seeding runs."
            );
        }
        return exchangesByMic.getOrDefault(DEFAULT_FOREX_EXCHANGE_MIC, exchangesByMic.values().iterator().next());
    }

    private ForexPair createForexPair(String baseCurrency, String quoteCurrency) {
        ForexPair forexPair = new ForexPair();
        forexPair.setTicker(buildTicker(baseCurrency, quoteCurrency));
        forexPair.setBaseCurrency(baseCurrency);
        forexPair.setQuoteCurrency(quoteCurrency);
        forexPair.setExchangeRate(ZERO_RATE);
        forexPair.setLiquidity(Liquidity.HIGH);
        return forexPair;
    }

    private Listing createListing(ForexPair forexPair, StockExchange stockExchange) {
        Listing listing = new Listing();
        listing.setSecurityId(forexPair.getId());
        listing.setListingType(ListingType.FOREX);
        listing.setStockExchange(stockExchange);
        listing.setTicker(forexPair.getTicker());
        listing.setName(buildListingName(forexPair.getBaseCurrency(), forexPair.getQuoteCurrency()));
        listing.setLastRefresh(SEEDED_LAST_REFRESH);
        listing.setPrice(forexPair.getExchangeRate());
        listing.setAsk(forexPair.getExchangeRate());
        listing.setBid(forexPair.getExchangeRate());
        listing.setChange(ZERO_RATE);
        listing.setVolume(resolveSeedVolume(forexPair));
        return listing;
    }

    private long resolveSeedVolume(ForexPair forexPair) {
        return forexPair.getExchangeRate().signum() == 0 ? ZERO_VOLUME : forexPair.getContractSize();
    }

    private String buildTicker(String baseCurrency, String quoteCurrency) {
        return baseCurrency + "/" + quoteCurrency;
    }

    private String buildListingName(String baseCurrency, String quoteCurrency) {
        return baseCurrency + " / " + quoteCurrency;
    }
}
