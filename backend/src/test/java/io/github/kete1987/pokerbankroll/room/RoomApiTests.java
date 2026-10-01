package io.github.kete1987.pokerbankroll.room;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RoomApiTests extends ApiIntegrationTest {

    // ---- create ----

    @Test
    void createsARoom() {
        var result = postJson("/rooms", """
                {"name": "  Winamax ", "currencyCode": "EUR"}""").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = jdbc.queryForObject("select id from room where name = 'Winamax'", Long.class);
        assertThat(result).headers().hasValue("Location", "http://localhost/rooms/" + id);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.name").isEqualTo("Winamax");
        json.extractingPath("$.currencyCode").isEqualTo("EUR");
        json.extractingPath("$.active").isEqualTo(true);
        json.extractingPath("$.inUse").isEqualTo(false);
        json.extractingPath("$.createdAt").isNotNull();
    }

    @Test
    void rejectsANameAlreadyTakenIgnoringCase() {
        insertRoom("Winamax", "EUR");

        var json = assertThat(postJson("/rooms", """
                {"name": "WINAMAX", "currencyCode": "USD"}"""))
                .hasStatus(HttpStatus.CONFLICT).bodyJson();

        json.extractingPath("$.code").isEqualTo("ROOM_NAME_TAKEN");
        json.extractingPath("$.detail").isEqualTo("There is already a room called WINAMAX.");
    }

    @Test
    void conflictMessagesAreLocalized() {
        insertRoom("Winamax", "EUR");

        assertThat(postJson("/rooms", """
                {"name": "Winamax", "currencyCode": "EUR"}""").header("Accept-Language", "es"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Ya existe una sala llamada Winamax.");
    }

    @Test
    void rejectsAnUnknownCurrency() {
        assertThat(postJson("/rooms", """
                {"name": "PokerStars", "currencyCode": "GBP"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_CURRENCY");
    }

    @Test
    void rejectsInvalidData() {
        var json = assertThat(postJson("/rooms", """
                {"name": "   ", "currencyCode": "eur"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("name", "currencyCode");
        json.extractingPath("$.errors[?(@.field == 'name')].code").asArray().containsExactly("NotBlank");
        json.extractingPath("$.errors[?(@.field == 'currencyCode')].code").asArray().containsExactly("Pattern");
    }

    // ---- read ----

    @Test
    void listsRoomsByNameTellingWhichAreInUse() {
        long winamax = insertRoom("winamax", "EUR");
        insertRoom("888poker", "EUR");
        insertRoom("PokerStars", "USD");
        insertGame(winamax, "TOURNAMENT", null);

        var json = assertThat(mvc.get().uri("/rooms")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].name").asArray().containsExactly("888poker", "PokerStars", "winamax");
        json.extractingPath("$[*].inUse").asArray().containsExactly(false, false, true);
        json.extractingPath("$[1].currencyCode").isEqualTo("USD");
    }

    @Test
    void filtersRoomsByActive() {
        insertRoom("Winamax", "EUR");
        long old = insertRoom("Old room", "EUR");
        jdbc.update("update room set active = false where id = ?", old);

        assertThat(mvc.get().uri("/rooms").param("active", "true")).hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Winamax");
        assertThat(mvc.get().uri("/rooms").param("active", "false")).hasStatusOk()
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Old room");
    }

    @Test
    void getsARoom() {
        long id = insertRoom("Winamax", "EUR");

        assertThat(mvc.get().uri("/rooms/{id}", id)).hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Winamax");
    }

    @Test
    void unknownRoomIsNotFound() {
        assertThat(mvc.get().uri("/rooms/{id}", 999_999)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(putJson("/rooms/999999", """
                {"name": "X", "currencyCode": "EUR"}""")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/rooms/{id}", 999_999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- update ----

    @Test
    void renamesAndDeactivatesARoom() {
        long id = insertRoom("Winamax", "EUR");

        var json = assertThat(putJson("/rooms/" + id, """
                {"name": "Winamax.es", "currencyCode": "EUR", "active": false}"""))
                .hasStatusOk().bodyJson();

        json.extractingPath("$.name").isEqualTo("Winamax.es");
        json.extractingPath("$.active").isEqualTo(false);
        assertThat(jdbc.queryForObject("select active from room where id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void keepsActiveWhenOmittedAndAllowsChangingOnlyTheCaseOfTheName() {
        long id = insertRoom("winamax", "EUR");
        jdbc.update("update room set active = false where id = ?", id);

        var json = assertThat(putJson("/rooms/" + id, """
                {"name": "Winamax", "currencyCode": "EUR"}""")).hasStatusOk().bodyJson();

        json.extractingPath("$.name").isEqualTo("Winamax");
        json.extractingPath("$.active").isEqualTo(false);
    }

    @Test
    void cannotRenameToTheNameOfAnotherRoom() {
        insertRoom("Winamax", "EUR");
        long other = insertRoom("888poker", "EUR");

        assertThat(putJson("/rooms/" + other, """
                {"name": "winamax", "currencyCode": "EUR"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ROOM_NAME_TAKEN");
    }

    @Test
    void currencyCanChangeWhileTheRoomHasNoGames() {
        long id = insertRoom("PokerStars", "EUR");

        assertThat(putJson("/rooms/" + id, """
                {"name": "PokerStars", "currencyCode": "USD"}"""))
                .hasStatusOk().bodyJson().extractingPath("$.currencyCode").isEqualTo("USD");
    }

    @Test
    void currencyIsLockedOnceTheRoomHasGames() {
        long id = insertRoom("Winamax", "EUR");
        insertGame(id, "TOURNAMENT", null);

        assertThat(putJson("/rooms/" + id, """
                {"name": "Winamax", "currencyCode": "USD"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ROOM_CURRENCY_LOCKED");
        assertThat(jdbc.queryForObject("select currency_code from room where id = ?", String.class, id))
                .isEqualTo("EUR");

        // Other changes are still possible.
        assertThat(putJson("/rooms/" + id, """
                {"name": "Winamax.es", "currencyCode": "EUR"}"""))
                .hasStatusOk().bodyJson().extractingPath("$.inUse").isEqualTo(true);
    }

    // ---- delete ----

    @Test
    void deletesARoomWithoutGames() {
        long id = insertRoom("Winamax", "EUR");

        assertThat(mvc.delete().uri("/rooms/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void cannotDeleteARoomWithGames() {
        long id = insertRoom("Winamax", "EUR");
        insertGame(id, "TOURNAMENT", null);

        assertThat(mvc.delete().uri("/rooms/{id}", id))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ROOM_IN_USE");
        assertThat(jdbc.queryForObject("select count(*) from room where id = ?", Integer.class, id)).isOne();
    }
}
