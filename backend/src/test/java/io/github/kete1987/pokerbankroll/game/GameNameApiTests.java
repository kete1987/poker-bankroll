package io.github.kete1987.pokerbankroll.game;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/** {@code GET /games/names}: the names suggested while the name of a game is typed. */
class GameNameApiTests extends ApiIntegrationTest {

    long winamax;

    @BeforeEach
    void createRoom() {
        winamax = insertRoom("Winamax", "EUR");
    }

    @Test
    void suggestsTheNamesContainingTheTextIgnoringCase() {
        insertNamedGame("Kill The Fish", "2026-01-19", "TOURNAMENT");
        insertNamedGame("The Big One", "2026-01-18", "TOURNAMENT");
        insertNamedGame("Monday Special", "2026-01-17", "TOURNAMENT");

        var json = assertThat(names("tHe")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].name").asArray().containsExactly("Kill The Fish", "The Big One");
        // Spaces around the text are not part of it.
        assertThat(names("  fish ")).bodyJson().extractingPath("$[*].name").asArray()
                .containsExactly("Kill The Fish");
    }

    @Test
    void searchesTheTextLiterally() {
        insertNamedGame("100% Bounty", "2026-01-19", "TOURNAMENT");
        insertNamedGame("1000 Bounty", "2026-01-18", "TOURNAMENT");
        insertNamedGame("Deep_Stack", "2026-01-17", "TOURNAMENT");
        insertNamedGame("Deep Stack", "2026-01-16", "TOURNAMENT");
        insertNamedGame("Back\\Slash", "2026-01-15", "TOURNAMENT");

        assertThat(names("0% b")).bodyJson().extractingPath("$[*].name").asArray().containsExactly("100% Bounty");
        assertThat(names("p_s")).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Deep_Stack");
        assertThat(names("k\\s")).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Back\\Slash");
        assertThat(names("%%")).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(names("__")).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void ordersByUseThenByMostRecentThenByName() {
        insertNamedGame("Sunday Rare", "2026-01-25", "TOURNAMENT");
        insertNamedGame("Sunday Beta", "2026-01-20", "TOURNAMENT");
        insertNamedGame("Sunday Alpha", "2026-01-20", "TOURNAMENT");
        insertNamedGame("Sunday Usual", "2026-01-10", "TOURNAMENT");
        insertNamedGame("Sunday Usual", "2026-01-03", "TOURNAMENT");

        var json = assertThat(names("sunday")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].name").asArray()
                .containsExactly("Sunday Usual", "Sunday Rare", "Sunday Alpha", "Sunday Beta");
        json.extractingPath("$[*].games").asArray().containsExactly(2, 1, 1, 1);
    }

    @Test
    void namesThatDifferInCaseOrSurroundingSpacesAreOneWrittenAsInTheMostRecentGame() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        insertNamedGame("kill the fish", "2026-01-10", "TOURNAMENT");
        // The most recent one: the latest date and, within it, the latest id.
        insertGame("KILL THE FISH", "2026-01-19", "TOURNAMENT", "NLHE", "2.50", null);
        insertGame(" Kill The Fish ", "2026-01-19", "TOURNAMENT", "PLO", "5.00", ko);

        var json = assertThat(names("fish")).hasStatusOk().bodyJson();

        json.extractingPath("$.length()").isEqualTo(1);
        json.extractingPath("$[0].name").isEqualTo("Kill The Fish");
        json.extractingPath("$[0].games").isEqualTo(3);
        json.extractingPath("$[0].gameType").isEqualTo("TOURNAMENT");
        json.extractingPath("$[0].modality").isEqualTo("PLO");
        json.extractingPath("$[0].buyIn").isEqualTo(5.0);
        json.extractingPath("$[0].variant.id").isEqualTo((int) ko);
        json.extractingPath("$[0].variant.code").isEqualTo("KO");
        json.extractingPath("$[0].variant.name").isNull();
    }

    @Test
    void carriesAUserDefinedVariantOrNone() {
        long turbo = insertCustomVariant("SIT_AND_GO", "Hyper Turbo");
        insertGame("Turbo Night", "2026-01-19", "SIT_AND_GO", "NLHE", "1.00", turbo);
        insertGame("Plain Night", "2026-01-18", "SIT_AND_GO", "NLHE", "1.00", null);

        var json = assertThat(names("night")).hasStatusOk().bodyJson();

        json.extractingPath("$[0].variant.code").isNull();
        json.extractingPath("$[0].variant.name").isEqualTo("Hyper Turbo");
        json.extractingPath("$[1].variant").isNull();
    }

    @Test
    void theGameTypeRestrictsTheNamesAndTheirFigures() {
        insertGame("Winamax Series", "2026-01-10", "TOURNAMENT", "NLHE", "10.00", null);
        insertGame("Winamax Series", "2026-01-19", "SIT_AND_GO", "PLO", "2.00", null);
        insertGame("Series Table", "2026-01-15", "CASH", "NLHE", "20.00", null);

        var all = assertThat(names("series")).hasStatusOk().bodyJson();
        all.extractingPath("$[*].name").asArray().containsExactly("Winamax Series", "Series Table");
        all.extractingPath("$[0].games").isEqualTo(2);
        all.extractingPath("$[0].gameType").isEqualTo("SIT_AND_GO");

        var tournaments = assertThat(names("series").param("gameType", "TOURNAMENT")).hasStatusOk().bodyJson();
        tournaments.extractingPath("$.length()").isEqualTo(1);
        tournaments.extractingPath("$[0].games").isEqualTo(1);
        tournaments.extractingPath("$[0].gameType").isEqualTo("TOURNAMENT");
        tournaments.extractingPath("$[0].modality").isEqualTo("NLHE");
        tournaments.extractingPath("$[0].buyIn").isEqualTo(10.0);

        assertThat(names("series").param("gameType", "BINGO")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void suggestsEightNamesUnlessAnotherLimitIsGiven() {
        for (int i = 10; i < 20; i++) {
            insertNamedGame("Daily " + i, "2026-01-" + i, "TOURNAMENT");
        }

        assertThat(names("daily")).bodyJson().extractingPath("$[*].name").asArray()
                .containsExactly("Daily 19", "Daily 18", "Daily 17", "Daily 16", "Daily 15", "Daily 14",
                        "Daily 13", "Daily 12");
        assertThat(names("daily").param("limit", "2")).bodyJson().extractingPath("$[*].name").asArray()
                .containsExactly("Daily 19", "Daily 18");
        assertThat(names("daily").param("limit", "20")).bodyJson().extractingPath("$.length()").isEqualTo(10);
    }

    @Test
    void rejectsALimitOutOfRange() {
        for (String limit : new String[] {"0", "21"}) {
            var json = assertThat(names("daily").param("limit", limit)).as(limit)
                    .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
            json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
            json.extractingPath("$.errors[*].field").asArray().containsExactly("limit");
        }
    }

    @Test
    void suggestsNothingForLessThanTwoCharacters() {
        insertNamedGame("Kill The Fish", "2026-01-19", "TOURNAMENT");

        assertThat(mvc.get().uri("/games/names")).hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
        for (String text : new String[] {"", "k", " k ", "   "}) {
            assertThat(names(text)).as("'%s'", text).hasStatusOk()
                    .bodyJson().extractingPath("$").asArray().isEmpty();
        }
        assertThat(names("ki")).bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void countsGamesInPlayAndIgnoresGamesWithoutAName() {
        insertGame(winamax, "TOURNAMENT", null);
        long inPlay = insertNamedGame("Night Owl", "2026-01-19", "TOURNAMENT");
        jdbc.update("update game set status = 'IN_PLAY' where id = ?", inPlay);
        insertNamedGame("Night Owl", "2026-01-12", "TOURNAMENT");

        var json = assertThat(names("ni")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].name").asArray().containsExactly("Night Owl");
        json.extractingPath("$[0].games").isEqualTo(2);
    }

    private MockMvcRequestBuilder names(String text) {
        return mvc.get().uri("/games/names").param("q", text);
    }

    private long insertNamedGame(String name, String playedOn, String gameType) {
        return insertGame(name, playedOn, gameType, "NLHE", "1.00", null);
    }

    private long insertGame(String name, String playedOn, String gameType, String modality, String buyIn,
            Long variantId) {
        return jdbc.queryForObject("""
                insert into game (name, played_on, room_id, game_type_code, modality_code, buy_in, variant_id)
                values (?, ?::date, ?, ?, ?, ?::numeric, ?) returning id
                """, Long.class, name, playedOn, winamax, gameType, modality, buyIn, variantId);
    }
}
