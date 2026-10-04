package io.github.kete1987.pokerbankroll.bankroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BankrollEvolutionApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    /** 2026-01-05 is a Monday. */
    private void januaryAndFebruaryInWinamax() {
        movement("2026-01-05", "DEPOSIT", winamax, null, "100");
        movement("2026-01-06", "BONUS", winamax, null, "5");
        movement("2026-01-20", "WITHDRAWAL", winamax, null, "30");
        movement("2026-02-03", "ADJUSTMENT", winamax, null, "-2");
        game(winamax, "2026-01-05", "FINISHED", "10", 1, "25");
        // In play: its buy-in is already spent.
        game(winamax, "2026-02-10", "IN_PLAY", "2", 2, "0");
    }

    @Test
    void sumsEachPeriodAndCarriesTheBankrollFromOneToTheNext() {
        januaryAndFebruaryInWinamax();

        String json = evolution("?groupBy=MONTH");

        assertThat(JsonPath.<String>read(json, "$.groupBy")).isEqualTo("MONTH");
        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\"]");
        String series = "$.currencies[0].rooms[0].series";
        assertThat(JsonPath.<String>read(json, "$.currencies[0].rooms[0].room.name")).isEqualTo("Winamax");
        assertThat(JsonPath.<Boolean>read(json, "$.currencies[0].rooms[0].active")).isTrue();
        assertNumber(json, series + ".startingBankroll", "0");
        assertThat(JsonPath.<Object>read(json, series + ".periods[*].period")).hasToString("[\"2026-01\",\"2026-02\"]");
        String january = series + ".periods[0]";
        assertThat(JsonPath.<String>read(json, january + ".startsOn")).isEqualTo("2026-01-01");
        assertThat(JsonPath.<String>read(json, january + ".endsOn")).isEqualTo("2026-01-31");
        assertNumber(json, january + ".deposited", "100");
        assertNumber(json, january + ".withdrawn", "30");
        assertNumber(json, january + ".bonuses", "5");
        assertNumber(json, january + ".adjustments", "0");
        assertNumber(json, january + ".gamesNet", "15");
        assertNumber(json, january + ".bankroll", "90");
        String february = series + ".periods[1]";
        assertNumber(json, february + ".deposited", "0");
        assertNumber(json, february + ".adjustments", "-2");
        assertNumber(json, february + ".gamesNet", "-4");
        assertNumber(json, february + ".bankroll", "84");

        // One room: the total is the same.
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].total"))
                .isEqualTo(JsonPath.read(json, series));
    }

    @Test
    void groupsByDayWeekAndYear() {
        januaryAndFebruaryInWinamax();

        String days = evolution("?groupBy=DAY");
        String series = "$.currencies[0].total";
        assertThat(JsonPath.<Object>read(days, series + ".periods[*].period"))
                .hasToString("[\"2026-01-05\",\"2026-01-06\",\"2026-01-20\",\"2026-02-03\",\"2026-02-10\"]");
        String[] bankrolls = {"115", "120", "90", "88", "84"};
        for (int i = 0; i < bankrolls.length; i++) {
            assertNumber(days, series + ".periods[" + i + "].bankroll", bankrolls[i]);
        }

        String weeks = evolution("?groupBy=WEEK");
        assertThat(JsonPath.<Object>read(weeks, series + ".periods[*].period"))
                .hasToString("[\"2026-01-05\",\"2026-01-19\",\"2026-02-02\",\"2026-02-09\"]");
        assertThat(JsonPath.<String>read(weeks, series + ".periods[0].endsOn")).isEqualTo("2026-01-11");
        assertNumber(weeks, series + ".periods[0].bankroll", "120");
        assertNumber(weeks, series + ".periods[3].bankroll", "84");

        String years = evolution("?groupBy=YEAR");
        assertThat(JsonPath.<Object>read(years, series + ".periods[*].period")).hasToString("[\"2026\"]");
        assertThat(JsonPath.<String>read(years, series + ".periods[0].endsOn")).isEqualTo("2026-12-31");
        assertNumber(years, series + ".periods[0].bankroll", "84");
    }

    @Test
    void theBankrollStartsWithEverythingBeforeTheRange() {
        januaryAndFebruaryInWinamax();

        String json = evolution("?groupBy=MONTH&from=2026-01-15&to=2026-01-31");

        String series = "$.currencies[0].rooms[0].series";
        // Deposit, bonus and the game of the first days of January.
        assertNumber(json, series + ".startingBankroll", "120");
        assertThat(JsonPath.<Object>read(json, series + ".periods[*].period")).hasToString("[\"2026-01\"]");
        // The period is the whole month, but only what is in the range counts in it.
        assertThat(JsonPath.<String>read(json, series + ".periods[0].startsOn")).isEqualTo("2026-01-01");
        assertNumber(json, series + ".periods[0].deposited", "0");
        assertNumber(json, series + ".periods[0].withdrawn", "30");
        assertNumber(json, series + ".periods[0].gamesNet", "0");
        assertNumber(json, series + ".periods[0].bankroll", "90");

        // Nothing after the range: a room with a bankroll is listed, without periods.
        String march = evolution("?groupBy=MONTH&from=2026-03-01");
        assertNumber(march, series + ".startingBankroll", "84");
        assertThat(JsonPath.<Integer>read(march, series + ".periods.length()")).isZero();
        assertNumber(march, "$.currencies[0].total.startingBankroll", "84");
    }

    @Test
    void movementsWithoutARoomOnlyCountInTheTotalAndNotWithARoomFilter() {
        movement("2026-01-01", "DEPOSIT", null, "EUR", "200");
        movement("2026-01-02", "DEPOSIT", winamax, null, "50");

        String json = evolution("?groupBy=MONTH");
        assertNumber(json, "$.currencies[0].total.periods[0].deposited", "250");
        assertNumber(json, "$.currencies[0].total.periods[0].bankroll", "250");
        assertNumber(json, "$.currencies[0].rooms[0].series.periods[0].bankroll", "50");

        String winamaxOnly = evolution("?groupBy=MONTH&roomId=" + winamax);
        assertNumber(winamaxOnly, "$.currencies[0].total.periods[0].deposited", "50");
        assertNumber(winamaxOnly, "$.currencies[0].total.periods[0].bankroll", "50");

        // A currency with only movements without a room has no rooms.
        jdbc.update("delete from bankroll_movement where room_id is not null");
        String withoutRooms = evolution("?groupBy=MONTH");
        assertThat(JsonPath.<Integer>read(withoutRooms, "$.currencies[0].rooms.length()")).isZero();
        assertNumber(withoutRooms, "$.currencies[0].total.periods[0].bankroll", "200");
        // And nothing at all when only rooms are asked for.
        assertThat(JsonPath.<Integer>read(evolution("?groupBy=MONTH&roomId=" + winamax), "$.currencies.length()"))
                .isZero();
    }

    @Test
    void oneSeriesPerRoomAndCurrenciesAreNeverAddedUp() {
        long unibet = insertRoom("Unibet", "EUR");
        long empty = insertRoom("888poker", "EUR");
        movement("2026-01-01", "DEPOSIT", winamax, null, "100");
        movement("2026-02-01", "DEPOSIT", unibet, null, "40");
        movement("2026-01-01", "DEPOSIT", pokerStars, null, "70");
        game(unibet, "2026-03-01", "FINISHED", "5", 1, "0");

        String json = evolution("?groupBy=MONTH");

        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");
        // Rooms by name; one without anything is left out.
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].rooms[*].room.name"))
                .hasToString("[\"Unibet\",\"Winamax\"]");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].rooms[0].series.periods[*].period"))
                .hasToString("[\"2026-02\",\"2026-03\"]");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].rooms[1].series.periods[*].period"))
                .hasToString("[\"2026-01\"]");
        String total = "$.currencies[0].total";
        assertThat(JsonPath.<Object>read(json, total + ".periods[*].period"))
                .hasToString("[\"2026-01\",\"2026-02\",\"2026-03\"]");
        assertNumber(json, total + ".periods[0].bankroll", "100");
        assertNumber(json, total + ".periods[1].bankroll", "140");
        assertNumber(json, total + ".periods[2].bankroll", "135");
        assertNumber(json, "$.currencies[1].total.periods[0].bankroll", "70");
        assertThat(JsonPath.<Object>read(json, "$.currencies[1].rooms[*].room.id"))
                .hasToString("[" + pokerStars + "]");

        // A room filter: only those rooms, added up.
        String filtered = evolution("?groupBy=MONTH&roomId=" + unibet + "," + empty);
        assertThat(JsonPath.<Object>read(filtered, "$.currencies[*].currencyCode")).hasToString("[\"EUR\"]");
        assertThat(JsonPath.<Object>read(filtered, "$.currencies[0].rooms[*].room.name")).hasToString("[\"Unibet\"]");
        assertNumber(filtered, "$.currencies[0].total.periods[1].bankroll", "35");
    }

    @Test
    void withoutDatesTheLastBankrollIsTheOneOfTheSummary() {
        long unibet = insertRoom("Unibet", "EUR");
        long inactive = insertRoom("888poker", "EUR");
        insertRoom("Unused", "EUR");
        movement("2025-11-30", "DEPOSIT", winamax, null, "100");
        movement("2026-01-15", "WITHDRAWAL", winamax, null, "20");
        movement("2026-01-16", "BONUS", winamax, null, "3.50");
        movement("2026-01-17", "ADJUSTMENT", winamax, null, "-1");
        movement("2026-01-01", "DEPOSIT", null, "EUR", "200");
        movement("2026-02-01", "DEPOSIT", pokerStars, null, "50");
        movement("2026-02-02", "WITHDRAWAL", null, "USD", "7");
        movement("2026-03-01", "DEPOSIT", inactive, null, "10");
        game(winamax, "2026-01-19", "FINISHED", "10", 2, "5");
        game(winamax, "2026-01-20", "IN_PLAY", "4", 2, "0");
        game(unibet, "2025-12-31", "FINISHED", "1", 1, "0");
        game(pokerStars, "2026-02-05", "FINISHED", "3", 1, "9.25");
        jdbc.update("update room set active = false where id = ?", inactive);

        for (String query : new String[] {"", "?roomId=" + winamax + "," + pokerStars}) {
            String summary = body("/bankroll/summary" + query);
            for (String groupBy : new String[] {"DAY", "WEEK", "MONTH", "YEAR"}) {
                String evolution = evolution("?groupBy=" + groupBy + query.replace('?', '&'));
                List<Map<String, Object>> currencies = JsonPath.read(summary, "$.currencies");
                for (int c = 0; c < currencies.size(); c++) {
                    String code = JsonPath.read(summary, "$.currencies[%d].currencyCode".formatted(c));
                    String currency = "$.currencies[?(@.currencyCode == '%s')]".formatted(code);
                    assertThat(lastBankroll(evolution, currency + ".total"))
                            .as(code + " " + groupBy + query)
                            .isEqualByComparingTo(amount(summary, "$.currencies[%d].total.bankroll".formatted(c)));
                    List<Integer> roomIds = JsonPath.read(summary, "$.currencies[%d].rooms[*].room.id".formatted(c));
                    for (int r = 0; r < roomIds.size(); r++) {
                        BigDecimal expected = amount(summary, "$.currencies[%d].rooms[%d].figures.bankroll".formatted(c, r));
                        String room = currency + ".rooms[?(@.room.id == %d)].series".formatted(roomIds.get(r));
                        List<?> found = JsonPath.read(evolution, room);
                        if (found.isEmpty()) {
                            // Left out: nothing to show.
                            assertThat(expected).as("room " + roomIds.get(r)).isZero();
                        } else {
                            assertThat(lastBankroll(evolution, room)).as("room " + roomIds.get(r) + " " + groupBy)
                                    .isEqualByComparingTo(expected);
                        }
                    }
                }
            }
        }
    }

    @Test
    void rejectsAMissingOrUnknownGrouping() {
        assertThat(mvc.get().uri("/bankroll/evolution")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/bankroll/evolution?groupBy=ROOM")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/bankroll/evolution?groupBy=DAY&from=yesterday")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    /** The bankroll at the end of a series found by a filter path: of its last period, or its starting one. */
    private static BigDecimal lastBankroll(String json, String seriesPath) {
        List<Object> starting = JsonPath.read(json, seriesPath + ".startingBankroll");
        List<Object> bankrolls = JsonPath.read(json, seriesPath + ".periods[-1:].bankroll");
        assertThat(starting).as(seriesPath).hasSize(1);
        return new BigDecimal((bankrolls.isEmpty() ? starting.getFirst() : bankrolls.getFirst()).toString());
    }

    private static BigDecimal amount(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    private void movement(String occurredOn, String type, Long roomId, String currencyCode, String amount) {
        jdbc.update("""
                insert into bankroll_movement (occurred_on, type, room_id, currency_code, amount)
                values (?::date, ?, ?, ?, ?::numeric)
                """, occurredOn, type, roomId, currencyCode, amount);
    }

    private void game(long roomId, String playedOn, String status, String buyIn, int entries, String prize) {
        jdbc.update("""
                insert into game (played_on, room_id, game_type_code, status, buy_in, entries, prize)
                values (?::date, ?, 'TOURNAMENT', ?, ?::numeric, ?, ?::numeric)
                """, playedOn, roomId, status, buyIn, entries, prize);
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
}
