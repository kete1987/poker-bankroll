package io.github.kete1987.pokerbankroll.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GameApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    // ---- create ----

    @Test
    void recordsATournamentWithOnlyTheRequiredFields() {
        var result = postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 2.50}"""
                .formatted(winamax)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = jdbc.queryForObject("select id from game", Long.class);
        assertThat(result).headers().hasValue("Location", "http://localhost/games/" + id);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.playedOn").isEqualTo("2026-01-19");
        json.extractingPath("$.playedAt").isNull();
        json.extractingPath("$.room.name").isEqualTo("Winamax");
        json.extractingPath("$.currencyCode").isEqualTo("EUR");
        json.extractingPath("$.gameType").isEqualTo("TOURNAMENT");
        json.extractingPath("$.modality").isEqualTo("NLHE");
        json.extractingPath("$.variant").isNull();
        json.extractingPath("$.entries").isEqualTo(1);
        json.extractingPath("$.prize").isEqualTo(0);
        json.extractingPath("$.paidWithTicket").isEqualTo(false);
        json.extractingPath("$.invested").isEqualTo(2.5);
        json.extractingPath("$.net").isEqualTo(-2.5);
    }

    @Test
    void recordsATournamentWithEveryField() {
        long ko = builtInVariantId("TOURNAMENT", "KO");

        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "modality": "PLO", "variantId": %d, "name": " Kill The Fish ", "buyIn": 2.50, "entries": 3,
                 "prize": 10, "bounty": 1.25, "notes": "final table"}""".formatted(winamax, ko)))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.playedAt").isEqualTo("21:30:00");
        json.extractingPath("$.modality").isEqualTo("PLO");
        json.extractingPath("$.variant.code").isEqualTo("KO");
        json.extractingPath("$.variant.name").isNull();
        json.extractingPath("$.name").isEqualTo("Kill The Fish");
        json.extractingPath("$.entries").isEqualTo(3);
        json.extractingPath("$.invested").isEqualTo(7.5);
        json.extractingPath("$.net").isEqualTo(3.75);
        json.extractingPath("$.notes").isEqualTo("final table");
    }

    @Test
    void aTicketWonIsInformativeAndAnEntryPaidWithATicketIsFree() {
        var satellite = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 10,
                 "ticketPrizeValue": 100, "ticketDescription": "Main Event"}""".formatted(winamax)))
                .hasStatus(HttpStatus.CREATED).bodyJson();
        satellite.extractingPath("$.net").isEqualTo(-10.0);
        satellite.extractingPath("$.ticketPrizeValue").isEqualTo(100);
        satellite.extractingPath("$.ticketDescription").isEqualTo("Main Event");

        var mainEvent = assertThat(postJson("/games", """
                {"playedOn": "2026-01-20", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 100,
                 "paidWithTicket": true, "prize": 120}""".formatted(winamax)))
                .hasStatus(HttpStatus.CREATED).bodyJson();
        mainEvent.extractingPath("$.invested").isEqualTo(0);
        mainEvent.extractingPath("$.net").isEqualTo(120.0);
    }

    @Test
    void recordsACashGame() {
        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2, "prize": 3.10}"""
                .formatted(pokerStars))).hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.currencyCode").isEqualTo("USD");
        json.extractingPath("$.net").isEqualTo(1.1);
    }

    @Test
    void requiresDateRoomTypeAndBuyIn() {
        var json = assertThat(postJson("/games", "{}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("playedOn", "roomId", "gameType", "buyIn");
        json.extractingPath("$.errors[*].code").asArray().containsOnly("NotNull");
    }

    @Test
    void rejectsInvalidAmountsAndEntries() {
        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": -1,
                 "entries": 0, "prize": 1.234, "bounty": -0.01}""".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.errors[?(@.field == 'buyIn')].code").asArray().containsExactly("DecimalMin");
        json.extractingPath("$.errors[?(@.field == 'entries')].code").asArray().containsExactly("Min");
        json.extractingPath("$.errors[?(@.field == 'prize')].code").asArray().containsExactly("Digits");
        json.extractingPath("$.errors[?(@.field == 'bounty')].code").asArray().containsExactly("DecimalMin");
    }

    @Test
    void cashGamesHaveOneEntryAndNoBountiesOrTickets() {
        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2, "entries": 2,
                 "bounty": 1, "ticketPrizeValue": 5, "paidWithTicket": true}""".formatted(winamax))
                .header("Accept-Language", "es"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("entries", "bounty", "ticketPrizeValue", "paidWithTicket");
        json.extractingPath("$.errors[*].code").asArray().containsOnly("CashGameFields");
        json.extractingPath("$.errors[0].message").asString().startsWith("No permitido en una partida de cash");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void ticketDescriptionNeedsATicketValue() {
        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 2,
                 "ticketDescription": "Main Event"}""".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.errors[*].field").asArray().containsExactly("ticketDescription");
        json.extractingPath("$.errors[0].code").isEqualTo("TicketDescriptionNeedsValue");
    }

    @Test
    void rejectsUnknownRoomAndVariant() {
        assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": 999999, "gameType": "TOURNAMENT", "buyIn": 1}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_ROOM");

        assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": 999999, "buyIn": 1}"""
                .formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_VARIANT");
    }

    @Test
    void rejectsAVariantOfAnotherGameType() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");

        assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}"""
                .formatted(winamax, expresso)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_GAME_TYPE_MISMATCH");
    }

    @Test
    void cannotRecordAGameInAnInactiveRoomOrWithAnInactiveVariant() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        jdbc.update("update variant set active = false where id = ?", ko);
        assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}"""
                .formatted(winamax, ko)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_INACTIVE");

        jdbc.update("update room set active = false where id = ?", winamax);
        var json = assertThat(postJson("/games", game("2026-01-19", "Game")))
                .hasStatus(HttpStatus.CONFLICT).bodyJson();
        json.extractingPath("$.code").isEqualTo("ROOM_INACTIVE");
        json.extractingPath("$.detail").asString().contains("Winamax");

        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void rejectsMalformedValues() {
        assertThat(postJson("/games", """
                {"playedOn": "19/01/2026", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1}"""
                .formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- read ----

    @Test
    void getsAGame() {
        long id = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1, "name": "Freeroll"}"""
                .formatted(winamax));

        assertThat(mvc.get().uri("/games/{id}", id)).hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Freeroll");
        assertThat(mvc.get().uri("/games/{id}", 999_999)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void listsNewestFirstAndWithinADayByTimeThenByCreation() {
        create(game("2026-01-18", "A older day"));
        create(game("2026-01-19", "B no time, first created"));
        create(game("2026-01-19", "C no time, created later"));
        create(game("2026-01-19", "D at 18:00").replace("}", ", \"playedAt\": \"18:00\"}"));
        create(game("2026-01-19", "E at 22:15").replace("}", ", \"playedAt\": \"22:15\"}"));

        var json = assertThat(mvc.get().uri("/games")).hasStatusOk().bodyJson();

        json.extractingPath("$.items[*].name").asArray().containsExactly(
                "E at 22:15", "D at 18:00", "C no time, created later", "B no time, first created", "A older day");
        json.extractingPath("$.totalItems").isEqualTo(5);
        json.extractingPath("$.page").isEqualTo(0);
        json.extractingPath("$.size").isEqualTo(50);
        json.extractingPath("$.totalPages").isEqualTo(1);
    }

    @Test
    void filtersByDateRange() {
        create(game("2026-01-10", "before"));
        create(game("2026-01-15", "first day"));
        create(game("2026-01-20", "last day"));
        create(game("2026-01-21", "after"));

        assertThat(mvc.get().uri("/games").param("from", "2026-01-15").param("to", "2026-01-20")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].name").asArray().containsExactly("last day", "first day");
        assertThat(mvc.get().uri("/games").param("from", "2026-01-21")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].name").asArray().containsExactly("after");
    }

    @Test
    void filtersByTypeModalityRoomVariantAndCurrency() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "buyIn": 1, "name": "ko nlhe eur"}""".formatted(winamax, ko));
        create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "modality": "PLO",
                 "buyIn": 1, "name": "plo eur"}""".formatted(winamax));
        create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 1, "name": "cash usd"}"""
                .formatted(pokerStars));

        assertThat(names("gameType", "CASH")).containsExactly("cash usd");
        assertThat(names("modality", "PLO")).containsExactly("plo eur");
        assertThat(names("roomId", String.valueOf(pokerStars))).containsExactly("cash usd");
        assertThat(names("variantId", String.valueOf(ko))).containsExactly("ko nlhe eur");
        assertThat(names("currency", "EUR")).containsExactlyInAnyOrder("ko nlhe eur", "plo eur");
        assertThat(mvc.get().uri("/games").param("gameType", "TOURNAMENT").param("modality", "NLHE")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].name").asArray().containsExactly("ko nlhe eur");
    }

    @Test
    void searchesTextInNameAndNotesIgnoringCaseAndLiterally() {
        create(game("2026-01-19", "Kill The Fish"));
        create(game("2026-01-19", "Freeroll").replace("}", ", \"notes\": \"killed at the bubble\"}"));
        create(game("2026-01-19", "100% bounty"));
        create(game("2026-01-19", "Other"));

        assertThat(names("q", "KILL")).containsExactlyInAnyOrder("Kill The Fish", "Freeroll");
        assertThat(names("q", "100%")).containsExactly("100% bounty");
        assertThat(names("q", "%")).containsExactly("100% bounty");
        assertThat(names("q", "_")).isEmpty();
    }

    @Test
    void paginates() {
        for (int day = 1; day <= 5; day++) {
            create(game("2026-01-0" + day, "game " + day));
        }

        var json = assertThat(mvc.get().uri("/games").param("size", "2").param("page", "1")).hasStatusOk().bodyJson();

        json.extractingPath("$.items[*].name").asArray().containsExactly("game 3", "game 2");
        json.extractingPath("$.page").isEqualTo(1);
        json.extractingPath("$.size").isEqualTo(2);
        json.extractingPath("$.totalItems").isEqualTo(5);
        json.extractingPath("$.totalPages").isEqualTo(3);
    }

    @Test
    void sortsByNet() {
        create(game("2026-01-19", "break even").replace("}", ", \"prize\": 1}"));
        create(game("2026-01-20", "big win").replace("}", ", \"prize\": 50}"));
        create(game("2026-01-21", "loss"));

        assertThat(mvc.get().uri("/games").param("sort", "net,desc")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("big win", "break even", "loss");
        assertThat(mvc.get().uri("/games").param("sort", "net,asc")).hasStatusOk()
                .bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("loss", "break even", "big win");
    }

    @Test
    void rejectsInvalidSortAndPaging() {
        assertThat(mvc.get().uri("/games").param("sort", "notes,desc")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");
        assertThat(mvc.get().uri("/games").param("sort", "net,sideways")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");

        var json = assertThat(mvc.get().uri("/games").param("size", "500").param("page", "-1"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("size", "page");

        assertThat(mvc.get().uri("/games").param("gameType", "BINGO")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- update ----

    @Test
    void updateReplacesEveryFieldAndRecomputesNet() {
        long id = create("""
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "buyIn": 5, "entries": 2, "bounty": 3, "name": "Old name", "notes": "old"}""".formatted(winamax));

        var json = assertThat(putJson("/games/" + id, """
                {"playedOn": "2026-01-20", "roomId": %d, "gameType": "CASH", "buyIn": 20, "prize": 31.50}"""
                .formatted(pokerStars))).hasStatusOk().bodyJson();

        json.extractingPath("$.playedOn").isEqualTo("2026-01-20");
        json.extractingPath("$.playedAt").isNull();
        json.extractingPath("$.room.name").isEqualTo("PokerStars");
        json.extractingPath("$.currencyCode").isEqualTo("USD");
        json.extractingPath("$.gameType").isEqualTo("CASH");
        json.extractingPath("$.entries").isEqualTo(1);
        json.extractingPath("$.bounty").isEqualTo(0);
        json.extractingPath("$.name").isNull();
        json.extractingPath("$.notes").isNull();
        json.extractingPath("$.net").isEqualTo(11.5);
        assertThat(jdbc.queryForObject("select net from game where id = ?", String.class, id)).isEqualTo("11.50");
    }

    @Test
    void aGameCanStillBeEditedAfterItsRoomAndVariantWereDeactivated() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long id = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}"""
                .formatted(winamax, ko));
        jdbc.update("update room set active = false where id = ?", winamax);
        jdbc.update("update variant set active = false where id = ?", ko);

        var json = assertThat(putJson("/games/" + id, """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "buyIn": 1, "prize": 7}""".formatted(winamax, ko))).hasStatusOk().bodyJson();

        json.extractingPath("$.net").isEqualTo(6.0);
        json.extractingPath("$.variant.code").isEqualTo("KO");
    }

    @Test
    void aGameCannotBeMovedToAnInactiveRoomOrVariant() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long id = create(game("2026-01-19", "Game"));
        jdbc.update("update room set active = false where id = ?", pokerStars);
        jdbc.update("update variant set active = false where id = ?", ko);

        assertThat(putJson("/games/" + id, """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1}"""
                .formatted(pokerStars)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ROOM_INACTIVE");
        assertThat(putJson("/games/" + id, """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}"""
                .formatted(winamax, ko)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_INACTIVE");
    }

    @Test
    void updateValidatesLikeCreate() {
        long id = create(game("2026-01-19", "Game"));

        assertThat(putJson("/games/" + id, """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 1, "entries": 3}"""
                .formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].code").isEqualTo("CashGameFields");
        assertThat(putJson("/games/999999", game("2026-01-19", "Game"))).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- delete ----

    @Test
    void deletesAGame() {
        long id = create(game("2026-01-19", "Game"));

        assertThat(mvc.delete().uri("/games/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/games/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/games/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    /** JSON of a 1 EUR tournament at Winamax on the given day. */
    private String game(String playedOn, String name) {
        return """
                {"playedOn": "%s", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1, "name": "%s"}"""
                .formatted(playedOn, winamax, name);
    }

    private long create(String json) {
        var result = postJson("/games", json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private java.util.List<String> names(String param, String value) {
        var result = mvc.get().uri("/games").param(param, value).exchange();
        assertThat(result).hasStatusOk();
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.items[*].name");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
