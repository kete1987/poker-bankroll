package io.github.kete1987.pokerbankroll.demo;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(count("game where played_on > current_date")).isZero();
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
