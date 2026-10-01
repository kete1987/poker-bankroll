package io.github.kete1987.pokerbankroll.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        json.extractingPath("$.status").isEqualTo("IN_PLAY");
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
        json.extractingPath("$.won").isEqualTo(11.25);
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
                 "bounty": 1, "ticketPrizeValue": 5, "ticketDescription": "Main Event", "paidWithTicket": true}"""
                .formatted(winamax))
                .header("Accept-Language", "es"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        // Every offending field at once, each reported a single time.
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder(
                "entries", "bounty", "ticketPrizeValue", "ticketDescription", "paidWithTicket");
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
        for (String sort : new String[] {"net,sideways", ",", ",,", "net,", ",desc", "net,desc,extra"}) {
            assertThat(mvc.get().uri("/games").param("sort", sort)).as(sort).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SORT");
        }
        // A cash game with only a ticket description is reported once, by the cash rule.
        assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2,
                 "ticketDescription": "Main Event"}""".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].code").asArray().containsExactly("CashGameFields");

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

    // ---- status: in play / finished ----

    @Test
    void aGameSentWithAResultIsRecordedFinished() {
        assertThat(postJson("/games", game("2026-01-19", "won").replace("}", ", \"prize\": 5}")))
                .hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
        assertThat(postJson("/games", game("2026-01-19", "bounty only").replace("}", ", \"bounty\": 1}")))
                .hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
        assertThat(postJson("/games", game("2026-01-19", "ticket").replace("}", ", \"ticketPrizeValue\": 10}")))
                .hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
    }

    @Test
    void aLostGameCanBeRecordedFinishedExplicitly() {
        var json = assertThat(postJson("/games", game("2026-01-19", "lost").replace("}", ", \"status\": \"FINISHED\"}")))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.status").isEqualTo("FINISHED");
        json.extractingPath("$.net").isEqualTo(-1.0);
    }

    @Test
    void aGameInPlayCannotCarryAResult() {
        var json = assertThat(postJson("/games", """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1, "status": "IN_PLAY",
                 "prize": 5, "bounty": 1, "ticketPrizeValue": 10, "ticketDescription": "Main Event"}"""
                .formatted(winamax))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("prize", "bounty", "ticketPrizeValue", "ticketDescription");
        json.extractingPath("$.errors[*].code").asArray().containsOnly("InPlayGameHasNoResult");
    }

    @Test
    void finishesAGameWithItsResult() {
        long id = create(game("2026-01-19", "Kill The Fish"));

        var json = assertThat(postJson("/games/" + id + "/finish", """
                {"prize": 2.38, "bounty": 1.38, "ticketPrizeValue": 5, "ticketDescription": " Ticket 5 "}"""))
                .hasStatusOk().bodyJson();

        json.extractingPath("$.status").isEqualTo("FINISHED");
        json.extractingPath("$.prize").isEqualTo(2.38);
        json.extractingPath("$.bounty").isEqualTo(1.38);
        json.extractingPath("$.ticketDescription").isEqualTo("Ticket 5");
        json.extractingPath("$.net").isEqualTo(2.76);
        json.extractingPath("$.name").isEqualTo("Kill The Fish");
    }

    @Test
    void finishesAGameWithNothingWon() {
        long noBody = create(game("2026-01-19", "no body"));
        long emptyBody = create(game("2026-01-19", "empty body"));

        var json = assertThat(mvc.post().uri("/games/{id}/finish", noBody)).hasStatusOk().bodyJson();
        json.extractingPath("$.status").isEqualTo("FINISHED");
        json.extractingPath("$.prize").isEqualTo(0);
        json.extractingPath("$.net").isEqualTo(-1.0);

        assertThat(postJson("/games/" + emptyBody + "/finish", "{}")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
    }

    @Test
    void onlyAGameInPlayCanBeFinishedReEnteredOrRebought() {
        long tournament = create(game("2026-01-19", "done").replace("}", ", \"status\": \"FINISHED\"}"));
        long cash = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2, "prize": 3}"""
                .formatted(winamax));

        assertThat(postJson("/games/" + tournament + "/finish", "{\"prize\": 5}")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_IN_PLAY");
        assertThat(mvc.post().uri("/games/{id}/re-entries", tournament)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_IN_PLAY");
        assertThat(postJson("/games/" + cash + "/rebuys", "{\"amount\": 2}")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("GAME_NOT_IN_PLAY");
        assertThat(mvc.post().uri("/games/{id}/finish", 999_999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void finishValidatesTheResult() {
        long id = create(game("2026-01-19", "Game"));

        var json = assertThat(postJson("/games/" + id + "/finish", """
                {"prize": -1, "bounty": 1.234, "ticketDescription": "Main Event"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("prize", "bounty", "ticketDescription");
        assertThat(jdbc.queryForObject("select status from game where id = ?", String.class, id)).isEqualTo("IN_PLAY");
    }

    @Test
    void aCashGameFinishesWithOnlyTheAmountYouLeaveWith() {
        long id = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2}""".formatted(winamax));

        assertThat(postJson("/games/" + id + "/finish", "{\"prize\": 3, \"bounty\": 1}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("CASH_GAME_RESULT");

        var json = assertThat(postJson("/games/" + id + "/finish", "{\"prize\": 3.10}")).hasStatusOk().bodyJson();
        json.extractingPath("$.status").isEqualTo("FINISHED");
        json.extractingPath("$.net").isEqualTo(1.1);
    }

    @Test
    void addsReEntriesToAGameInPlay() {
        long id = create(game("2026-01-19", "Game").replace("\"buyIn\": 1", "\"buyIn\": 2.50"));

        assertThat(mvc.post().uri("/games/{id}/re-entries", id)).hasStatusOk()
                .bodyJson().extractingPath("$.entries").isEqualTo(2);
        var json = assertThat(mvc.post().uri("/games/{id}/re-entries", id)).hasStatusOk().bodyJson();

        json.extractingPath("$.entries").isEqualTo(3);
        json.extractingPath("$.invested").isEqualTo(7.5);
        json.extractingPath("$.net").isEqualTo(-7.5);
        json.extractingPath("$.status").isEqualTo("IN_PLAY");
    }

    /** E.g. a double click: every accepted action must build on the latest state of the game. */
    @Test
    void simultaneousReEntriesAreAllCounted() throws Exception {
        long id = create(game("2026-01-19", "Game"));
        int requests = 8;

        try (var executor = Executors.newFixedThreadPool(requests)) {
            var start = new CountDownLatch(1);
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return mvc.post().uri("/games/{id}/re-entries", id).exchange().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        }

        assertThat(jdbc.queryForObject("select entries from game where id = ?", Integer.class, id))
                .isEqualTo(1 + requests);
    }

    /** A re-entry racing a finish is either counted before it or rejected after it, never lost. */
    @Test
    void reEntriesRacingAFinishAreCountedOrRejected() throws Exception {
        long id = create(game("2026-01-19", "Game"));
        int reEntries = 6;

        List<Future<Integer>> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(reEntries + 1)) {
            var start = new CountDownLatch(1);
            for (int i = 0; i < reEntries; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return mvc.post().uri("/games/{id}/re-entries", id).exchange().getResponse().getStatus();
                }));
            }
            Future<Integer> finish = executor.submit(() -> {
                start.await();
                return postJson("/games/" + id + "/finish", "{\"prize\": 10}").exchange().getResponse().getStatus();
            });
            start.countDown();
            assertThat(finish.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }

        int accepted = 0;
        for (Future<Integer> status : statuses) {
            assertThat(status.get()).isIn(200, 409);
            accepted += status.get() == 200 ? 1 : 0;
        }
        var row = jdbc.queryForMap("select status, entries, prize from game where id = ?", id);
        assertThat(row.get("status")).isEqualTo("FINISHED");
        assertThat(row.get("entries")).isEqualTo(1 + accepted);
        assertThat((java.math.BigDecimal) row.get("prize")).isEqualByComparingTo("10");
    }

    @Test
    void addsRebuysToACashGameInPlay() {
        long id = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2}""".formatted(winamax));

        var json = assertThat(postJson("/games/" + id + "/rebuys", "{\"amount\": 1.50}")).hasStatusOk().bodyJson();

        json.extractingPath("$.buyIn").isEqualTo(3.5);
        json.extractingPath("$.entries").isEqualTo(1);
        json.extractingPath("$.net").isEqualTo(-3.5);

        var invalid = assertThat(postJson("/games/" + id + "/rebuys", "{\"amount\": 0}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        invalid.extractingPath("$.errors[0].field").isEqualTo("amount");
        invalid.extractingPath("$.errors[0].code").isEqualTo("DecimalMin");
    }

    @Test
    void reEntriesAreForTournamentsAndRebuysForCashGames() {
        long tournament = create(game("2026-01-19", "Game"));
        long cash = create("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "CASH", "buyIn": 2}""".formatted(winamax));

        assertThat(mvc.post().uri("/games/{id}/re-entries", cash)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("RE_ENTRY_NOT_FOR_CASH_GAMES");
        assertThat(postJson("/games/" + tournament + "/rebuys", "{\"amount\": 2}")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("REBUY_ONLY_FOR_CASH_GAMES");
    }

    @Test
    void updateKeepsTheStatusUnlessAResultOrAStatusIsSent() {
        long inPlay = create(game("2026-01-19", "in play"));
        long lost = create(game("2026-01-19", "lost").replace("}", ", \"status\": \"FINISHED\"}"));

        // Editing without status or result keeps each status.
        assertThat(putJson("/games/" + inPlay, game("2026-01-20", "in play"))).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("IN_PLAY");
        assertThat(putJson("/games/" + lost, game("2026-01-20", "lost"))).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("FINISHED");

        // Adding a result finishes the game; an explicit status reopens it.
        assertThat(putJson("/games/" + inPlay, game("2026-01-20", "in play").replace("}", ", \"prize\": 3}")))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("FINISHED");
        var reopened = assertThat(putJson("/games/" + inPlay,
                game("2026-01-20", "in play").replace("}", ", \"status\": \"IN_PLAY\"}"))).hasStatusOk().bodyJson();
        reopened.extractingPath("$.status").isEqualTo("IN_PLAY");
        reopened.extractingPath("$.prize").isEqualTo(0);
    }

    @Test
    void filtersByStatus() {
        create(game("2026-01-19", "in play"));
        create(game("2026-01-19", "finished").replace("}", ", \"prize\": 5}"));

        assertThat(names("status", "IN_PLAY")).containsExactly("in play");
        assertThat(names("status", "FINISHED")).containsExactly("finished");
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
