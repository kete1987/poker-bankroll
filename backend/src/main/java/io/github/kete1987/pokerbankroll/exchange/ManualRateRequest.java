package io.github.kete1987.pokerbankroll.exchange;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** An exchange rate typed by the user. */
public record ManualRateRequest(
        @Schema(description = "Units of the currency that 1 EUR is worth that day, as the ECB publishes rates "
                + "(1 EUR = 1.0850 USD is 1.0850); up to 8 decimals")
        @NotNull @Positive @Digits(integer = 10, fraction = 8) BigDecimal rate) {
}
