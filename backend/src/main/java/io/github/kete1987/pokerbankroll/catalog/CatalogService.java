package io.github.kete1987.pokerbankroll.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reference data shared by REST and MCP. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CurrencyRepository currencies;

    CatalogService(CurrencyRepository currencies) {
        this.currencies = currencies;
    }

    public Catalog catalog() {
        return new Catalog(currencies.findAllByOrderByCode().stream()
                .map(currency -> new CurrencyResponse(currency.getCode(), currency.getSymbol(), currency.getDecimals()))
                .toList(), List.of(GameType.values()), List.of(Modality.values()));
    }

    public record Catalog(List<CurrencyResponse> currencies, List<GameType> gameTypes, List<Modality> modalities) {
    }

    public record CurrencyResponse(String code, String symbol, int decimals) {
    }
}
