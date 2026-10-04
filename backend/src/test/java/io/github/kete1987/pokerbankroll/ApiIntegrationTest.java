package io.github.kete1987.pokerbankroll;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Base of the API tests: the whole application on PostgreSQL, called through MockMvc (paths are
 * relative to the {@code /api} context path). Rows created by a test are deleted afterwards and
 * the seeded variants are restored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @AfterEach
    void restoreDatabase() {
        jdbc.update("delete from game_template");
        jdbc.update("delete from game");
        jdbc.update("delete from bankroll_movement");
        jdbc.update("delete from room");
        jdbc.update("delete from variant where code is null");
        jdbc.update("update variant set active = true");
    }

    protected MockMvcRequestBuilder postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected MockMvcRequestBuilder putJson(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    protected long insertRoom(String name, String currency) {
        return jdbc.queryForObject(
                "insert into room (name, currency_code) values (?, ?) returning id", Long.class, name, currency);
    }

    protected long insertCustomVariant(String gameType, String name) {
        return jdbc.queryForObject(
                "insert into variant (game_type_code, name) values (?, ?) returning id", Long.class, gameType, name);
    }

    protected long builtInVariantId(String gameType, String code) {
        return jdbc.queryForObject(
                "select id from variant where game_type_code = ? and code = ?", Long.class, gameType, code);
    }

    /** Inserts a game of the given type with a buy-in of 1; {@code variantId} may be null. */
    protected long insertGame(long roomId, String gameType, Long variantId) {
        return jdbc.queryForObject("""
                insert into game (played_on, room_id, game_type_code, variant_id, buy_in)
                values ('2026-01-19', ?, ?, ?, 1) returning id
                """, Long.class, roomId, gameType, variantId);
    }
}
