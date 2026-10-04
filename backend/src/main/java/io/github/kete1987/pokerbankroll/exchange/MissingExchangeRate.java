package io.github.kete1987.pokerbankroll.exchange;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A currency without an exchange rate on or before some days: the amounts of those days that
 * needed it could not be converted and are left out of the converted figures. Rates are never
 * invented; a rate typed by hand or downloaded fills the gap.
 */
@Schema(description = "Amounts that could not be converted, for lack of a rate of this currency on or before their "
        + "day: they are left out of the converted figures")
public record MissingExchangeRate(
        @Schema(description = "The currency without a rate: the one of the amounts, or the base currency")
        String currencyCode,
        @Schema(description = "First day of an amount left out")
        LocalDate from,
        @Schema(description = "Last day of an amount left out")
        LocalDate to) {
}
