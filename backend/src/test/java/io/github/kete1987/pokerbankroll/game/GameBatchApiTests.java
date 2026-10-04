package io.github.kete1987.pokerbankroll.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GameBatchApiTests extends ApiIntegrationTest {

    long winamax;

    @BeforeEach
    void createRoom() {
        winamax = insertRoom("Winamax", "EUR");
    }

    @Test
    void recordsEveryGameInTheOrderSent() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");

        var json = assertThat(postJson("/games/batch", batch(
                expresso("2026-01-19", expresso, "\"prize\": 10"),
                expresso("2026-01-19", expresso, "\"status\": \"FINISHED\", \"prize\": 0, \"notes\": \"x2\""),
                expresso("2026-01-19", expresso, "\"status\": \"FINISHED\""))))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.games.length()").isEqualTo(3);
        json.extractingPath("$.games[*].status").asArray().containsOnly("FINISHED");
        json.extractingPath("$.games[*].net").asArray().containsExactly(8.0, -2.0, -2.0);
        json.extractingPath("$.games[*].variant.code").asArray().containsOnly("EXPRESSO");
        json.extractingPath("$.games[1].notes").isEqualTo("x2");
        json.extractingPath("$.games[0].currencyCode").isEqualTo("EUR");

        // Ids follow the order sent, so the list keeps it for games of the same day without time.
        List<Long> ids = jdbc.queryForList("select id from game order by id", Long.class);
        List<Integer> nets = jdbc.queryForList("select net::int from game order by id", Integer.class);
        assertThat(ids).hasSize(3);
        assertThat(nets).containsExactly(8, -2, -2);
        assertThat(mvc.get().uri("/games?sort=playedOn,asc")).bodyJson()
                .extractingPath("$.items[*].net").asArray().containsExactly(8.0, -2.0, -2.0);
    }

    @Test
    void recordsGamesInPlay() {
        var json = assertThat(postJson("/games/batch", batch(
                tournament("\"name\": \"Daily\""), tournament("\"name\": \"Daily\""))))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.games[*].status").asArray().containsExactly("IN_PLAY", "IN_PLAY");
        json.extractingPath("$.games[*].name").asArray().containsOnly("Daily");
        assertThat(jdbc.queryForObject("select count(*) from game where status = 'IN_PLAY'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void anInvalidGameRecordsNoneAndIsNamedByItsPosition() {
        var json = assertThat(postJson("/games/batch", batch(
                tournament("\"prize\": 1"),
                tournament("\"prize\": 1"),
                tournament("\"prize\": 1"),
                tournament("\"prize\": -1, \"status\": \"IN_PLAY\""))))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[?(@.field == 'games[3].prize')].code").asArray()
                .containsExactlyInAnyOrder("DecimalMin", "InPlayGameHasNoResult");
        json.extractingPath("$.errors[*].field").asArray().containsOnly("games[3].prize");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void aBusinessErrorOfOneGameRecordsNoneAndSaysWhichOne() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        jdbc.update("update variant set active = false where id = ?", ko);

        var json = assertThat(postJson("/games/batch", batch(
                tournament("\"prize\": 1"),
                tournament("\"prize\": 1"),
                tournament("\"variantId\": " + ko))))
                .hasStatus(HttpStatus.CONFLICT).bodyJson();

        json.extractingPath("$.code").isEqualTo("VARIANT_INACTIVE");
        json.extractingPath("$.index").isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void cannotRecordGamesInAnInactiveRoom() {
        jdbc.update("update room set active = false where id = ?", winamax);

        var json = assertThat(postJson("/games/batch", batch(tournament(""))))
                .hasStatus(HttpStatus.CONFLICT).bodyJson();

        json.extractingPath("$.code").isEqualTo("ROOM_INACTIVE");
        json.extractingPath("$.index").isEqualTo(0);
        json.extractingPath("$.detail").asString().contains("Winamax");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void takesFromOneToFiftyGames() {
        var none = assertThat(postJson("/games/batch", batch())).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        none.extractingPath("$.errors[*].field").asArray().containsExactly("games");
        none.extractingPath("$.errors[*].code").asArray().containsExactly("Size");

        var missing = assertThat(postJson("/games/batch", "{}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        missing.extractingPath("$.errors[*].field").asArray().containsExactly("games");
        missing.extractingPath("$.errors[*].code").asArray().containsExactly("NotNull");

        var aNull = assertThat(postJson("/games/batch", "{\"games\": [" + tournament("") + ", null]}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        aNull.extractingPath("$.errors[*].field").asArray().containsExactly("games[1]");

        var tooMany = assertThat(postJson("/games/batch",
                batch(repeat(tournament(""), GameBatchRequest.MAX_GAMES + 1))))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        tooMany.extractingPath("$.errors[*].code").asArray().containsExactly("Size");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();

        assertThat(postJson("/games/batch", batch(repeat(tournament(""), GameBatchRequest.MAX_GAMES))))
                .hasStatus(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class))
                .isEqualTo(GameBatchRequest.MAX_GAMES);
    }

    private String expresso(String playedOn, long variantId, String extra) {
        return """
                {"playedOn": "%s", "roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d, "buyIn": 2, %s}"""
                .formatted(playedOn, winamax, variantId, extra);
    }

    private String tournament(String extra) {
        return """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 5%s}"""
                .formatted(winamax, extra.isEmpty() ? "" : ", " + extra);
    }

    private static String[] repeat(String game, int times) {
        return IntStream.range(0, times).mapToObj(i -> game).toArray(String[]::new);
    }

    private static String batch(String... games) {
        return "{\"games\": [" + String.join(", ", games) + "]}";
    }
}
