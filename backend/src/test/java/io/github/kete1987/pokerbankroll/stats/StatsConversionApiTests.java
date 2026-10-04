package io.github.kete1987.pokerbankroll.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The statistics of several currencies converted to the base currency, each game with the rates of
 * the day it was played. Rates are per 1 EUR.
 */
class StatsConversionApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    /**
     * 2026-01-16 is a Friday, 2026-01-17 a Saturday (no rate: Friday's applies) and 2026-01-19 a
     * Monday. On 2026-01-20 a rate typed by hand wins over the downloaded one.
     */
    @BeforeEach
    void createRoomsRatesAndGames() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
        insertRate("USD", "2026-01-16", "1.10");
        insertRate("USD", "2026-01-19", "1.25");
        insertRate("USD", "2026-01-20", "2.00");
        insertRate("USD", "2026-01-20", "1.00", "MANUAL");
        chooseBaseCurrency("EUR");

        game(winamax, "2026-01-17", "10", "30", "Sunday Million");  // +20 EUR
        game(pokerStars, "2026-01-17", "11", "0", "sunday million ");  // -11 USD = -10 EUR at 1.10
        game(pokerStars, "2026-01-19", "5", "30", null);  // +25 USD = +20 EUR at 1.25
        game(pokerStars, "2026-01-20", "10", "0", null);  // -10 USD = -10 EUR at 1.00 (manual)
    }

    @Test
    void convertsEachGameWithTheRateOfItsDayAndKeepsEachCurrencyAsItIs() {
        String json = summary("");

        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");
        assertNumber(json, "$.currencies[1].total.net", "4");
        assertThat(JsonPath.<String>read(json, "$.converted.currencyCode")).isEqualTo("EUR");
        String total = "$.converted.total";
        assertNumber(json, total + ".games", "4");
        assertNumber(json, total + ".winningGames", "2");
        assertNumber(json, total + ".invested", "34");
        assertNumber(json, total + ".won", "54");
        assertNumber(json, total + ".net", "20");
        assertNumber(json, total + ".roi", "0.5882");
        // (10 + 10 + 4 + 10) / 4
        assertNumber(json, total + ".averageBuyIn", "8.50");
        assertNumber(json, "$.converted.byGameType[0].figures.net", "20");
        assertThat(JsonPath.<List<Object>>read(json, "$.converted.missingRates")).isEmpty();
    }

    @Test
    void convertsToTheBaseCurrencyChosenOrToTheOneWithMostGames() {
        chooseBaseCurrency("USD");
        String json = summary("");
        assertThat(JsonPath.<String>read(json, "$.converted.currencyCode")).isEqualTo("USD");
        // 20 EUR at 1.10 is 22 USD; the dollars stay as they are.
        assertNumber(json, "$.converted.total.net", "26");

        // Automatic: most games are in dollars.
        jdbc.update("update currency_setting set base_currency_code = null");
        assertThat(JsonPath.<String>read(summary(""), "$.converted.currencyCode")).isEqualTo("USD");
    }

    @Test
    void aSelectionInOneCurrencyIsTheSameConvertedIntoIt() {
        String json = summary("?currency=EUR");
        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\"]");
        assertThat(JsonPath.<Object>read(json, "$.converted.total"))
                .isEqualTo(JsonPath.read(json, "$.currencies[0].total"));

        // In another one, it is converted; the client shows the currency of the selection.
        chooseBaseCurrency("USD");
        assertNumber(summary("?currency=EUR"), "$.converted.total.net", "22");
    }

    @Test
    void gamesWithoutARateAreLeftOutAndReported() {
        // Before the first rate of the dollar.
        game(pokerStars, "2026-01-10", "1", "0", null);
        game(pokerStars, "2026-01-12", "1", "0", null);

        String json = summary("");
        assertNumber(json, "$.currencies[1].total.games", "5");
        assertNumber(json, "$.converted.total.games", "4");
        assertNumber(json, "$.converted.total.net", "20");
        assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.converted.missingRates"))
                .containsExactly(Map.of("currencyCode", "USD", "from", "2026-01-10", "to", "2026-01-12"));

        // In dollars, nothing is missing for them; the euros of Saturday have Friday's rate.
        chooseBaseCurrency("USD");
        assertThat(JsonPath.<List<Object>>read(summary(""), "$.converted.missingRates")).isEmpty();
    }

    @Test
    void aFreerollWithoutARateNeedsNone() {
        // Before the first rate of the dollar, but no money at all: it still counts as a game.
        game(pokerStars, "2026-01-10", "0", "0", null);
        String json = summary("");
        assertNumber(json, "$.converted.total.games", "5");
        assertNumber(json, "$.converted.total.net", "20");
        assertThat(JsonPath.<List<Object>>read(json, "$.converted.missingRates")).isEmpty();
    }

    @Test
    void theBaseCurrencyCanBeTheOneWithoutARate() {
        jdbc.update("delete from exchange_rate");
        chooseBaseCurrency("USD");
        String json = summary("");
        // The euros cannot be converted to dollars: it is the dollar that has no rate.
        assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.converted.missingRates"))
                .containsExactly(Map.of("currencyCode", "USD", "from", "2026-01-17", "to", "2026-01-17"));
        assertNumber(json, "$.converted.total.games", "3");
    }

    @Test
    void convertsTheGroupsAndTheirCumulativeNet() {
        String json = groups("?groupBy=DAY");
        String groups = "$.converted.groups";
        assertThat(JsonPath.<Object>read(json, groups + "[*].key.period"))
                .hasToString("[\"2026-01-17\",\"2026-01-19\",\"2026-01-20\"]");
        assertNumbers(json, groups + "[*].figures.net", "10", "20", "-10");
        assertNumbers(json, groups + "[*].cumulativeNet", "10", "30", "20");
        // Per currency, as before.
        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");

        String months = groups("?groupBy=MONTH&byGameType=true");
        assertNumbers(months, "$.converted.groups[*].figures.net", "20");
        assertNumbers(months, "$.converted.groups[0].byGameType[*].figures.net", "20");
    }

    @Test
    void groupsByRoomAndByNameAcrossCurrencies() {
        String rooms = groups("?groupBy=ROOM");
        assertThat(JsonPath.<Object>read(rooms, "$.converted.groups[*].key.room.name"))
                .hasToString("[\"PokerStars\",\"Winamax\"]");
        assertNumbers(rooms, "$.converted.groups[*].figures.net", "0", "20");

        // Names are put together whatever their currency (a tie of spellings goes to the last one).
        String names = groups("?groupBy=NAME");
        assertThat(JsonPath.<Object>read(names, "$.converted.groups[*].key.name"))
                .hasToString("[\"sunday million\",null]");
        assertNumbers(names, "$.converted.groups[*].figures.games", "2", "2");
    }

    @Test
    void aBuyInStaysInItsCurrencyAndARangeIsTheOneOfTheConvertedBuyIn() {
        String buyIns = groups("?groupBy=BUY_IN");
        String groups = "$.converted.groups";
        assertNumbers(buyIns, groups + "[*].key.buyIn", "5", "10", "10", "11");
        assertThat(JsonPath.<Object>read(buyIns, groups + "[*].key.currencyCode"))
                .hasToString("[\"USD\",\"EUR\",\"USD\",\"USD\"]");
        assertThat(JsonPath.<Object>read(buyIns, "$.currencies[0].groups[0].key.currencyCode")).isNull();

        // 5 USD at 1.25 is 4 EUR; 11 USD at 1.10 and 10 USD at 1.00 are 10 EUR.
        String ranges = groups("?groupBy=BUY_IN_RANGE");
        assertNumbers(ranges, groups + "[*].key.buyInRange.from", "2", "10");
        assertNumbers(ranges, groups + "[*].figures.games", "1", "3");
    }

    private void game(long roomId, String playedOn, String buyIn, String prize, String name) {
        jdbc.update("""
                insert into game (played_on, room_id, game_type_code, buy_in, prize, name)
                values (?::date, ?, 'TOURNAMENT', ?::numeric, ?::numeric, ?)
                """, playedOn, roomId, buyIn, prize, name);
    }

    private String summary(String query) {
        return get("/stats/summary" + query);
    }

    private String groups(String query) {
        return get("/stats/groups" + query);
    }

    private String get(String uri) {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compares by value, whatever the scale the number was written with. */
    private static void assertNumber(String json, String path, String expected) {
        Object value = JsonPath.read(json, path);
        assertThat(value).as(path).isNotNull();
        assertThat(new BigDecimal(value.toString())).as(path).isEqualByComparingTo(expected);
    }

    private static void assertNumbers(String json, String path, String... expected) {
        List<Object> values = JsonPath.read(json, path);
        assertThat(values.stream().map(value -> new BigDecimal(value.toString()).stripTrailingZeros()).toList())
                .as(path)
                .isEqualTo(java.util.Arrays.stream(expected).map(value -> new BigDecimal(value).stripTrailingZeros())
                        .toList());
    }
}
