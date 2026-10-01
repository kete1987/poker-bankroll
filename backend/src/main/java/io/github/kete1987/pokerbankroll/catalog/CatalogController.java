package io.github.kete1987.pokerbankroll.catalog;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
@Tag(name = "Catalog", description = "Reference data used by the forms")
class CatalogController {

    private final CurrencyRepository currencies;

    CatalogController(CurrencyRepository currencies) {
        this.currencies = currencies;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Currencies, game types and modalities, in display order")
    Catalog catalog() {
        List<CurrencyResponse> currencyList = currencies.findAllByOrderByCode().stream()
                .map(currency -> new CurrencyResponse(currency.getCode(), currency.getSymbol(), currency.getDecimals()))
                .toList();
        return new Catalog(currencyList, List.of(GameType.values()), List.of(Modality.values()));
    }

    /** The codes are translated by the frontend. */
    record Catalog(List<CurrencyResponse> currencies, List<GameType> gameTypes, List<Modality> modalities) {
    }

    record CurrencyResponse(String code, String symbol, int decimals) {
    }
}
