package com.banka1.stock_service.service.implementation;

import com.banka1.stock_service.client.AlphaVantageClient;
import com.banka1.stock_service.domain.ForexPair;
import com.banka1.stock_service.domain.Listing;
import com.banka1.stock_service.domain.ListingDailyPriceInfo;
import com.banka1.stock_service.domain.ListingType;
import com.banka1.stock_service.domain.Liquidity;
import com.banka1.stock_service.domain.Stock;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.AlphaVantageForexExchangeRateResponse;
import com.banka1.stock_service.dto.AlphaVantageQuoteResponse;
import com.banka1.stock_service.dto.ListingRefreshResponse;
import com.banka1.stock_service.repository.ForexPairRepository;
import com.banka1.stock_service.repository.ListingDailyPriceInfoRepository;
import com.banka1.stock_service.repository.ListingRepository;
import com.banka1.stock_service.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ListingMarketDataRefreshServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ListingMarketDataRefreshServiceImplTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingDailyPriceInfoRepository listingDailyPriceInfoRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ForexPairRepository forexPairRepository;

    @Mock
    private AlphaVantageClient alphaVantageClient;

    @Test
    void refreshListingUpdatesStockSnapshotAndCreatesDailyEntry() {
        Listing listing = createListing(10L, 1L, ListingType.STOCK, "AAPL", new BigDecimal("200.00000000"));
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setTicker("AAPL");
        stock.setName("Apple Inc.");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        when(alphaVantageClient.fetchQuote("AAPL")).thenReturn(new AlphaVantageQuoteResponse(
                "AAPL",
                new BigDecimal("212.40000000"),
                new BigDecimal("212.45000000"),
                new BigDecimal("212.35000000"),
                new BigDecimal("4.60000000"),
                25_000L,
                LocalDate.of(2026, 4, 8)
        ));
        when(listingDailyPriceInfoRepository.findByListingIdAndDate(10L, LocalDate.of(2026, 4, 8)))
                .thenReturn(Optional.empty());

        ListingRefreshResponse response = serviceAt("2026-04-08T10:15:30Z").refreshListing(10L);

        assertThat(response.listingId()).isEqualTo(10L);
        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.listingType()).isEqualTo(ListingType.STOCK);
        assertThat(response.dailySnapshotDate()).isEqualTo(LocalDate.of(2026, 4, 8));
        assertThat(response.lastRefresh()).isEqualTo(LocalDateTime.of(2026, 4, 8, 10, 15, 30));

        assertThat(listing.getPrice()).isEqualByComparingTo("212.40000000");
        assertThat(listing.getAsk()).isEqualByComparingTo("212.45000000");
        assertThat(listing.getBid()).isEqualByComparingTo("212.35000000");
        assertThat(listing.getChange()).isEqualByComparingTo("4.60000000");
        assertThat(listing.getVolume()).isEqualTo(25_000L);
        assertThat(listing.getLastRefresh()).isEqualTo(LocalDateTime.of(2026, 4, 8, 10, 15, 30));

        ArgumentCaptor<ListingDailyPriceInfo> dailyCaptor = ArgumentCaptor.forClass(ListingDailyPriceInfo.class);
        verify(listingDailyPriceInfoRepository).save(dailyCaptor.capture());

        ListingDailyPriceInfo persistedDaily = dailyCaptor.getValue();
        assertThat(persistedDaily.getListing()).isSameAs(listing);
        assertThat(persistedDaily.getDate()).isEqualTo(LocalDate.of(2026, 4, 8));
        assertThat(persistedDaily.getPrice()).isEqualByComparingTo("212.40000000");
        assertThat(persistedDaily.getAsk()).isEqualByComparingTo("212.45000000");
        assertThat(persistedDaily.getBid()).isEqualByComparingTo("212.35000000");
        assertThat(persistedDaily.getChange()).isEqualByComparingTo("4.60000000");
        assertThat(persistedDaily.getVolume()).isEqualTo(25_000L);

        verify(listingRepository).save(listing);
        verify(forexPairRepository, never()).save(any());
    }

    @Test
    void refreshListingUpdatesForexSnapshotAndExistingDailyEntry() {
        Listing listing = createListing(11L, 5L, ListingType.FOREX, "EUR/USD", new BigDecimal("1.08000000"));
        listing.setVolume(10L);

        ForexPair forexPair = new ForexPair();
        forexPair.setId(5L);
        forexPair.setTicker("EUR/USD");
        forexPair.setBaseCurrency("EUR");
        forexPair.setQuoteCurrency("USD");
        forexPair.setExchangeRate(new BigDecimal("1.08000000"));
        forexPair.setLiquidity(Liquidity.HIGH);

        ListingDailyPriceInfo existingDaily = new ListingDailyPriceInfo();
        existingDaily.setId(77L);
        existingDaily.setListing(listing);
        existingDaily.setDate(LocalDate.of(2026, 4, 8));
        existingDaily.setPrice(new BigDecimal("1.08000000"));
        existingDaily.setAsk(new BigDecimal("1.08000000"));
        existingDaily.setBid(new BigDecimal("1.08000000"));
        existingDaily.setChange(BigDecimal.ZERO);
        existingDaily.setVolume(10L);

        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing));
        when(forexPairRepository.findById(5L)).thenReturn(Optional.of(forexPair));
        when(alphaVantageClient.fetchExchangeRate("EUR", "USD")).thenReturn(new AlphaVantageForexExchangeRateResponse(
                "EUR",
                "USD",
                new BigDecimal("1.09000000"),
                LocalDateTime.of(2026, 4, 8, 16, 0)
        ));
        when(listingDailyPriceInfoRepository.findByListingIdAndDate(11L, LocalDate.of(2026, 4, 8)))
                .thenReturn(Optional.of(existingDaily));

        ListingRefreshResponse response = serviceAt("2026-04-08T16:05:00Z").refreshListing(11L);

        assertThat(response.listingId()).isEqualTo(11L);
        assertThat(response.dailySnapshotDate()).isEqualTo(LocalDate.of(2026, 4, 8));
        assertThat(response.lastRefresh()).isEqualTo(LocalDateTime.of(2026, 4, 8, 16, 5));

        assertThat(forexPair.getExchangeRate()).isEqualByComparingTo("1.09000000");
        assertThat(listing.getPrice()).isEqualByComparingTo("1.09000000");
        assertThat(listing.getAsk()).isEqualByComparingTo("1.09000000");
        assertThat(listing.getBid()).isEqualByComparingTo("1.09000000");
        assertThat(listing.getChange()).isEqualByComparingTo("0.01000000");
        assertThat(listing.getVolume()).isEqualTo(1_000L);
        assertThat(existingDaily.getPrice()).isEqualByComparingTo("1.09000000");
        assertThat(existingDaily.getChange()).isEqualByComparingTo("0.01000000");
        assertThat(existingDaily.getVolume()).isEqualTo(1_000L);

        verify(forexPairRepository).save(forexPair);
        verify(listingDailyPriceInfoRepository).save(existingDaily);
        verify(listingRepository).save(listing);
    }

    @Test
    void refreshListingLeavesPlaceholderForexValuesUntouchedWhenProviderPairIsUnavailable() {
        Listing listing = createListing(11L, 5L, ListingType.FOREX, "RSD/JPY", new BigDecimal("0.00000000"));
        listing.setVolume(0L);

        ForexPair forexPair = new ForexPair();
        forexPair.setId(5L);
        forexPair.setTicker("RSD/JPY");
        forexPair.setBaseCurrency("RSD");
        forexPair.setQuoteCurrency("JPY");
        forexPair.setExchangeRate(new BigDecimal("0.00000000"));
        forexPair.setLiquidity(Liquidity.HIGH);

        when(listingRepository.findById(11L)).thenReturn(Optional.of(listing));
        when(forexPairRepository.findById(5L)).thenReturn(Optional.of(forexPair));
        when(alphaVantageClient.fetchExchangeRate("RSD", "JPY"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Pair not available"));

        assertThatThrownBy(() -> serviceAt("2026-04-08T16:05:00Z").refreshListing(11L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(forexPair.getExchangeRate()).isEqualByComparingTo("0.00000000");
        assertThat(listing.getPrice()).isEqualByComparingTo("0.00000000");
        assertThat(listing.getAsk()).isEqualByComparingTo("0.00000000");
        assertThat(listing.getBid()).isEqualByComparingTo("0.00000000");
        assertThat(listing.getChange()).isEqualByComparingTo("0");
        assertThat(listing.getVolume()).isZero();
        assertThat(listing.getLastRefresh()).isEqualTo(LocalDateTime.of(2026, 4, 7, 9, 0));

        verify(forexPairRepository, never()).save(any());
        verify(listingDailyPriceInfoRepository, never()).save(any());
        verify(listingRepository, never()).save(listing);
    }

    @Test
    void refreshListingRejectsUnsupportedFuturesListing() {
        Listing listing = createListing(12L, 8L, ListingType.FUTURES, "CRUDEOILENERGY", new BigDecimal("1.00000000"));

        when(listingRepository.findById(12L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> serviceAt("2026-04-08T10:15:30Z").refreshListing(12L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(alphaVantageClient, never()).fetchQuote(any());
        verify(alphaVantageClient, never()).fetchExchangeRate(any(), any());
    }

    private ListingMarketDataRefreshServiceImpl serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ListingMarketDataRefreshServiceImpl(
                listingRepository,
                listingDailyPriceInfoRepository,
                stockRepository,
                forexPairRepository,
                alphaVantageClient,
                clock
        );
    }

    private Listing createListing(
            Long listingId,
            Long securityId,
            ListingType listingType,
            String ticker,
            BigDecimal price
    ) {
        StockExchange exchange = new StockExchange();
        exchange.setId(100L);
        exchange.setExchangeName("Nasdaq");
        exchange.setExchangeAcronym("NASDAQ");
        exchange.setExchangeMICCode("XNAS");
        exchange.setPolity("United States");
        exchange.setCurrency("USD");
        exchange.setTimeZone("America/New_York");
        exchange.setOpenTime(LocalTime.of(9, 30));
        exchange.setCloseTime(LocalTime.of(16, 0));
        exchange.setIsActive(true);

        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setSecurityId(securityId);
        listing.setListingType(listingType);
        listing.setStockExchange(exchange);
        listing.setTicker(ticker);
        listing.setName(ticker);
        listing.setLastRefresh(LocalDateTime.of(2026, 4, 7, 9, 0));
        listing.setPrice(price);
        listing.setAsk(price);
        listing.setBid(price);
        listing.setChange(BigDecimal.ZERO);
        listing.setVolume(100L);
        return listing;
    }
}
