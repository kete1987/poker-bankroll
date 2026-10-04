package io.github.kete1987.pokerbankroll.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The table of templates ({@code V4__game_templates.sql}). Statements run in auto-commit. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GameTemplateSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestData() {
        jdbc.update("delete from game_template");
        jdbc.update("delete from room");
        jdbc.update("delete from variant where name is not null");
    }

    @Test
    void defaults() {
        long room = insertRoom("Winamax");
        long template = insertTemplate(room, "TOURNAMENT", "buy_in", "2.50");

        Map<String, Object> row = jdbc.queryForMap("select * from game_template where id = ?", template);
        assertThat(row.get("modality_code")).isEqualTo("NLHE");
        assertThat(row.get("label")).isNull();
        assertThat(row.get("variant_id")).isNull();
        assertThat(row.get("game_name")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void rejectsANegativeBuyInAndBlankTexts() {
        long room = insertRoom("Winamax");
        assertThatThrownBy(() -> insertTemplate(room, "TOURNAMENT", "buy_in", "-0.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTemplate(room, "TOURNAMENT", "label", "' '"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTemplate(room, "TOURNAMENT", "game_name", "''"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownTypeModalityAndRoom() {
        long room = insertRoom("Winamax");
        assertThatThrownBy(() -> insertTemplate(room, "BINGO", "buy_in", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTemplate(room, "TOURNAMENT", "modality_code", "'STUD'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTemplate(room + 1000, "TOURNAMENT", "buy_in", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theVariantMustBeOfTheGameType() {
        long room = insertRoom("Winamax");
        long expresso = variantId("SIT_AND_GO", "EXPRESSO");

        assertThatThrownBy(() -> insertTemplate(room, "TOURNAMENT", "variant_id", String.valueOf(expresso)))
                .isInstanceOf(DataIntegrityViolationException.class);
        long template = insertTemplate(room, "SIT_AND_GO", "variant_id", String.valueOf(expresso));
        assertThat(jdbc.queryForObject("select variant_id from game_template where id = ?", Long.class, template))
                .isEqualTo(expresso);
    }

    @Test
    void goesWithItsRoomAndItsVariant() {
        long winamax = insertRoom("Winamax");
        long stars = insertRoom("PokerStars");
        long turbo = jdbc.queryForObject(
                "insert into variant (game_type_code, name) values ('SIT_AND_GO', 'Turbo') returning id", Long.class);
        insertTemplate(winamax, "TOURNAMENT", "buy_in", "1");
        insertTemplate(stars, "SIT_AND_GO", "variant_id", String.valueOf(turbo));
        insertTemplate(stars, "SIT_AND_GO", "buy_in", "2");

        jdbc.update("delete from variant where id = ?", turbo);
        assertThat(count()).isEqualTo(2);
        jdbc.update("delete from room where id = ?", winamax);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void doesNotLockTheCurrencyOfItsRoom() {
        long room = insertRoom("Winamax");
        insertTemplate(room, "TOURNAMENT", "buy_in", "1");

        jdbc.update("update room set currency_code = 'USD' where id = ?", room);

        assertThat(jdbc.queryForObject("select currency_code from room where id = ?", String.class, room))
                .isEqualTo("USD");
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from game_template", Integer.class);
    }

    private long variantId(String gameType, String code) {
        return jdbc.queryForObject(
                "select id from variant where game_type_code = ? and code = ?", Long.class, gameType, code);
    }

    private long insertRoom(String name) {
        return jdbc.queryForObject(
                "insert into room (name, currency_code) values (?, 'EUR') returning id", Long.class, name);
    }

    /** Inserts a template with a buy-in of 1 (unless {@code column} is buy_in) and one extra column set. */
    private long insertTemplate(long room, String gameType, String column, String sqlValue) {
        String columns = column.equals("buy_in") ? "buy_in" : "buy_in, " + column;
        String values = column.equals("buy_in") ? sqlValue : "1, " + sqlValue;
        return jdbc.queryForObject(
                "insert into game_template (room_id, game_type_code, " + columns + ") "
                        + "values (?, ?, " + values + ") returning id",
                Long.class, room, gameType);
    }
}
