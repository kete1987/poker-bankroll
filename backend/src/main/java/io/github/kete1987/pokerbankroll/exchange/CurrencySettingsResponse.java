package io.github.kete1987.pokerbankroll.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** Which currency amounts of several currencies are shown in, converted. */
public record CurrencySettingsResponse(
        @Schema(description = "Chosen by the user; null when it is automatic")
        @Nullable String baseCurrencyCode,
        @Schema(description = "The one used when none is chosen: the currency with most games; without games the "
                + "first one of the rooms and movements; without anything EUR")
        String automaticBaseCurrencyCode,
        @Schema(description = "The base currency in use: the chosen one, or the automatic one")
        String effectiveBaseCurrencyCode) {
}
