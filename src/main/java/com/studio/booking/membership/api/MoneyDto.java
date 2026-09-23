package com.studio.booking.membership.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Price with amount and currency")
public record MoneyDto(
    @JsonProperty("amount")
    @Schema(description = "Price amount as decimal string with up to 2 decimal places", example = "99.99")
    BigDecimal amount,

    @JsonProperty("currency")
    @Schema(description = "ISO 4217 3-letter currency code (uppercase)", example = "USD")
    String currency
) {
}
