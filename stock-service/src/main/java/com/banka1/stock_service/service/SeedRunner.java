package com.banka1.stock_service.service;

import com.banka1.stock_service.config.ForexPairSeedProperties;
import com.banka1.stock_service.config.FuturesContractSeedProperties;
import com.banka1.stock_service.config.StockExchangeSeedProperties;
import com.banka1.stock_service.config.StockTickerSeedProperties;
import com.banka1.stock_service.dto.ForexPairImportResponse;
import com.banka1.stock_service.dto.FuturesContractImportResponse;
import com.banka1.stock_service.dto.StockExchangeImportResponse;
import com.banka1.stock_service.dto.StockTickerSeedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup runner that seeds reference data from configured sources.
 *
 * <p>The current startup flow can import stock exchanges, futures contracts,
 * starter stock tickers, and FX pairs independently, based on their dedicated
 * feature flags.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private final StockExchangeCsvImportService stockExchangeCsvImportService;
    private final FuturesContractCsvImportService futuresContractCsvImportService;
    private final StockTickerSeedService stockTickerSeedService;
    private final ForexPairSeedService forexPairSeedService;
    private final StockExchangeSeedProperties stockExchangeSeedProperties;
    private final FuturesContractSeedProperties futuresContractSeedProperties;
    private final StockTickerSeedProperties stockTickerSeedProperties;
    private final ForexPairSeedProperties forexPairSeedProperties;

    /**
     * Imports stock exchanges, starter stock tickers, futures contracts, and FX pairs
     * on startup when their seed features are enabled.
     *
     * @param args application startup arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        if (stockExchangeSeedProperties.enabled()) {
            StockExchangeImportResponse importResponse = stockExchangeCsvImportService.importFromConfiguredCsv();
            log.info(
                    "Stock exchanges imported from {}. processedRows={}, createdCount={}, updatedCount={}, unchangedCount={}",
                    importResponse.source(),
                    importResponse.processedRows(),
                    importResponse.createdCount(),
                    importResponse.updatedCount(),
                    importResponse.unchangedCount()
            );
        } else {
            log.info("Stock exchange CSV seeding is disabled.");
        }

        if (stockTickerSeedProperties.enabled()) {
            StockTickerSeedResponse seedResponse = stockTickerSeedService.seedDefaultTickers();
            log.info(
                    "Starter stock tickers seeded from {}. processedRows={}, createdCount={}, updatedCount={}, unchangedCount={}",
                    seedResponse.source(),
                    seedResponse.processedRows(),
                    seedResponse.createdCount(),
                    seedResponse.updatedCount(),
                    seedResponse.unchangedCount()
            );
        } else {
            log.info("Starter stock ticker seeding is disabled.");
        }

        if (futuresContractSeedProperties.enabled()) {
            FuturesContractImportResponse importResponse = futuresContractCsvImportService.importFromConfiguredCsv();
            log.info(
                    "Futures contracts imported from {}. processedRows={}, createdCount={}, updatedCount={}, unchangedCount={}",
                    importResponse.source(),
                    importResponse.processedRows(),
                    importResponse.createdCount(),
                    importResponse.updatedCount(),
                    importResponse.unchangedCount()
            );
        } else {
            log.info("Futures contract CSV seeding is disabled.");
        }

        if (forexPairSeedProperties.enabled()) {
            ForexPairImportResponse importResponse = forexPairSeedService.seedSupportedPairs();
            log.info(
                    "FX pairs seeded from {}. processedRows={}, createdCount={}, updatedCount={}, unchangedCount={}",
                    importResponse.source(),
                    importResponse.processedRows(),
                    importResponse.createdCount(),
                    importResponse.updatedCount(),
                    importResponse.unchangedCount()
            );
        } else {
            log.info("FX pair seeding is disabled.");
        }
    }
}
