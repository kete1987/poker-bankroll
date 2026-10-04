package io.github.kete1987.pokerbankroll.exchange;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Currencies", description = "Base currency and exchange rates, to show several currencies together")
// Method names are the operation ids of the contract: unique ones keep the ids of other endpoints.
class ExchangeRateController {

    private final ExchangeRateService service;

    ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    @GetMapping("/settings/currency")
    @Operation(summary = "Base currency",
            description = "Amounts of several currencies are shown together converted to it. When none is chosen, "
                    + "it is the currency with most games.")
    CurrencySettingsResponse getCurrencySettings() {
        return service.settings();
    }

    @PutMapping("/settings/currency")
    @Operation(summary = "Choose the base currency",
            description = "Null leaves it automatic. The rates the new one needs are downloaded afterwards, in the "
                    + "background.")
    CurrencySettingsResponse updateCurrencySettings(@Valid @RequestBody CurrencySettingsRequest request) {
        return service.updateSettings(request);
    }

    @GetMapping("/exchange-rates/status")
    @Operation(summary = "Exchange rates there are and how their download went",
            description = "Rates are those of the European Central Bank, downloaded from Frankfurter when the API "
                    + "starts and every day, plus the ones typed by hand.")
    ExchangeRateStatusResponse getExchangeRateStatus() {
        return service.status();
    }

    @PostMapping("/exchange-rates/refresh")
    @Operation(summary = "Download the missing exchange rates now",
            description = "Waits for the download and returns the status, with `lastError` when something failed. "
                    + "409 EXCHANGE_RATES_DISABLED when the installation does not download rates.")
    ExchangeRateStatusResponse refreshExchangeRates() {
        return service.refresh();
    }

    // A plain list, not a page: manual rates are a fallback, a few of them.
    @GetMapping("/exchange-rates/manual")
    @Operation(summary = "List the rates typed by hand", description = "Newest first.")
    List<ManualRateResponse> listManualRates() {
        return service.manualRates();
    }

    @PutMapping("/exchange-rates/manual/{currencyCode}/{date}")
    @Operation(summary = "Record a rate by hand",
            description = "The rate of a currency from a day on (until its next rate), per 1 EUR. It wins over the "
                    + "downloaded rate of that day. Replaces the one typed before for the same currency and day. "
                    + "EUR has no rate (EXCHANGE_RATE_OF_EUR): it is always 1.")
    ManualRateResponse saveManualRate(
            @Parameter(description = "Currency of the catalog, e.g. USD") @PathVariable String currencyCode,
            @Parameter(description = "Day of the rate, e.g. 2026-10-01") @PathVariable LocalDate date,
            @Valid @RequestBody ManualRateRequest request) {
        return service.saveManualRate(currencyCode, date, request);
    }

    @DeleteMapping("/exchange-rates/manual/{currencyCode}/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a rate typed by hand",
            description = "The downloaded rate of that day, if any, applies again.")
    void deleteManualRate(@PathVariable String currencyCode, @PathVariable LocalDate date) {
        service.deleteManualRate(currencyCode, date);
    }
}
