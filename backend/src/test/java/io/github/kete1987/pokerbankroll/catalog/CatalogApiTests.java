package io.github.kete1987.pokerbankroll.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

class CatalogApiTests extends ApiIntegrationTest {

    @Test
    void returnsCurrenciesGameTypesAndModalities() {
        var json = assertThat(mvc.get().uri("/catalog")).hasStatusOk().bodyJson();

        json.extractingPath("$.currencies[*].code").asArray().containsExactly("EUR", "USD");
        json.extractingPath("$.currencies[0].symbol").isEqualTo("€");
        json.extractingPath("$.currencies[0].decimals").isEqualTo(2);
        json.extractingPath("$.gameTypes").asArray().containsExactly("TOURNAMENT", "SIT_AND_GO", "CASH");
        json.extractingPath("$.modalities").asArray().containsExactly("NLHE", "PLO");
    }

    @Test
    void gameTypeEnumMatchesItsTable() {
        assertThat(jdbc.queryForList("select code from game_type order by sort_order", String.class))
                .isEqualTo(names(GameType.values()));
    }

    @Test
    void modalityEnumMatchesItsTable() {
        assertThat(jdbc.queryForList("select code from modality order by sort_order", String.class))
                .isEqualTo(names(Modality.values()));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
