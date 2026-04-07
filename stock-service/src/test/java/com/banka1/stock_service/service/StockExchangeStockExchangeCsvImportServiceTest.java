package com.banka1.stock_service.service;

import com.banka1.stock_service.config.StockExchangeSeedProperties;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.StockExchangeImportResponse;
import com.banka1.stock_service.repository.StockExchangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CsvImportService}.
 */
@ExtendWith(MockitoExtension.class)
class StockExchangeCsvImportServiceTest {

    @Mock
    private StockExchangeRepository stockExchangeRepository;

    @Test
    void importFromResourceCreatesNewExchangesFromCsv() {
        CsvImportService service = createService();
        when(stockExchangeRepository.findAllByExchangeMICCodeIn(any())).thenReturn(List.of());
        when(stockExchangeRepository.saveAll(any())).thenAnswer(invocation -> List.of());

        StockExchangeImportResponse response = service.importFromResource(
                csvResource("""
                        Exchange Name,Exchange Acronym,Exchange Mic Code,Country,Currency,Time Zone,Open Time,Close Time,Pre Market Open Time,Pre Market Close Time,Post Market Open Time,Post Market Close Time,Is Active
                        New York Stock Exchange,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00,07:00,09:30,16:00,20:00,true
                        London Stock Exchange,LSE,XLON,United Kingdom,GBP,Europe/London,08:00,16:30,,,,,true
                        """),
                "test-exchanges.csv"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockExchange>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockExchangeRepository).saveAll(captor.capture());
        List<StockExchange> savedEntities = captor.getValue();

        assertThat(response.processedRows()).isEqualTo(2);
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isZero();
        assertThat(savedEntities).hasSize(2);
        assertThat(savedEntities.getFirst().getExchangeMICCode()).isEqualTo("XNYS");
        assertThat(savedEntities.getFirst().getOpenTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(savedEntities.getFirst().getPostMarketCloseTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(savedEntities.get(1).getPreMarketOpenTime()).isNull();
    }

    @Test
    void importFromResourceUpdatesExistingExchangeWhenCsvChanges() {
        StockExchange existingExchange = new StockExchange();
        existingExchange.setExchangeMICCode("XNYS");
        existingExchange.setExchangeName("Old Name");
        existingExchange.setExchangeAcronym("NYSE");
        existingExchange.setPolity("United States");
        existingExchange.setCurrency("USD");
        existingExchange.setTimeZone("America/New_York");
        existingExchange.setOpenTime(LocalTime.of(10, 0));
        existingExchange.setCloseTime(LocalTime.of(16, 0));
        existingExchange.setPreMarketOpenTime(LocalTime.of(7, 0));
        existingExchange.setPreMarketCloseTime(LocalTime.of(9, 30));
        existingExchange.setPostMarketOpenTime(LocalTime.of(16, 0));
        existingExchange.setPostMarketCloseTime(LocalTime.of(20, 0));
        existingExchange.setIsActive(true);

        CsvImportService service = createService();
        when(stockExchangeRepository.findAllByExchangeMICCodeIn(any())).thenReturn(List.of(existingExchange));
        when(stockExchangeRepository.saveAll(any())).thenAnswer(invocation -> List.of(existingExchange));

        StockExchangeImportResponse response = service.importFromResource(
                csvResource("""
                        Exchange Name,Exchange Acronym,Exchange Mic Code,Country,Currency,Time Zone,Open Time,Close Time,Pre Market Open Time,Pre Market Close Time,Post Market Open Time,Post Market Close Time,Is Active
                        New York Stock Exchange,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00,07:00,09:30,16:00,20:00,true
                        """),
                "test-exchanges.csv"
        );

        assertThat(response.processedRows()).isEqualTo(1);
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.unchangedCount()).isZero();
        assertThat(existingExchange.getExchangeName()).isEqualTo("New York Stock Exchange");
        assertThat(existingExchange.getOpenTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void importFromResourceSkipsUnchangedExchangeOnRepeatedImport() {
        StockExchange existingExchange = new StockExchange();
        existingExchange.setExchangeName("New York Stock Exchange");
        existingExchange.setExchangeAcronym("NYSE");
        existingExchange.setExchangeMICCode("XNYS");
        existingExchange.setPolity("United States");
        existingExchange.setCurrency("USD");
        existingExchange.setTimeZone("America/New_York");
        existingExchange.setOpenTime(LocalTime.of(9, 30));
        existingExchange.setCloseTime(LocalTime.of(16, 0));
        existingExchange.setPreMarketOpenTime(LocalTime.of(7, 0));
        existingExchange.setPreMarketCloseTime(LocalTime.of(9, 30));
        existingExchange.setPostMarketOpenTime(LocalTime.of(16, 0));
        existingExchange.setPostMarketCloseTime(LocalTime.of(20, 0));
        existingExchange.setIsActive(true);

        CsvImportService service = createService();
        when(stockExchangeRepository.findAllByExchangeMICCodeIn(any())).thenReturn(List.of(existingExchange));

        StockExchangeImportResponse response = service.importFromResource(
                csvResource("""
                        Exchange Name,Exchange Acronym,Exchange Mic Code,Country,Currency,Time Zone,Open Time,Close Time,Pre Market Open Time,Pre Market Close Time,Post Market Open Time,Post Market Close Time,Is Active
                        New York Stock Exchange,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00,07:00,09:30,16:00,20:00,true
                        """),
                "test-exchanges.csv"
        );

        assertThat(response.processedRows()).isEqualTo(1);
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isEqualTo(1);
        verify(stockExchangeRepository, never()).saveAll(any());
    }

    @Test
    void importFromResourceRejectsDuplicateMicCodesInsideCsv() {
        CsvImportService service = createService();

        assertThatThrownBy(() -> service.importFromResource(
                csvResource("""
                        Exchange Name,Exchange Acronym,Exchange Mic Code,Country,Currency,Time Zone,Open Time,Close Time,Pre Market Open Time,Pre Market Close Time,Post Market Open Time,Post Market Close Time,Is Active
                        New York Stock Exchange,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00,07:00,09:30,16:00,20:00,true
                        NYSE Duplicate,NYSE,XNYS,United States,USD,America/New_York,09:30,16:00,07:00,09:30,16:00,20:00,true
                        """),
                "test-exchanges.csv"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MIC code 'XNYS'");
    }

    private CsvImportService createService() {
        return new CsvImportService(
                stockExchangeRepository,
                new StockExchangeSeedProperties(true, "classpath:seed/exchanges.csv"),
                new DefaultResourceLoader()
        );
    }

    private ByteArrayResource csvResource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
