package io.github.kete1987.pokerbankroll.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** The base currency chosen by the user. */
public record CurrencySettingsRequest(
        @Schema(description = "Code of a currency of the catalog, e.g. EUR; null (or omitted) for the automatic one: "
                + "the currency with most games")
        @Nullable String baseCurrencyCode) {
}
