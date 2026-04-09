package com.banka1.transaction_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for querying currency conversion details.
 * Contains information about the source and target currencies and the amount to be converted.
 */
@Getter
@Setter
public class ConversionQueryDto {

    private static final String SUPPORTED_CURRENCY_REGEX = "^(?i)(RSD|EUR|CHF|USD|GBP|JPY|CAD|AUD)$";
    private static final String SUPPORTED_CURRENCY_MESSAGE =
            "Supported currencies are RSD, EUR, CHF, USD, GBP, JPY, CAD and AUD.";

    /** Source currency code. */
    private String fromCurrency;

    /** Target currency code. */
    private String toCurrency;

    /** Amount to be converted. */
    private BigDecimal amount;

    /** Conversion rate to be applied. */
    private LocalDate date;
}
