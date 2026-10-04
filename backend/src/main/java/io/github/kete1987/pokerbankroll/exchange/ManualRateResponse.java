package io.github.kete1987.pokerbankroll.exchange;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** An exchange rate typed by the user: it wins over the downloaded one of its day. */
public record ManualRateResponse(
        String currencyCode,
        @Schema(description = "Day from which it applies, until the next rate of the currency")
        LocalDate date,
        @Schema(description = "Units of the currency that 1 EUR is worth")
        BigDecimal rate,
        @Schema(description = "The rate downloaded from the ECB for the same day, which this one replaces; null when "
                + "there is none")
        @Nullable BigDecimal downloadedRate) {
}
