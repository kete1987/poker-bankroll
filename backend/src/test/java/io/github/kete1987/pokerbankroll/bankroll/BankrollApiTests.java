package io.github.kete1987.pokerbankroll.bankroll;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BankrollApiTests extends ApiIntegrationTest {

    @Autowired
    BankrollService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    // ---- movements ----

    @Test
    void recordsAMovementOfARoomInItsCurrency() {
        var result = postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "roomId": %d, "amount": 100, "notes": " initial "}"""
                .formatted(winamax)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = jdbc.queryForObject("select id from bankroll_movement", Long.class);
        assertThat(result).headers().hasValue("Location", "http://localhost/bankroll/movements/" + id);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.occurredOn").isEqualTo("2026-01-19");
        json.extractingPath("$.type").isEqualTo("DEPOSIT");
        json.extractingPath("$.room.id").isEqualTo((int) winamax);
        json.extractingPath("$.room.name").isEqualTo("Winamax");
        json.extractingPath("$.currencyCode").isEqualTo("EUR");
        json.extractingPath("$.amount").isEqualTo(100);
        json.extractingPath("$.signedAmount").isEqualTo(100.0);
        json.extractingPath("$.notes").isEqualTo("initial");
    }

    @Test
    void recordsAMovementWithoutARoomInItsOwnCurrency() {
        var json = assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 200}"""))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.room").isNull();
        json.extractingPath("$.currencyCode").isEqualTo("EUR");
    }

    @Test
    void aWithdrawalSubtractsAndAnAdjustmentCanBeNegative() {
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "WITHDRAWAL", "roomId": %d, "amount": 30}"""
                .formatted(winamax))).hasStatus(HttpStatus.CREATED).bodyJson()
                .extractingPath("$.signedAmount").isEqualTo(-30.0);
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "ADJUSTMENT", "roomId": %d, "amount": -4.5}"""
                .formatted(winamax))).hasStatus(HttpStatus.CREATED).bodyJson()
                .extractingPath("$.signedAmount").isEqualTo(-4.5);
    }

    @Test
    void requiresDateTypeAndAmount() {
        var json = assertThat(postJson("/bankroll/movements", "{\"roomId\": %d}".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("occurredOn", "type", "amount");
    }

    @Test
    void needsEitherARoomOrACurrency() {
        var none = assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "amount": 10}""")
                .header("Accept-Language", "es")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        none.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("roomId", "currencyCode");
        none.extractingPath("$.errors[*].code").asArray().containsOnly("RoomOrCurrency");
        none.extractingPath("$.errors[0].message").asString().startsWith("Indica una sala");

        var both = assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "roomId": %d, "currencyCode": "EUR", "amount": 10}"""
                .formatted(winamax))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        both.extractingPath("$.errors[*].field").asArray().containsExactly("currencyCode");
    }

    @Test
    void rejectsAZeroAmountAndANegativeOneUnlessItIsAnAdjustment() {
        for (String amount : new String[] {"0", "-5"}) {
            var json = assertThat(postJson("/bankroll/movements", """
                    {"occurredOn": "2026-01-19", "type": "DEPOSIT", "roomId": %d, "amount": %s}"""
                    .formatted(winamax, amount))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
            json.extractingPath("$.errors[*].field").asArray().containsExactly("amount");
            json.extractingPath("$.errors[*].code").asArray().containsExactly("MovementAmountSign");
        }
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "ADJUSTMENT", "roomId": %d, "amount": 0}"""
                .formatted(winamax))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "BONUS", "roomId": %d, "amount": 1.234}"""
                .formatted(winamax))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.errors[*].code").asArray().containsExactly("Digits");
    }

    @Test
    void rejectsAnUnknownRoomOrCurrency() {
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "roomId": 999999, "amount": 10}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_ROOM");
        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "DEPOSIT", "currencyCode": "XXX", "amount": 10}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_CURRENCY");
    }

    @Test
    void acceptsMovementsInAnInactiveRoom() {
        jdbc.update("update room set active = false where id = ?", winamax);

        assertThat(postJson("/bankroll/movements", """
                {"occurredOn": "2026-01-19", "type": "WITHDRAWAL", "roomId": %d, "amount": 10}"""
                .formatted(winamax))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void getsUpdatesAndDeletesAMovement() {
        long id = movement("2026-01-19", "DEPOSIT", winamax, null, "100");

        assertThat(mvc.get().uri("/bankroll/movements/" + id)).hasStatusOk().bodyJson()
                .extractingPath("$.amount").isEqualTo(100.0);

        // From a deposit of a room to a withdrawal of the bankroll as a whole.
        var updated = assertThat(putJson("/bankroll/movements/" + id, """
                {"occurredOn": "2026-02-01", "type": "WITHDRAWAL", "currencyCode": "USD", "amount": 40}"""))
                .hasStatusOk().bodyJson();
        updated.extractingPath("$.occurredOn").isEqualTo("2026-02-01");
        updated.extractingPath("$.room").isNull();
        updated.extractingPath("$.currencyCode").isEqualTo("USD");
        updated.extractingPath("$.signedAmount").isEqualTo(-40.0);
        updated.extractingPath("$.notes").isNull();

        // And back to a room: its own currency is dropped.
        assertThat(putJson("/bankroll/movements/" + id, """
                {"occurredOn": "2026-02-01", "type": "BONUS", "roomId": %d, "amount": 5}""".formatted(pokerStars)))
                .hasStatusOk().bodyJson().extractingPath("$.currencyCode").isEqualTo("USD");
        assertThat(jdbc.queryForObject("select currency_code from bankroll_movement where id = ?", String.class, id))
                .isNull();

        assertThat(mvc.delete().uri("/bankroll/movements/" + id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/bankroll/movements/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(putJson("/bankroll/movements/" + id, """
                {"occurredOn": "2026-02-01", "type": "BONUS", "roomId": %d, "amount": 5}""".formatted(pokerStars)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/bankroll/movements/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void listsMovementsNewestFirstWithFiltersAndPages() {
        long first = movement("2026-01-10", "DEPOSIT", winamax, null, "100");
        long second = movement("2026-01-20", "WITHDRAWAL", winamax, null, "20");
        long third = movement("2026-01-20", "BONUS", pokerStars, null, "5");
        long global = movement("2026-01-05", "DEPOSIT", null, "EUR", "200");

        assertThat(ids("")).isEqualTo("[%d,%d,%d,%d]".formatted(third, second, first, global));
        assertThat(ids("?roomId=" + winamax)).isEqualTo("[%d,%d]".formatted(second, first));
        assertThat(ids("?type=DEPOSIT")).isEqualTo("[%d,%d]".formatted(first, global));
        assertThat(ids("?from=2026-01-10&to=2026-01-19")).isEqualTo("[%d]".formatted(first));
        // The currency of the room, or the own one of a movement without a room.
        assertThat(ids("?currency=EUR")).isEqualTo("[%d,%d,%d]".formatted(second, first, global));
        assertThat(ids("?currency=USD")).isEqualTo("[%d]".formatted(third));
        assertThat(ids("?withoutRoom=true")).isEqualTo("[%d]".formatted(global));
        assertThat(ids("?withoutRoom=false")).isEqualTo("[%d,%d,%d]".formatted(third, second, first));

        var page = assertThat(mvc.get().uri("/bankroll/movements?page=1&size=3")).hasStatusOk().bodyJson();
        page.extractingPath("$.items[*].id").asArray().containsExactly((int) global);
        page.extractingPath("$.totalItems").isEqualTo(4);
        page.extractingPath("$.totalPages").isEqualTo(2);
        assertThat(mvc.get().uri("/bankroll/movements?size=0")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- summary ----

    @Test
    void withoutMovementsTheBankrollOfARoomIsItsResult() {
        game(winamax, "FINISHED", "5", 1, "0", "0");
        game(winamax, "FINISHED", "2", 1, "1", "0");

        String json = summary();

        String room = "$.currencies[0].rooms[0]";
        assertThat(JsonPath.<String>read(json, "$.currencies[0].currencyCode")).isEqualTo("EUR");
        assertThat(JsonPath.<String>read(json, room + ".room.name")).isEqualTo("Winamax");
        assertNumber(json, room + ".figures.deposited", "0");
        assertNumber(json, room + ".figures.gamesNet", "-6");
        assertNumber(json, room + ".figures.result", "-6");
        assertNumber(json, room + ".figures.bankroll", "-6");
        assertNumber(json, "$.currencies[0].total.bankroll", "-6");
    }

    @Test
    void theBankrollAddsMovementsToTheResult() {
        long unibet = insertRoom("Unibet", "EUR");
        movement("2026-01-01", "DEPOSIT", winamax, null, "100");
        movement("2026-01-15", "WITHDRAWAL", winamax, null, "20");
        movement("2026-01-16", "BONUS", winamax, null, "3.50");
        movement("2026-01-17", "ADJUSTMENT", winamax, null, "-1");
        movement("2026-01-17", "ADJUSTMENT", winamax, null, "0.25");
        movement("2026-01-01", "DEPOSIT", null, "EUR", "200");
        game(winamax, "FINISHED", "10", 2, "5", "20");
        game(winamax, "IN_PLAY", "4", 2, "0", "0");
        game(unibet, "FINISHED", "1", 1, "0", "0");

        String json = summary();

        // Rooms by name: Unibet, Winamax.
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].rooms[*].room.name"))
                .hasToString("[\"Unibet\",\"Winamax\"]");
        String room = "$.currencies[0].rooms[1].figures";
        assertNumber(json, room + ".deposited", "100");
        assertNumber(json, room + ".withdrawn", "20");
        assertNumber(json, room + ".bonuses", "3.5");
        assertNumber(json, room + ".adjustments", "-0.75");
        // 5 - 2 x 10 for the finished game and - 2 x 4 for the one in play.
        assertNumber(json, room + ".gamesNet", "-23");
        assertNumber(json, room + ".result", "-19.5");
        assertNumber(json, room + ".bankroll", "59.75");
        assertNumber(json, room + ".ticketsWon", "20");
        assertNumber(json, room + ".gamesInPlay", "1");
        assertNumber(json, room + ".investedInPlay", "8");

        assertNumber(json, "$.currencies[0].rooms[0].figures.bankroll", "-1");

        String withoutRoom = "$.currencies[0].withoutRoom";
        assertNumber(json, withoutRoom + ".deposited", "200");
        assertNumber(json, withoutRoom + ".gamesNet", "0");
        assertNumber(json, withoutRoom + ".bankroll", "200");

        String total = "$.currencies[0].total";
        assertNumber(json, total + ".deposited", "300");
        assertNumber(json, total + ".withdrawn", "20");
        assertNumber(json, total + ".gamesNet", "-24");
        assertNumber(json, total + ".result", "-20.5");
        assertNumber(json, total + ".bankroll", "258.75");
        assertNumber(json, total + ".gamesInPlay", "1");
    }

    @Test
    void currenciesAreNeverAddedUp() {
        movement("2026-01-01", "DEPOSIT", winamax, null, "100");
        movement("2026-01-01", "DEPOSIT", pokerStars, null, "50");
        movement("2026-01-01", "DEPOSIT", null, "USD", "7");

        String json = summary();

        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");
        assertNumber(json, "$.currencies[0].total.bankroll", "100");
        assertNumber(json, "$.currencies[0].withoutRoom.bankroll", "0");
        assertNumber(json, "$.currencies[1].total.bankroll", "57");
    }

    @Test
    void aCurrencyWithOnlyMovementsWithoutARoomHasNoRooms() {
        jdbc.update("delete from room");
        movement("2026-01-01", "DEPOSIT", null, "EUR", "200");

        String json = summary();

        assertThat(JsonPath.<Integer>read(json, "$.currencies.length()")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(json, "$.currencies[0].rooms.length()")).isZero();
        assertNumber(json, "$.currencies[0].total.bankroll", "200");
    }

    @Test
    void inactiveRoomsAreListedOnlyWhenTheyHaveHistory() {
        long empty = insertRoom("Unibet", "EUR");
        long withHistory = insertRoom("888poker", "EUR");
        game(withHistory, "FINISHED", "1", 1, "0", "0");
        jdbc.update("update room set active = false where id in (?, ?)", empty, withHistory);

        String json = summary();

        assertThat(JsonPath.<Object>read(json, "$.currencies[0].rooms[*].room.name"))
                .hasToString("[\"888poker\",\"Winamax\"]");
        assertThat(JsonPath.<Boolean>read(json, "$.currencies[0].rooms[0].active")).isFalse();
    }

    @Test
    void withDatesTheFiguresAreThoseOfThePeriod() {
        long unibet = insertRoom("Unibet", "EUR");
        movement("2025-12-01", "DEPOSIT", winamax, null, "100");
        movement("2026-01-10", "BONUS", winamax, null, "5");
        movement("2026-02-01", "WITHDRAWAL", winamax, null, "20");
        movement("2026-01-05", "DEPOSIT", null, "EUR", "200");
        // Both games are of 2026-01-19.
        game(winamax, "FINISHED", "10", 1, "4", "0");
        game(unibet, "FINISHED", "1", 1, "0", "0");
        jdbc.update("update room set active = false where id = ?", unibet);

        String january = body("/bankroll/summary?from=2026-01-01&to=2026-01-31");

        String rooms = "$.currencies[0].rooms";
        assertThat(JsonPath.<Object>read(january, rooms + "[*].room.name")).hasToString("[\"Unibet\",\"Winamax\"]");
        assertNumber(january, rooms + "[1].figures.deposited", "0");
        assertNumber(january, rooms + "[1].figures.withdrawn", "0");
        assertNumber(january, rooms + "[1].figures.bonuses", "5");
        assertNumber(january, rooms + "[1].figures.gamesNet", "-6");
        // What was won or lost in the period, and what the bankroll changed in it.
        assertNumber(january, rooms + "[1].figures.result", "-1");
        assertNumber(january, rooms + "[1].figures.bankroll", "-1");
        assertNumber(january, "$.currencies[0].withoutRoom.deposited", "200");
        assertNumber(january, "$.currencies[0].total.result", "-2");
        assertNumber(january, "$.currencies[0].total.bankroll", "198");

        // A period without activity still lists the same rooms, at zero.
        String march = body("/bankroll/summary?from=2026-03-01");
        assertThat(JsonPath.<Object>read(march, rooms + "[*].room.name")).hasToString("[\"Unibet\",\"Winamax\"]");
        assertNumber(march, "$.currencies[0].total.bankroll", "0");

        // An open end: everything up to a date.
        assertNumber(body("/bankroll/summary?to=2025-12-31"), "$.currencies[0].total.bankroll", "100");
        // Without dates, the bankroll as it is now.
        assertNumber(body("/bankroll/summary"), "$.currencies[0].total.bankroll", "278");
    }

    @Test
    void withRoomsOnlyTheyAreListedAndAddedUp() {
        long unibet = insertRoom("Unibet", "EUR");
        movement("2026-01-01", "DEPOSIT", winamax, null, "100");
        movement("2026-01-01", "DEPOSIT", unibet, null, "30");
        movement("2026-01-01", "DEPOSIT", pokerStars, null, "50");
        movement("2026-01-01", "DEPOSIT", null, "EUR", "200");
        game(winamax, "FINISHED", "10", 1, "4", "0");

        String one = body("/bankroll/summary?roomId=" + winamax);
        assertThat(JsonPath.<Integer>read(one, "$.currencies.length()")).isEqualTo(1);
        assertThat(JsonPath.<Object>read(one, "$.currencies[0].rooms[*].room.name")).hasToString("[\"Winamax\"]");
        // The movements without a room are of no room in particular.
        assertNumber(one, "$.currencies[0].withoutRoom.deposited", "0");
        assertNumber(one, "$.currencies[0].total.bankroll", "94");

        String two = body("/bankroll/summary?roomId=" + winamax + "," + unibet + "&from=2026-01-01&to=2026-01-01");
        assertThat(JsonPath.<Object>read(two, "$.currencies[0].rooms[*].room.name"))
                .hasToString("[\"Unibet\",\"Winamax\"]");
        assertNumber(two, "$.currencies[0].total.deposited", "130");
        assertNumber(two, "$.currencies[0].total.gamesNet", "0");

        // A blank value is no filter.
        assertNumber(body("/bankroll/summary?roomId="), "$.currencies[0].total.deposited", "330");
        assertThat(mvc.get().uri("/bankroll/summary?from=yesterday")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- rooms with movements ----

    @Test
    void aRoomWithMovementsIsInUse() {
        movement("2026-01-01", "DEPOSIT", winamax, null, "100");

        assertThat(mvc.get().uri("/rooms/" + winamax)).hasStatusOk().bodyJson()
                .extractingPath("$.inUse").isEqualTo(true);
        assertThat(mvc.get().uri("/rooms")).hasStatusOk().bodyJson()
                .extractingPath("$[?(@.inUse == true)].name").asArray().containsExactly("Winamax");
        assertThat(mvc.delete().uri("/rooms/" + winamax)).hasStatus(HttpStatus.CONFLICT).bodyJson()
                .extractingPath("$.code").isEqualTo("ROOM_IN_USE");
        assertThat(putJson("/rooms/" + winamax, "{\"name\": \"Winamax\", \"currencyCode\": \"USD\"}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson()
                .extractingPath("$.code").isEqualTo("ROOM_CURRENCY_LOCKED");
    }

    /**
     * The currency check of the room cannot see a movement that is not committed yet: the change
     * waits for it and is then rejected, instead of relabelling its amount.
     */
    @Test
    void aCurrencyChangeWaitsForAMovementBeingRecordedAndIsRejected() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Integer> currencyChange = new TransactionTemplate(transactionManager).execute(status -> {
                service.create(new MovementRequest(
                        LocalDate.parse("2026-01-19"), MovementType.DEPOSIT, winamax, null, new BigDecimal("100"), null));
                Future<Integer> change = executor.submit(() -> putJson("/rooms/" + winamax,
                        "{\"name\": \"Winamax\", \"currencyCode\": \"USD\"}").exchange().getResponse().getStatus());
                assertThatThrownBy(() -> change.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                return change;
            });

            assertThat(currencyChange.get(30, TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(jdbc.queryForObject("select currency_code from room where id = ?", String.class, winamax))
                .isEqualTo("EUR");
    }

    private long movement(String occurredOn, String type, Long roomId, String currencyCode, String amount) {
        return jdbc.queryForObject("""
                insert into bankroll_movement (occurred_on, type, room_id, currency_code, amount)
                values (?::date, ?, ?, ?, ?::numeric) returning id
                """, Long.class, occurredOn, type, roomId, currencyCode, amount);
    }

    private void game(long roomId, String status, String buyIn, int entries, String prize, String ticket) {
        jdbc.update("""
                insert into game (played_on, room_id, game_type_code, status, buy_in, entries, prize, ticket_prize_value)
                values ('2026-01-19', ?, 'TOURNAMENT', ?, ?::numeric, ?, ?::numeric, ?::numeric)
                """, roomId, status, buyIn, entries, prize, ticket);
    }

    private String ids(String query) {
        return JsonPath.read(body("/bankroll/movements" + query), "$.items[*].id").toString();
    }

    private String summary() {
        return body("/bankroll/summary");
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
