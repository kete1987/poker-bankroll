package io.github.kete1987.pokerbankroll.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the tables of the tags (migration V3). Statements run in auto-commit, so the rows created
 * by each test are deleted afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TagSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    long room;

    @BeforeEach
    void createRoom() {
        room = jdbc.queryForObject(
                "insert into room (name, currency_code) values ('Winamax', 'EUR') returning id", Long.class);
    }

    @AfterEach
    void deleteTestData() {
        jdbc.update("delete from game");
        jdbc.update("delete from tag");
        jdbc.update("delete from room");
    }

    @Test
    void tagNamesAreUniqueIgnoringCase() {
        insertTag("Challenge");
        assertThatThrownBy(() -> insertTag("CHALLENGE")).isInstanceOf(DataIntegrityViolationException.class);
        insertTag("Challenges");
    }

    @Test
    void aTagNameIsStrippedNotBlankAndAtMostFortyCharacters() {
        assertThatThrownBy(() -> insertTag("")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTag("   ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTag(" Friends")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTag("x".repeat(41))).isInstanceOf(DataIntegrityViolationException.class);
        insertTag("x".repeat(40));
    }

    @Test
    void aGameHasEachTagOnce() {
        long game = insertGame();
        long tag = insertTag("Series");
        tag(game, tag);
        assertThatThrownBy(() -> tag(game, tag)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tag(game, tag + 1000)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAGameOrATagDeletesWhatTiesThem() {
        long game = insertGame();
        long other = insertGame();
        long series = insertTag("Series");
        long friends = insertTag("Friends");
        tag(game, series);
        tag(game, friends);
        tag(other, series);

        jdbc.update("delete from game where id = ?", game);
        assertThat(jdbc.queryForObject("select count(*) from game_tag", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tag", Integer.class)).isEqualTo(2);

        jdbc.update("delete from tag where id = ?", series);
        assertThat(jdbc.queryForObject("select count(*) from game_tag", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
    }

    private long insertTag(String name) {
        return jdbc.queryForObject("insert into tag (name) values (?) returning id", Long.class, name);
    }

    private long insertGame() {
        return jdbc.queryForObject("""
                insert into game (played_on, room_id, game_type_code, buy_in)
                values ('2026-01-19', ?, 'TOURNAMENT', 1) returning id
                """, Long.class, room);
    }

    private void tag(long game, long tag) {
        jdbc.update("insert into game_tag (game_id, tag_id) values (?, ?)", game, tag);
    }
}
