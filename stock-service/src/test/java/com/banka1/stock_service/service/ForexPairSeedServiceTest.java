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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ForexPairSeedService}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ForexPairSeedServiceTest {

    @Autowired
    private ForexPairSeedService forexPairSeedService;

    @Autowired
    private ForexPairRepository forexPairRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private StockExchangeRepository stockExchangeRepository;

    @Test
    void seedSupportedPairsCreatesFullCatalogAndListings() {
        saveExchange("Nasdaq", "NASDAQ", "XNAS");

        ForexPairImportResponse response = forexPairSeedService.seedSupportedPairs();

        ForexPair eurUsd = forexPairRepository.findByTicker("EUR/USD").orElseThrow();
        Listing eurUsdListing = listingRepository.findByListingTypeAndSecurityId(ListingType.FOREX, eurUsd.getId())
                .orElseThrow();

        assertThat(response.source()).isEqualTo("built-in supported forex pairs");
        assertThat(response.processedRows()).isEqualTo(56);
        assertThat(response.createdCount()).isEqualTo(56);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isZero();
        assertThat(forexPairRepository.count()).isEqualTo(56);
        assertThat(listingRepository.count()).isEqualTo(56);

        assertThat(eurUsd.getBaseCurrency()).isEqualTo("EUR");
        assertThat(eurUsd.getQuoteCurrency()).isEqualTo("USD");
        assertThat(eurUsd.getExchangeRate()).isEqualByComparingTo("0.00000000");
        assertThat(eurUsd.getLiquidity()).isEqualTo(Liquidity.HIGH);

        assertThat(eurUsdListing.getListingType()).isEqualTo(ListingType.FOREX);
        assertThat(eurUsdListing.getStockExchange().getExchangeMICCode()).isEqualTo("XNAS");
        assertThat(eurUsdListing.getTicker()).isEqualTo("EUR/USD");
        assertThat(eurUsdListing.getName()).isEqualTo("EUR / USD");
        assertThat(eurUsdListing.getLastRefresh()).isEqualTo(LocalDateTime.of(2026, 4, 8, 12, 0));
        assertThat(eurUsdListing.getPrice()).isEqualByComparingTo("0.00000000");
        assertThat(eurUsdListing.getAsk()).isEqualByComparingTo("0.00000000");
        assertThat(eurUsdListing.getBid()).isEqualByComparingTo("0.00000000");
        assertThat(eurUsdListing.getChange()).isEqualByComparingTo("0.00000000");
        assertThat(eurUsdListing.getVolume()).isZero();
    }

    @Test
    void seedSupportedPairsIsIdempotentOnRepeatedRuns() {
        saveExchange("Nasdaq", "NASDAQ", "XNAS");

        forexPairSeedService.seedSupportedPairs();
        ForexPairImportResponse secondRun = forexPairSeedService.seedSupportedPairs();

        assertThat(secondRun.processedRows()).isEqualTo(56);
        assertThat(secondRun.createdCount()).isZero();
        assertThat(secondRun.updatedCount()).isZero();
        assertThat(secondRun.unchangedCount()).isEqualTo(56);
        assertThat(forexPairRepository.count()).isEqualTo(56);
        assertThat(listingRepository.count()).isEqualTo(56);
    }

    @Test
    void seedSupportedPairsCreatesMissingListingWithoutOverwritingExistingPair() {
        saveExchange("Nasdaq", "NASDAQ", "XNAS");

        ForexPair existingPair = new ForexPair();
        existingPair.setTicker("EUR/USD");
        existingPair.setBaseCurrency("EUR");
        existingPair.setQuoteCurrency("USD");
        existingPair.setExchangeRate(new BigDecimal("1.08350000"));
        existingPair.setLiquidity(Liquidity.MEDIUM);
        forexPairRepository.saveAndFlush(existingPair);

        ForexPairImportResponse response = forexPairSeedService.seedSupportedPairs();

        ForexPair persistedPair = forexPairRepository.findByTicker("EUR/USD").orElseThrow();
        Listing eurUsdListing = listingRepository.findByListingTypeAndSecurityId(ListingType.FOREX, persistedPair.getId())
                .orElseThrow();

        assertThat(response.processedRows()).isEqualTo(56);
        assertThat(response.createdCount()).isEqualTo(56);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isZero();

        assertThat(persistedPair.getExchangeRate()).isEqualByComparingTo("1.08350000");
        assertThat(persistedPair.getLiquidity()).isEqualTo(Liquidity.MEDIUM);
        assertThat(eurUsdListing.getTicker()).isEqualTo("EUR/USD");
        assertThat(eurUsdListing.getName()).isEqualTo("EUR / USD");
        assertThat(eurUsdListing.getPrice()).isEqualByComparingTo("1.08350000");
        assertThat(eurUsdListing.getAsk()).isEqualByComparingTo("1.08350000");
        assertThat(eurUsdListing.getBid()).isEqualByComparingTo("1.08350000");
        assertThat(eurUsdListing.getVolume()).isEqualTo(1_000L);
        assertThat(eurUsdListing.getStockExchange().getExchangeMICCode()).isEqualTo("XNAS");
    }

    private void saveExchange(String exchangeName, String acronym, String micCode) {
        StockExchange exchange = new StockExchange();
        exchange.setExchangeName(exchangeName);
        exchange.setExchangeAcronym(acronym);
        exchange.setExchangeMICCode(micCode);
        exchange.setPolity("United States");
        exchange.setCurrency("USD");
        exchange.setTimeZone("America/New_York");
        exchange.setOpenTime(LocalTime.of(9, 30));
        exchange.setCloseTime(LocalTime.of(16, 0));
        exchange.setIsActive(true);
        stockExchangeRepository.saveAndFlush(exchange);
    }
}
