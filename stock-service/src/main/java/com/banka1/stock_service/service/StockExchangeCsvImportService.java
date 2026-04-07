package com.banka1.stock_service.service;

import com.banka1.stock_service.config.StockExchangeSeedProperties;
import com.banka1.stock_service.domain.StockExchange;
import com.banka1.stock_service.dto.StockExchangeImportResponse;
import com.banka1.stock_service.repository.StockExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports stock exchange reference data from a CSV file and upserts it into the database.
 *
 * <p>The import is idempotent and keyed by the exchange MIC code.
 * That means:
 *
 * <ul>
 *     <li>if a MIC code does not exist yet, a new exchange is created</li>
 *     <li>if a MIC code already exists and at least one imported value changed, the exchange is updated</li>
 *     <li>if a MIC code already exists and all imported values are the same, the row is counted as unchanged</li>
 * </ul>
 *
 * <p>The default seed file is now {@code classpath:seed/exchanges.csv}.
 * That file contains regular market hours but does not contain pre-market, post-market, or {@code Is Active} columns,
 * so this importer fills those missing optional values as follows:
 *
 * <ul>
 *     <li>pre-market times -> {@code null}</li>
 *     <li>post-market times -> {@code null}</li>
 *     <li>{@code isActive} -> {@code true}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final List<String> REQUIRED_HEADERS = List.of(
            "Exchange Name",
            "Exchange Acronym",
            "Exchange Mic Code",
            "Country",
            "Currency",
            "Time Zone",
            "Open Time",
            "Close Time"
    );
    private static final List<String> EXCHANGE_NAME_HEADERS = List.of("Exchange Name");
    private static final List<String> ACRONYM_HEADERS = List.of("Exchange Acronym");
    private static final List<String> MIC_CODE_HEADERS = List.of("Exchange Mic Code");
    private static final List<String> POLITY_HEADERS = List.of("Country");
    private static final List<String> CURRENCY_HEADERS = List.of("Currency");
    private static final List<String> TIME_ZONE_HEADERS = List.of("Time Zone");
    private static final List<String> OPEN_TIME_HEADERS = List.of("Open Time");
    private static final List<String> CLOSE_TIME_HEADERS = List.of("Close Time");
    private static final List<String> PRE_MARKET_OPEN_HEADERS = List.of("Pre Market Open Time");
    private static final List<String> PRE_MARKET_CLOSE_HEADERS = List.of("Pre Market Close Time");
    private static final List<String> POST_MARKET_OPEN_HEADERS = List.of("Post Market Open Time");
    private static final List<String> POST_MARKET_CLOSE_HEADERS = List.of("Post Market Close Time");
    private static final List<String> IS_ACTIVE_HEADERS = List.of("Is Active");

    private final StockExchangeRepository stockExchangeRepository;
    private final StockExchangeSeedProperties stockExchangeSeedProperties;
    private final ResourceLoader resourceLoader;

    /**
     * Imports the configured CSV source from application properties.
     *
     * <p>This is the entry point used by startup seeding and by the manual admin import endpoint.
     *
     * @return import summary with created, updated, and unchanged counters
     */
    @Transactional
    public StockExchangeImportResponse importFromConfiguredCsv() {
        return importFromLocation(stockExchangeSeedProperties.csvLocation());
    }

    /**
     * Imports stock exchanges from the provided Spring resource location.
     *
     * <p>Example locations:
     *
     * <ul>
     *     <li>{@code classpath:seed/exchanges.csv}</li>
     *     <li>{@code file:./custom/exchanges.csv}</li>
     * </ul>
     *
     * @param csvLocation Spring resource location, for example {@code classpath:seed/exchanges.csv}
     * @return import summary
     */
    @Transactional
    public StockExchangeImportResponse importFromLocation(String csvLocation) {
        Resource resource = resourceLoader.getResource(csvLocation);
        return importFromResource(resource, csvLocation);
    }

    /**
     * Imports stock exchanges from the provided resource.
     *
     * <p>This method intentionally splits the process into two steps:
     *
     * <ol>
     *     <li>parse and validate CSV rows into an intermediate row model</li>
     *     <li>persist those rows as create/update/unchanged database operations</li>
     * </ol>
     *
     * @param resource CSV resource
     * @param source source label used in the response
     * @return import summary
     */
    @Transactional
    public StockExchangeImportResponse importFromResource(Resource resource, String source) {
        List<StockExchangeCsvRow> rows = parseCsv(resource, source);
        return persistRows(rows, source);
    }

    /**
     * Persists parsed CSV rows into the database using MIC code as the stable business key.
     *
     * <p>The method first loads all existing exchanges for the imported MIC codes in one repository call.
     * It then decides row by row whether to:
     *
     * <ul>
     *     <li>create a new entity</li>
     *     <li>update an existing entity</li>
     *     <li>skip persistence because nothing changed</li>
     * </ul>
     *
     * <p>Example:
     * if the CSV contains {@code XNAS} and the database already contains {@code XNAS} with the same values,
     * the row is counted as unchanged.
     * If {@code XNAS} exists but the open time changed from {@code 09:00} to {@code 09:30},
     * the entity is updated and counted in {@code updatedCount}.
     *
     * @param rows validated parsed rows
     * @param source human-readable source label
     * @return import summary
     */
    private StockExchangeImportResponse persistRows(List<StockExchangeCsvRow> rows, String source) {
        Collection<String> micCodes = rows.stream()
                .map(StockExchangeCsvRow::exchangeMICCode)
                .toList();

        Map<String, StockExchange> existingByMicCode = stockExchangeRepository.findAllByExchangeMICCodeIn(micCodes)
                .stream()
                .collect(Collectors.toMap(StockExchange::getExchangeMICCode, Function.identity()));

        List<StockExchange> entitiesToPersist = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (StockExchangeCsvRow row : rows) {
            StockExchange existingEntity = existingByMicCode.get(row.exchangeMICCode());
            if (existingEntity == null) {
                StockExchange newEntity = new StockExchange();
                applyRow(newEntity, row);
                entitiesToPersist.add(newEntity);
                createdCount++;
                continue;
            }

            if (applyRowIfChanged(existingEntity, row)) {
                entitiesToPersist.add(existingEntity);
                updatedCount++;
                continue;
            }

            unchangedCount++;
        }

        if (!entitiesToPersist.isEmpty()) {
            stockExchangeRepository.saveAll(entitiesToPersist);
        }

        return new StockExchangeImportResponse(
                source,
                rows.size(),
                createdCount,
                updatedCount,
                unchangedCount
        );
    }

    /**
     * Reads and validates a CSV resource and converts it into intermediate row objects.
     *
     * <p>Validation performed here includes:
     *
     * <ul>
     *     <li>resource existence</li>
     *     <li>non-empty header row</li>
     *     <li>presence of required headers from the supported {@code exchanges.csv} format</li>
     *     <li>consistent column count per row</li>
     *     <li>duplicate MIC-code detection inside the same CSV file</li>
     *     <li>valid time and boolean parsing</li>
     * </ul>
     *
     * <p>The parser keeps row numbers in error messages so invalid files are easy to debug.
     *
     * @param resource CSV resource to read
     * @param source human-readable source label used in error messages
     * @return parsed CSV rows ready for persistence
     */
    private List<StockExchangeCsvRow> parseCsv(Resource resource, String source) {
        if (!resource.exists()) {
            throw new IllegalStateException("Stock exchange CSV resource does not exist: " + source);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("Stock exchange CSV is empty: " + source);
            }

            List<String> headerValues = parseCsvLine(headerLine, 1, source);
            Map<String, Integer> headerIndexes = indexHeaders(headerValues, source);
            validateHeaders(headerIndexes, source);

            List<StockExchangeCsvRow> rows = new ArrayList<>();
            Set<String> micCodes = new HashSet<>();
            int lineNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseCsvLine(line, lineNumber, source);
                if (values.stream().allMatch(String::isBlank)) {
                    continue;
                }

                if (values.size() != headerValues.size()) {
                    throw new IllegalArgumentException(
                            "CSV row " + lineNumber + " in " + source + " has " + values.size()
                                    + " columns, expected " + headerValues.size()
                    );
                }

                StockExchangeCsvRow row = mapRow(values, headerIndexes, lineNumber, source);
                if (!micCodes.add(row.exchangeMICCode())) {
                    throw new IllegalArgumentException(
                            "Duplicate MIC code '" + row.exchangeMICCode() + "' found in " + source
                                    + " on row " + lineNumber
                    );
                }
                rows.add(row);
            }

            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stock exchange CSV resource: " + source, exception);
        }
    }

    /**
     * Builds a header-name to column-index map from the first CSV row.
     *
     * <p>Headers are trimmed before indexing.
     * Duplicate header names are rejected because they would make column resolution ambiguous.
     *
     * @param headers parsed header row values
     * @param source source label used in error messages
     * @return header-index map
     */
    private Map<String, Integer> indexHeaders(List<String> headers, String source) {
        Map<String, Integer> headerIndexes = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalizedHeader = headers.get(i).trim();
            if (headerIndexes.putIfAbsent(normalizedHeader, i) != null) {
                throw new IllegalArgumentException("Duplicate CSV header '" + normalizedHeader + "' in " + source);
            }
        }
        return headerIndexes;
    }

    /**
     * Validates that all required business columns from {@code exchanges.csv} exist.
     *
     * <p>Optional columns such as pre-market, post-market, and {@code Is Active} are not checked here.
     *
     * @param headerIndexes indexed CSV headers
     * @param source source label used in error messages
     */
    private void validateHeaders(Map<String, Integer> headerIndexes, String source) {
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headerIndexes.containsKey(requiredHeader)) {
                throw new IllegalArgumentException(
                        "Missing required CSV header '" + requiredHeader + "' in " + source
                );
            }
        }
    }

    /**
     * Converts one parsed CSV row into the intermediate row record used by the importer.
     *
     * <p>This method resolves header aliases and applies defaulting rules for optional values.
     *
     * <p>For the current {@code exchanges.csv} format:
     *
     * <ul>
     *     <li>{@code Country} is mapped into the entity field {@code polity}</li>
     *     <li>missing pre/post-market columns become {@code null}</li>
     *     <li>missing {@code Is Active} becomes {@code true}</li>
     * </ul>
     *
     * @param values row values
     * @param headerIndexes indexed CSV headers
     * @param lineNumber current CSV row number for error reporting
     * @param source source label used in error messages
     * @return parsed row model
     */
    private StockExchangeCsvRow mapRow(
            List<String> values,
            Map<String, Integer> headerIndexes,
            int lineNumber,
            String source
    ) {
        return new StockExchangeCsvRow(
                requiredValue(values, headerIndexes, EXCHANGE_NAME_HEADERS, lineNumber, source),
                requiredValue(values, headerIndexes, ACRONYM_HEADERS, lineNumber, source),
                requiredValue(values, headerIndexes, MIC_CODE_HEADERS, lineNumber, source),
                requiredValue(values, headerIndexes, POLITY_HEADERS, lineNumber, source),
                requiredValue(values, headerIndexes, CURRENCY_HEADERS, lineNumber, source),
                requiredValue(values, headerIndexes, TIME_ZONE_HEADERS, lineNumber, source),
                parseTime(requiredValue(values, headerIndexes, OPEN_TIME_HEADERS, lineNumber, source), "Open Time", lineNumber, source),
                parseTime(requiredValue(values, headerIndexes, CLOSE_TIME_HEADERS, lineNumber, source), "Close Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, PRE_MARKET_OPEN_HEADERS), "Pre Market Open Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, PRE_MARKET_CLOSE_HEADERS), "Pre Market Close Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, POST_MARKET_OPEN_HEADERS), "Post Market Open Time", lineNumber, source),
                parseOptionalTime(optionalValue(values, headerIndexes, POST_MARKET_CLOSE_HEADERS), "Post Market Close Time", lineNumber, source),
                parseOptionalBoolean(optionalValue(values, headerIndexes, IS_ACTIVE_HEADERS))
        );
    }

    /**
     * Resolves a required CSV value through one or more accepted header names.
     *
     * <p>If the column does not exist or the resolved value is blank, the file is rejected.
     *
     * @param values current row values
     * @param headerIndexes indexed CSV headers
     * @param headers accepted header aliases
     * @param lineNumber current row number for error reporting
     * @param source source label used in error messages
     * @return non-blank required value
     */
    private String requiredValue(
            List<String> values,
            Map<String, Integer> headerIndexes,
            List<String> headers,
            int lineNumber,
            String source
    ) {
        String value = optionalValue(values, headerIndexes, headers);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing value for column " + headers + " on row " + lineNumber + " in " + source
            );
        }
        return value;
    }

    /**
     * Resolves an optional CSV value through one or more accepted header names.
     *
     * <p>If none of the accepted headers exist, the method returns {@code null}.
     * This is how the importer stays compatible with both the old and new CSV layouts.
     *
     * @param values current row values
     * @param headerIndexes indexed CSV headers
     * @param headers accepted header aliases
     * @return trimmed value or {@code null}
     */
    private String optionalValue(List<String> values, Map<String, Integer> headerIndexes, List<String> headers) {
        Integer index = headerIndexes.get(headers.getFirst());
        if (index == null || index >= values.size()) {
            return null;
        }
        return values.get(index).trim();
    }

    /**
     * Parses a required time column using {@code H:mm} format.
     *
     * <p>Accepted examples:
     *
     * <ul>
     *     <li>{@code 9:30}</li>
     *     <li>{@code 09:30}</li>
     *     <li>{@code 17:25}</li>
     * </ul>
     *
     * @param rawValue raw CSV value
     * @param header column name used in error messages
     * @param lineNumber row number used in error messages
     * @param source source label used in error messages
     * @return parsed local time
     */
    private LocalTime parseTime(String rawValue, String header, int lineNumber, String source) {
        try {
            return LocalTime.parse(rawValue, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid time value '" + rawValue + "' for column '" + header + "' on row "
                            + lineNumber + " in " + source + ". Expected HH:mm format.",
                    exception
            );
        }
    }

    /**
     * Parses an optional time column.
     *
     * <p>Blank or missing values are returned as {@code null}.
     * This is important for the new {@code exchanges.csv}, which currently does not define
     * pre-market or post-market windows.
     *
     * @param rawValue raw CSV value
     * @param header column name used in error messages
     * @param lineNumber row number used in error messages
     * @param source source label used in error messages
     * @return parsed local time or {@code null}
     */
    private LocalTime parseOptionalTime(String rawValue, String header, int lineNumber, String source) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return parseTime(rawValue, header, lineNumber, source);
    }

    /**
     * Parses a required boolean field from the CSV.
     *
     * <p>Supported true values:
     * {@code true}, {@code 1}, {@code yes}
     *
     * <p>Supported false values:
     * {@code false}, {@code 0}, {@code no}
     *
     * @param rawValue raw CSV value
     * @param lineNumber row number used in error messages
     * @param source source label used in error messages
     * @return parsed boolean value
     */
    private boolean parseBoolean(String rawValue, int lineNumber, String source) {
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException(
                    "Invalid boolean value '" + rawValue + "' on row " + lineNumber + " in " + source
            );
        };
    }

    /**
     * Parses an optional boolean field and defaults it to {@code true} when missing.
     *
     * <p>This default exists because the new seed file does not provide an {@code Is Active} column.
     * Without this fallback, every new CSV would have to explicitly include the flag even when all
     * imported exchanges should start as active.
     *
     * @param rawValue raw CSV value or {@code null}
     * @return parsed value or {@code true} when the field is absent
     */
    private boolean parseOptionalBoolean(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }
        return parseBoolean(rawValue, 0, "CSV");
    }

    /**
     * Parses one CSV line while respecting quoted values and escaped quotes.
     *
     * <p>Important behavior:
     *
     * <ul>
     *     <li>commas outside quotes split columns</li>
     *     <li>commas inside quotes are preserved as part of the value</li>
     *     <li>double quotes inside a quoted field are unescaped</li>
     *     <li>all returned values are trimmed</li>
     * </ul>
     *
     * <p>Example:
     * the line
     * {@code "Nasdaq, Inc.",NASDAQ,XNAS,...}
     * is parsed so that {@code Nasdaq, Inc.} stays a single field.
     *
     * @param line raw CSV line
     * @param lineNumber current CSV row number for error reporting
     * @param source source label used in error messages
     * @return parsed row values
     */
    private List<String> parseCsvLine(String line, int lineNumber, String source) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentCharacter = line.charAt(i);

            if (currentCharacter == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++;
                    continue;
                }

                inQuotes = !inQuotes;
                continue;
            }

            if (currentCharacter == ',' && !inQuotes) {
                values.add(currentValue.toString().trim());
                currentValue.setLength(0);
                continue;
            }

            currentValue.append(currentCharacter);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted CSV value on row " + lineNumber + " in " + source);
        }

        values.add(currentValue.toString().trim());
        return values;
    }

    /**
     * Copies parsed row values into a persistent entity.
     *
     * <p>This method performs the raw field assignment only.
     * It is used both when creating a brand-new exchange and when updating an existing one.
     *
     * @param entity target entity
     * @param row parsed CSV row
     */
    private void applyRow(StockExchange entity, StockExchangeCsvRow row) {
        entity.setExchangeName(row.exchangeName());
        entity.setExchangeAcronym(row.exchangeAcronym());
        entity.setExchangeMICCode(row.exchangeMICCode());
        entity.setPolity(row.polity());
        entity.setCurrency(row.currency());
        entity.setTimeZone(row.timeZone());
        entity.setOpenTime(row.openTime());
        entity.setCloseTime(row.closeTime());
        entity.setPreMarketOpenTime(row.preMarketOpenTime());
        entity.setPreMarketCloseTime(row.preMarketCloseTime());
        entity.setPostMarketOpenTime(row.postMarketOpenTime());
        entity.setPostMarketCloseTime(row.postMarketCloseTime());
        entity.setIsActive(row.isActive());
    }

    /**
     * Updates an existing entity only when at least one imported value changed.
     *
     * <p>This is what allows the import summary to distinguish between updated rows and unchanged rows.
     *
     * @param entity existing entity from the database
     * @param row parsed CSV row
     * @return {@code true} if the entity was changed and should be persisted
     */
    private boolean applyRowIfChanged(StockExchange entity, StockExchangeCsvRow row) {
        if (matches(entity, row)) {
            return false;
        }

        applyRow(entity, row);
        return true;
    }

    /**
     * Compares all imported business fields between the existing entity and the parsed row.
     *
     * <p>If this method returns {@code true}, the importer treats the row as unchanged and skips persistence.
     *
     * @param entity existing entity from the database
     * @param row parsed CSV row
     * @return {@code true} when all imported fields already match
     */
    private boolean matches(StockExchange entity, StockExchangeCsvRow row) {
        return Objects.equals(entity.getExchangeName(), row.exchangeName())
                && Objects.equals(entity.getExchangeAcronym(), row.exchangeAcronym())
                && Objects.equals(entity.getExchangeMICCode(), row.exchangeMICCode())
                && Objects.equals(entity.getPolity(), row.polity())
                && Objects.equals(entity.getCurrency(), row.currency())
                && Objects.equals(entity.getTimeZone(), row.timeZone())
                && Objects.equals(entity.getOpenTime(), row.openTime())
                && Objects.equals(entity.getCloseTime(), row.closeTime())
                && Objects.equals(entity.getPreMarketOpenTime(), row.preMarketOpenTime())
                && Objects.equals(entity.getPreMarketCloseTime(), row.preMarketCloseTime())
                && Objects.equals(entity.getPostMarketOpenTime(), row.postMarketOpenTime())
                && Objects.equals(entity.getPostMarketCloseTime(), row.postMarketCloseTime())
                && Objects.equals(entity.getIsActive(), row.isActive());
    }

    /**
     * Intermediate immutable representation of one validated CSV row.
     *
     * <p>This record exists so parsing/validation is separated from persistence.
     * The importer first converts the file into a clean row model and only then applies it to JPA entities.
     */
    private record StockExchangeCsvRow(
            String exchangeName,
            String exchangeAcronym,
            String exchangeMICCode,
            String polity,
            String currency,
            String timeZone,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime preMarketOpenTime,
            LocalTime preMarketCloseTime,
            LocalTime postMarketOpenTime,
            LocalTime postMarketCloseTime,
            Boolean isActive
    ) {
    }
}
