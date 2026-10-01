package io.github.kete1987.pokerbankroll.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * The day and the start time of a game are local: they are stored as written, whatever the time
 * zone of the JVM, so the same database gives the same games to an API running in another zone.
 */
class GameTimeZoneApiTests extends ApiIntegrationTest {

    private final TimeZone original = TimeZone.getDefault();

    long winamax;

    @BeforeEach
    void createRoom() {
        winamax = insertRoom("Winamax", "EUR");
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Europe/Madrid", "America/New_York", "Asia/Tokyo", "Pacific/Kiritimati"})
    void theDayAndTheStartTimeAreStoredAsWritten(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));

        create("2026-01-19", "21:30", "Evening");
        create("2026-01-19", "00:30", "After midnight");

        assertThat(stored("Evening")).isEqualTo("2026-01-19 21:30:00");
        assertThat(stored("After midnight")).isEqualTo("2026-01-19 00:30:00");
    }

    @Test
    void aGameIsReadTheSameInAnotherTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"));
        create("2026-01-19", "21:30", "Evening");

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        var json = assertThat(mvc.get().uri("/games")).hasStatusOk().bodyJson();
        json.extractingPath("$.items[0].playedOn").isEqualTo("2026-01-19");
        json.extractingPath("$.items[0].playedAt").isEqualTo("21:30:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Europe/Madrid", "America/New_York"})
    void theGamesOfADayAreOrderedByTheirLocalTimeAroundMidnight(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        create("2026-01-19", "23:30", "Late");
        create("2026-01-19", "00:30", "After midnight");
        create("2026-01-19", "12:00", "Noon");

        var json = assertThat(mvc.get().uri("/games").param("sort", "playedOn,asc")).hasStatusOk().bodyJson();

        json.extractingPath("$.items[*].name").asArray().containsExactly("After midnight", "Noon", "Late");
    }

    private void create(String playedOn, String playedAt, String name) {
        assertThat(postJson("/games", """
                {"playedOn": "%s", "playedAt": "%s", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1,
                 "name": "%s"}""".formatted(playedOn, playedAt, winamax, name))).hasStatus(HttpStatus.CREATED);
    }

    /** Day and start time of a game as PostgreSQL has them, without any conversion by the driver. */
    private String stored(String name) {
        return jdbc.queryForObject(
                "select played_on::text || ' ' || played_at::text from game where name = ?", String.class, name);
    }
}
