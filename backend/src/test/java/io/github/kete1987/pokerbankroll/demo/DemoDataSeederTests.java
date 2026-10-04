package io.github.kete1987.pokerbankroll.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;

import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The application started with the {@code demo} profile on an empty database (its own container). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataSeederTests {

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void loadsAYearOfSampleDataOnAnEmptyDatabase() {
        assertThat(count("room")).isEqualTo(4);
        assertThat(count("room where not active")).isEqualTo(1);
        assertThat(count("variant where name is not null")).isEqualTo(1);
        assertThat(count("game")).isBetween(250, 700);
        assertThat(count("game where status = 'IN_PLAY'")).isEqualTo(3);
        // The seeder's "today" is the one of the JVM, which may not be the database's.
        assertThat(jdbc.queryForObject("select count(*) from game where played_on > ?", Integer.class,
                LocalDate.now())).isZero();
        assertThat(jdbc.queryForList("select distinct game_type_code from game order by 1", String.class))
                .containsExactly("CASH", "SIT_AND_GO", "TOURNAMENT");
        assertThat(jdbc.queryForList("select distinct modality_code from game order by 1", String.class))
                .containsExactly("NLHE", "PLO");
        assertThat(count("game where entries > 1")).isPositive();
        assertThat(count("game where bounty > 0")).isPositive();
        assertThat(count("game where ticket_prize_value > 0")).isPositive();
        assertThat(count("game where paid_with_ticket")).isPositive();
        assertThat(count("game where variant_id is null and game_type_code = 'TOURNAMENT'")).isPositive();
        assertThat(count("bankroll_movement")).isGreaterThan(10);
        assertThat(count("bankroll_movement where room_id is null")).isEqualTo(1);
        assertThat(jdbc.queryForList("select distinct type from bankroll_movement order by 1", String.class))
                .containsExactly("ADJUSTMENT", "BONUS", "DEPOSIT", "WITHDRAWAL");
        assertThat(count("game_template")).isEqualTo(4);
        assertThat(jdbc.queryForList("select distinct game_type_code from game_template order by 1", String.class))
                .containsExactly("CASH", "SIT_AND_GO", "TOURNAMENT");
    }

    @Test
    void oneTemplateIsOfTheClosedRoom() {
        assertThat(mvc.get().uri("/game-templates")).hasStatusOk().bodyJson()
                .extractingPath("$[?(@.usable == false)].room.name").asArray().containsExactly("Unibet");
    }

    @Test
    void theApiServesTheSampleData() {
        assertThat(mvc.get().uri("/stats/summary")).hasStatusOk().bodyJson()
                .extractingPath("$.currencies[*].currencyCode").asArray().containsExactly("EUR", "USD");
        assertThat(mvc.get().uri("/bankroll/summary")).hasStatusOk().bodyJson()
                .extractingPath("$.currencies[0].rooms[*].room.name").asArray()
                .containsExactly("888poker", "Unibet", "Winamax");
    }

    @Test
    void someRoomsHaveAnInventedLogo() {
        assertThat(jdbc.queryForList("""
                select r.name from room r join room_logo l on l.room_id = r.id
                where l.content_type = 'image/png' order by r.name
                """, String.class)).containsExactly("888poker", "Unibet", "Winamax");
        assertThat(mvc.get().uri("/rooms")).hasStatusOk().bodyJson()
                .extractingPath("$[?(@.name == 'PokerStars')].logoVersion").asArray().containsOnlyNulls();

        long winamax = jdbc.queryForObject("select id from room where name = 'Winamax'", Long.class);
        var logo = mvc.get().uri("/rooms/{id}/logo", winamax).exchange();
        assertThat(logo).hasStatusOk().hasContentType("image/png");
        // A 64x64 PNG: the signature, then the IHDR chunk starting with width and height.
        byte[] png = logo.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOfRange(png, 0, 8)).containsExactly(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        assertThat(Arrays.copyOfRange(png, 12, 24)).containsExactly('I', 'H', 'D', 'R', 0, 0, 0, 64, 0, 0, 0, 64);
    }

    @Test
    void doesNothingWhenTheDatabaseAlreadyHasData() {
        int games = count("game");

        seeder.run(null);

        assertThat(count("game")).isEqualTo(games);
        assertThat(count("room")).isEqualTo(4);
    }

    private int count(String from) {
        return jdbc.queryForObject("select count(*) from " + from, Integer.class);
    }
}
