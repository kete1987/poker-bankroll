package io.github.kete1987.pokerbankroll.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.List;

import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the schema created by the Flyway migrations: seed data, generated columns, constraints.
 * Statements run in auto-commit (a rejected statement would abort a surrounding transaction), so
 * the rows created by each test are deleted afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InitialSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestData() {
        jdbc.update("delete from game");
        jdbc.update("delete from room");
        jdbc.update("delete from variant where name is not null");
    }

    // ---- seed data ----

    @Test
    void seedsEuroAndDollar() {
        assertThat(jdbc.queryForList("select code from currency order by code", String.class))
                .containsExactly("EUR", "USD");
    }

    @Test
    void seedsGameTypesInDisplayOrder() {
        assertThat(jdbc.queryForList("select code from game_type order by sort_order", String.class))
                .containsExactly("TOURNAMENT", "SIT_AND_GO", "CASH");
    }

    @Test
    void seedsModalities() {
        assertThat(jdbc.queryForList("select code from modality order by sort_order", String.class))
                .containsExactly("NLHE", "PLO");
    }

    @Test
    void seedsVariantsPerGameType() {
        assertThat(variantCodes("TOURNAMENT"))
                .containsExactly("REGULAR", "KO", "SPACE_KO", "MYSTERY_KO");
        assertThat(variantCodes("SIT_AND_GO"))
                .containsExactly("REGULAR", "EXPRESSO", "EXPRESSO_NITRO", "DOUBLE_OR_NOTHING",
                        "DOUBLE_OR_NOTHING_DEMENTE", "TRIPLE_OR_NOTHING", "TRIPLE_OR_NOTHING_DEMENTE", "HEADS_UP");
        assertThat(variantCodes("CASH")).isEmpty();
    }

    @Test
    void seedsBaseCurrencySetting() {
        assertThat(jdbc.queryForObject("select value from app_setting where key = 'base_currency'", String.class))
                .isEqualTo("EUR");
    }

    // ---- game ----

    @Test
    void netIsComputedFromTheAmounts() {
        long room = insertRoom("Winamax", "EUR");
        long game = jdbc.queryForObject("""
                insert into game (played_on, room_id, game_type_code, buy_in, entries, prize, bounty, ticket_prize_value)
                values ('2026-01-19', ?, 'TOURNAMENT', 2.50, 3, 10.00, 1.25, 5.00) returning id
                """, Long.class, room);

        // 10.00 + 1.25 + 5.00 - 2.50 * 3
        assertThat(jdbc.queryForObject("select net from game where id = ?", BigDecimal.class, game))
                .isEqualByComparingTo("8.75");
    }

    @Test
    void netFollowsLaterChanges() {
        long room = insertRoom("Winamax", "EUR");
        long game = insertGame(room, "TOURNAMENT", "buy_in", "5.00");

        jdbc.update("update game set prize = 12.30 where id = ?", game);

        assertThat(jdbc.queryForObject("select net from game where id = ?", BigDecimal.class, game))
                .isEqualByComparingTo("7.30");
    }

    @Test
    void gameDefaults() {
        long room = insertRoom("Winamax", "EUR");
        long game = insertGame(room, "TOURNAMENT", "buy_in", "1.00");

        var row = jdbc.queryForMap("select * from game where id = ?", game);
        assertThat(row.get("modality_code")).isEqualTo("NLHE");
        assertThat(row.get("entries")).isEqualTo(1);
        assertThat((BigDecimal) row.get("prize")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("net")).isEqualByComparingTo("-1.00");
        assertThat(row.get("paid_with_ticket")).isEqualTo(false);
        assertThat(row.get("played_at")).isNull();
        assertThat(row.get("variant_id")).isNull();
    }

    @Test
    void timeOfDayIsOptionalAndKept() {
        long room = insertRoom("Winamax", "EUR");
        long game = jdbc.queryForObject("""
                insert into game (played_on, played_at, room_id, game_type_code, buy_in)
                values ('2026-01-19', '21:30', ?, 'TOURNAMENT', 1) returning id
                """, Long.class, room);

        assertThat(jdbc.queryForObject("select played_at from game where id = ?", Time.class, game))
                .hasToString("21:30:00");
    }

    @Test
    void anyTypeCanBePlayedInAnyModality() {
        long room = insertRoom("Winamax", "EUR");
        long variant = variantId("TOURNAMENT", "KO");

        long game = jdbc.queryForObject("""
                insert into game (played_on, room_id, game_type_code, modality_code, variant_id, buy_in)
                values ('2026-01-19', ?, 'TOURNAMENT', 'PLO', ?, 2) returning id
                """, Long.class, room, variant);

        assertThat(jdbc.queryForObject("select modality_code from game where id = ?", String.class, game))
                .isEqualTo("PLO");
    }

    @Test
    void rejectsNegativeAmounts() {
        long room = insertRoom("Winamax", "EUR");
        for (String column : List.of("buy_in", "prize", "bounty", "ticket_prize_value")) {
            assertThatThrownBy(() -> insertGame(room, "TOURNAMENT", column, "-0.01"))
                    .as(column)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void rejectsZeroEntries() {
        long room = insertRoom("Winamax", "EUR");
        assertThatThrownBy(() -> insertGame(room, "TOURNAMENT", "entries", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownTypeModalityAndRoom() {
        long room = insertRoom("Winamax", "EUR");
        assertThatThrownBy(() -> insertGame(room, "BINGO", "buy_in", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGame(room, "TOURNAMENT", "modality_code", "'STUD'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGame(room + 1000, "TOURNAMENT", "buy_in", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAVariantOfAnotherGameType() {
        long room = insertRoom("Winamax", "EUR");
        long expresso = variantId("SIT_AND_GO", "EXPRESSO");

        assertThatThrownBy(() -> insertGame(room, "TOURNAMENT", "variant_id", String.valueOf(expresso)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAVariantOfTheSameGameType() {
        long room = insertRoom("Winamax", "EUR");
        long expresso = variantId("SIT_AND_GO", "EXPRESSO");

        long game = insertGame(room, "SIT_AND_GO", "variant_id", String.valueOf(expresso));

        assertThat(jdbc.queryForObject("select variant_id from game where id = ?", Long.class, game))
                .isEqualTo(expresso);
    }

    @Test
    void cashGamesHaveNoEntriesBountiesOrTickets() {
        long room = insertRoom("Winamax", "EUR");
        assertThat(insertGame(room, "CASH", "prize", "3.10")).isPositive();

        for (String assignment : List.of("entries = 2", "bounty = 1", "ticket_prize_value = 1", "paid_with_ticket = true")) {
            long cash = insertGame(room, "CASH", "prize", "1");
            assertThatThrownBy(() -> jdbc.update("update game set " + assignment + " where id = ?", cash))
                    .as(assignment)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void ticketDescriptionNeedsATicketValue() {
        long room = insertRoom("Winamax", "EUR");
        assertThatThrownBy(() -> insertGame(room, "TOURNAMENT", "ticket_description", "'Ticket 5 EUR'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- room ----

    @Test
    void roomNamesAreUniqueIgnoringCase() {
        insertRoom("Winamax", "EUR");
        assertThatThrownBy(() -> insertRoom("WINAMAX", "USD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roomNeedsAKnownCurrencyAndAName() {
        assertThatThrownBy(() -> insertRoom("PokerStars", "GBP"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRoom("   ", "EUR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roomCurrencyCanChangeWhileItHasNoGames() {
        long room = insertRoom("PokerStars", "EUR");

        jdbc.update("update room set currency_code = 'USD' where id = ?", room);

        assertThat(jdbc.queryForObject("select currency_code from room where id = ?", String.class, room))
                .isEqualTo("USD");
    }

    @Test
    void roomCurrencyCannotChangeOnceItHasGames() {
        long room = insertRoom("Winamax", "EUR");
        insertGame(room, "TOURNAMENT", "buy_in", "1");

        assertThatThrownBy(() -> jdbc.update("update room set currency_code = 'USD' where id = ?", room))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cannot change because it has games");
        assertThat(jdbc.queryForObject("select currency_code from room where id = ?", String.class, room))
                .isEqualTo("EUR");
    }

    @Test
    void roomWithGamesCanStillBeRenamedOrDeactivated() {
        long room = insertRoom("Winamax", "EUR");
        insertGame(room, "TOURNAMENT", "buy_in", "1");

        jdbc.update("update room set name = 'Winamax.es', active = false, currency_code = 'EUR' where id = ?", room);

        assertThat(jdbc.queryForObject("select name from room where id = ?", String.class, room))
                .isEqualTo("Winamax.es");
    }

    // ---- variant ----

    @Test
    void variantHasEitherACodeOrAName() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into variant (game_type_code, code, name) values ('CASH', 'ZOOM', 'Zoom')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into variant (game_type_code) values ('CASH')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void customVariantNamesAreUniquePerGameTypeIgnoringCase() {
        jdbc.update("insert into variant (game_type_code, name) values ('CASH', 'Zoom')");
        jdbc.update("insert into variant (game_type_code, name) values ('TOURNAMENT', 'Zoom')");

        assertThatThrownBy(() -> jdbc.update("insert into variant (game_type_code, name) values ('CASH', 'ZOOM')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void knownVariantCodesAreUniquePerGameType() {
        assertThatThrownBy(() -> jdbc.update("insert into variant (game_type_code, code) values ('TOURNAMENT', 'KO')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- helpers ----

    private List<String> variantCodes(String gameType) {
        return jdbc.queryForList(
                "select code from variant where game_type_code = ? order by sort_order", String.class, gameType);
    }

    private long variantId(String gameType, String code) {
        return jdbc.queryForObject(
                "select id from variant where game_type_code = ? and code = ?", Long.class, gameType, code);
    }

    private long insertRoom(String name, String currency) {
        return jdbc.queryForObject(
                "insert into room (name, currency_code) values (?, ?) returning id", Long.class, name, currency);
    }

    /** Inserts a game with a buy-in of 1 (unless {@code column} is buy_in) and one extra column set. */
    private long insertGame(long room, String gameType, String column, String sqlValue) {
        String columns = column.equals("buy_in") ? "buy_in" : "buy_in, " + column;
        String values = column.equals("buy_in") ? sqlValue : "1, " + sqlValue;
        return jdbc.queryForObject(
                "insert into game (played_on, room_id, game_type_code, " + columns + ") "
                        + "values ('2026-01-19', ?, ?, " + values + ") returning id",
                Long.class, room, gameType);
    }
}
