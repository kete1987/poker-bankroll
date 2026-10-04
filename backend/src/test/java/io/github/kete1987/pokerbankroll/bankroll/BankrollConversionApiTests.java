package io.github.kete1987.pokerbankroll.bankroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bankroll of several currencies converted to the base currency: what happened with the rates
 * of its day, a balance with the rates of the day it is taken. Rates are per 1 EUR.
 */
class BankrollConversionApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    /**
     * The dollar is worth 1 EUR in January, 0.80 EUR (1.25 per euro) in February and 0.50 EUR
     * (2 per euro) from March on, which is the rate of today.
     */
    @BeforeEach
    void createRoomsAndRates() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
        insertRate("USD", "2026-01-02", "1.00");
        insertRate("USD", "2026-02-02", "1.25");
        insertRate("USD", "2026-03-02", "2.00");
        chooseBaseCurrency("EUR");
    }

    private void januaryToMarch() {
        movement("2026-01-05", "DEPOSIT", pokerStars, null, "100");  // 100 EUR
        movement("2026-01-05", "DEPOSIT", winamax, null, "50");
        game(pokerStars, "2026-02-03", "5", "30");  // +25 USD = +20 EUR
        movement("2026-03-10", "BONUS", winamax, null, "10");
    }

    @Test
    void theBankrollNowIsEachBalanceAtTodaysRateAndWhatHappenedAtTheRateOfItsDay() {
        januaryToMarch();
        String json = summary("");

        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");
        assertNumber(json, "$.currencies[1].total.bankroll", "125");
        String total = "$.converted.total";
        assertThat(JsonPath.<String>read(json, "$.converted.currencyCode")).isEqualTo("EUR");
        assertNumber(json, total + ".deposited", "150");
        assertNumber(json, total + ".gamesNet", "20");
        assertNumber(json, total + ".bonuses", "10");
        assertNumber(json, total + ".result", "30");
        // 125 USD at 2 per euro, plus 60 EUR: not 150 + 30, the dollar is worth less today.
        assertNumber(json, total + ".bankroll", "122.50");
        assertThat(JsonPath.<String>read(json, "$.converted.balanceRatesOn")).isEqualTo(LocalDate.now().toString());
        assertThat(JsonPath.<Object>read(json, "$.converted.rooms[*].room.name"))
                .hasToString("[\"PokerStars\",\"Winamax\"]");
        assertNumber(json, "$.converted.rooms[0].figures.deposited", "100");
        assertNumber(json, "$.converted.rooms[0].figures.bankroll", "62.50");
        assertNumber(json, "$.converted.rooms[1].figures.bankroll", "60");
        assertThat(JsonPath.<List<Object>>read(json, "$.converted.missingRates")).isEmpty();
    }

    @Test
    void untilADayTheBalanceHasTheRatesOfThatDay() {
        januaryToMarch();
        String json = summary("?to=2026-02-10");
        assertThat(JsonPath.<String>read(json, "$.converted.balanceRatesOn")).isEqualTo("2026-02-10");
        // 125 USD at 1.25 per euro, plus 50 EUR.
        assertNumber(json, "$.converted.total.bankroll", "150");
    }

    @Test
    void inAPeriodTheBankrollIsWhatChangedConvertedDayByDay() {
        januaryToMarch();
        String json = summary("?from=2026-01-01&to=2026-12-31");
        assertThat(JsonPath.<Object>read(json, "$.converted.balanceRatesOn")).isNull();
        assertNumber(json, "$.converted.total.bankroll", "180");
        assertNumber(json, "$.converted.total.result", "30");
    }

    @Test
    void aSelectionInOneCurrencyIsTheSameConvertedIntoIt() {
        januaryToMarch();
        String json = summary("?roomId=" + winamax);
        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\"]");
        assertThat(JsonPath.<Object>read(json, "$.converted.total"))
                .isEqualTo(JsonPath.read(json, "$.currencies[0].total"));
        assertThat(JsonPath.<Object>read(json, "$.converted.rooms"))
                .isEqualTo(JsonPath.read(json, "$.currencies[0].rooms"));
    }

    @Test
    void movementsWithoutARoomAreConvertedToo() {
        movement("2026-02-05", "DEPOSIT", null, "USD", "50");  // 40 EUR that day, 25 EUR today
        movement("2026-02-05", "DEPOSIT", null, "EUR", "10");
        String json = summary("");
        assertNumber(json, "$.converted.withoutRoom.deposited", "50");
        assertNumber(json, "$.converted.withoutRoom.bankroll", "35");
        assertNumber(json, "$.converted.total.bankroll", "35");
    }

    @Test
    void amountsWithoutARateAreLeftOutAndReported() {
        // Before the first rate of the dollar.
        movement("2025-12-20", "DEPOSIT", pokerStars, null, "10");
        String json = summary("");
        assertNumber(json, "$.converted.total.deposited", "0");
        // As a balance, today's rate converts it.
        assertNumber(json, "$.converted.total.bankroll", "5");
        assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.converted.missingRates"))
                .containsExactly(Map.of("currencyCode", "USD", "from", "2025-12-20", "to", "2025-12-20"));
    }

    @Test
    void anEmptyRoomNeedsNoRate() {
        // Before the first rate of the dollar, the room in dollars has nothing: zero in any currency.
        movement("2025-12-20", "DEPOSIT", winamax, null, "10");
        String json = summary("?to=2025-12-31");
        assertNumber(json, "$.converted.total.bankroll", "10");
        assertThat(JsonPath.<List<Object>>read(json, "$.converted.missingRates")).isEmpty();

        // Nor does a freeroll in dollars that won nothing.
        game(pokerStars, "2025-12-21", "0", "0");
        assertThat(JsonPath.<List<Object>>read(summary("?to=2025-12-31"), "$.converted.missingRates")).isEmpty();
    }

    @Test
    void theEvolutionValuesEachBalanceAtTheEndOfEachPeriod() {
        januaryToMarch();
        String json = evolution("?groupBy=MONTH");

        String total = "$.converted.total";
        assertThat(JsonPath.<String>read(json, "$.converted.currencyCode")).isEqualTo("EUR");
        assertNumber(json, total + ".startingBankroll", "0");
        assertThat(JsonPath.<Object>read(json, total + ".periods[*].period"))
                .hasToString("[\"2026-01\",\"2026-02\",\"2026-03\"]");
        assertNumbers(json, total + ".periods[*].deposited", "150", "0", "0");
        assertNumbers(json, total + ".periods[*].gamesNet", "0", "20", "0");
        assertNumbers(json, total + ".periods[*].bonuses", "0", "0", "10");
        // 100 USD at 1, 125 USD at 1.25 and at 2 per euro; plus the euros.
        assertNumbers(json, total + ".periods[*].bankroll", "150", "150", "122.50");

        // Every room has every period: a balance in dollars changes value when nothing happens.
        assertThat(JsonPath.<Object>read(json, "$.converted.rooms[*].room.name"))
                .hasToString("[\"PokerStars\",\"Winamax\"]");
        assertNumbers(json, "$.converted.rooms[0].series.periods[*].bankroll", "100", "100", "62.50");
        assertNumbers(json, "$.converted.rooms[1].series.periods[*].bankroll", "50", "50", "60");
        // Each currency as it is, as before.
        assertThat(JsonPath.<Object>read(json, "$.currencies[1].total.periods[*].period"))
                .hasToString("[\"2026-01\",\"2026-02\"]");
    }

    @Test
    void theStartingBankrollHasTheRatesOfTheDayBefore() {
        januaryToMarch();
        String json = evolution("?groupBy=MONTH&from=2026-02-01&to=2026-02-28");
        // 100 USD on 2026-01-31, at 1 per euro, plus 50 EUR.
        assertNumber(json, "$.converted.total.startingBankroll", "150");
        assertNumbers(json, "$.converted.total.periods[*].bankroll", "150");
        assertNumbers(json, "$.converted.rooms[0].series.periods[*].gamesNet", "20");

        // Up to a day in the middle of a period, its balance has the rates of that day, not of its end.
        insertRate("USD", "2026-03-20", "4.00");
        assertNumbers(evolution("?groupBy=MONTH&to=2026-03-15"), "$.converted.total.periods[*].bankroll",
                "150", "150", "122.50");
        // 125 USD at 4 per euro.
        assertNumbers(evolution("?groupBy=MONTH"), "$.converted.total.periods[*].bankroll", "150", "150", "91.25");
    }

    @Test
    void theCurrentPeriodHasTheRatesOfTodayAsTheSummary() {
        januaryToMarch();
        LocalDate today = LocalDate.now();
        movement(today.toString(), "BONUS", pokerStars, null, "5");
        // A rate typed for a day still to come, inside the current month.
        insertRate("USD", today.plusDays(5).toString(), "8.00", "MANUAL");

        String evolution = evolution("?groupBy=MONTH");
        List<Object> bankrolls = JsonPath.read(evolution, "$.converted.total.periods[*].bankroll");
        assertNumber(evolution, "$.converted.total.periods[" + (bankrolls.size() - 1) + "].bankroll",
                JsonPath.<Object>read(summary(""), "$.converted.total.bankroll").toString());
    }

    private void movement(String occurredOn, String type, Long roomId, String currencyCode, String amount) {
        jdbc.update("""
                insert into bankroll_movement (occurred_on, type, room_id, currency_code, amount)
                values (?::date, ?, ?, ?, ?::numeric)
                """, occurredOn, type, roomId, currencyCode, amount);
    }

    private void game(long roomId, String playedOn, String buyIn, String prize) {
        jdbc.update("""
                insert into game (played_on, room_id, game_type_code, buy_in, prize)
                values (?::date, ?, 'TOURNAMENT', ?::numeric, ?::numeric)
                """, playedOn, roomId, buyIn, prize);
    }

    private String summary(String query) {
        return body("/bankroll/summary" + query);
    }

    private String evolution(String query) {
        return body("/bankroll/evolution" + query);
    }

    private String body(String uri) {
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
