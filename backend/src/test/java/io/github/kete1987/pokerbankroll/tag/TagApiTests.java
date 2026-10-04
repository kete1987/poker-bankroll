package io.github.kete1987.pokerbankroll.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.game.GameRequest;
import io.github.kete1987.pokerbankroll.game.GameService;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TagApiTests extends ApiIntegrationTest {

    @Autowired
    GameService games;

    @Autowired
    PlatformTransactionManager transactionManager;

    long winamax;

    @BeforeEach
    void createRoom() {
        winamax = insertRoom("Winamax", "EUR");
    }

    // ---- tags of a game ----

    @Test
    void aGameIsGivenTagsByNameAndTheMissingOnesAreCreated() {
        var json = assertThat(postJson("/games", game("\"tags\": [\" Satellite \", \"challenge\", \"SATELLITE\"]")))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        // Stripped, once each ignoring case, by name.
        json.extractingPath("$.tags[*].name").asArray().containsExactly("challenge", "Satellite");
        assertThat(jdbc.queryForList("select name from tag order by name", String.class))
                .containsExactly("Satellite", "challenge");

        // An existing tag is used whatever the capitals: it keeps how it is written.
        assertThat(postJson("/games", game("\"tags\": [\"CHALLENGE\", \"Friends\"]")))
                .hasStatus(HttpStatus.CREATED).bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("challenge", "Friends");
        assertThat(jdbc.queryForObject("select count(*) from tag", Integer.class)).isEqualTo(3);
    }

    @Test
    void anUpdateReplacesTheTagsAndWithoutThemTheGameHasNone() {
        long id = createGame(game("\"tags\": [\"a\", \"b\"]"));

        assertThat(putJson("/games/" + id, game("\"tags\": [\"b\", \"c\"]"))).hasStatusOk().bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("b", "c");
        assertThat(mvc.get().uri("/games/" + id)).bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("b", "c");

        assertThat(putJson("/games/" + id, game(""))).hasStatusOk().bodyJson()
                .extractingPath("$.tags").asArray().isEmpty();
        // The tags stay, without games.
        assertThat(mvc.get().uri("/tags")).bodyJson().extractingPath("$[*].games").asArray().containsOnly(0);
    }

    @Test
    void theOtherActionsOnAGameKeepItsTags() {
        long id = createGame(game("\"tags\": [\"Series\"]"));

        assertThat(mvc.post().uri("/games/" + id + "/re-entries")).hasStatusOk().bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("Series");
        assertThat(mvc.post().uri("/games/" + id + "/finish")).hasStatusOk().bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("Series");
    }

    @Test
    void severalGamesAtOnceGetTheirTags() {
        assertThat(postJson("/games/batch", "{\"games\": [%s, %s]}".formatted(
                game("\"tags\": [\"Expresso session\"]"), game("\"tags\": [\"expresso SESSION\"]"))))
                .hasStatus(HttpStatus.CREATED).bodyJson()
                .extractingPath("$.games[*].tags[*].name").asArray()
                .containsExactly("Expresso session", "Expresso session");
        assertThat(jdbc.queryForObject("select count(*) from tag", Integer.class)).isEqualTo(1);
    }

    @Test
    void aGameHasAtMostTenTagsOfFortyCharactersWithoutSemicolons() {
        var json = assertThat(postJson("/games", game("\"tags\": [\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\","
                + "\"j\",\"k\"]"))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.errors[*].field").asArray().containsExactly("tags");
        json.extractingPath("$.errors[*].code").asArray().containsExactly("Size");

        String tooLong = "x".repeat(41);
        var names = assertThat(postJson("/games", game("\"tags\": [\"" + tooLong + "\", \"  \", \"a;b\", null, \"  "
                + "y".repeat(40) + "  \"]"))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        names.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("tags[0]", "tags[1]", "tags[2]", "tags[3]");
        names.extractingPath("$.errors[?(@.field == 'tags[0]')].code").asArray().containsExactly("TagName");
        names.extractingPath("$.errors[?(@.field == 'tags[0]')].message").asArray()
                .containsExactly("A tag has from 1 to 40 characters and no semicolons.");
        names.extractingPath("$.errors[?(@.field == 'tags[3]')].code").asArray().containsExactly("NotNull");
        assertThat(jdbc.queryForObject("select count(*) from tag", Integer.class)).isZero();
    }

    /**
     * A new tag created by two requests at once: the second waits for the first and uses its tag,
     * instead of failing on the name taken.
     */
    @Test
    void twoGamesCreatingTheSameTagAtOnceShareIt() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<Integer> other = new TransactionTemplate(transactionManager).execute(status -> {
                games.create(new GameRequest(LocalDate.parse("2026-01-19"), null, winamax, GameType.TOURNAMENT, null,
                        null, GameStatus.FINISHED, null, BigDecimal.ONE, null, null, null, null, null, null, null,
                        List.of("Brand new")));
                Future<Integer> second = executor.submit(() ->
                        postJson("/games", game("\"tags\": [\"BRAND NEW\"]")).exchange().getResponse().getStatus());
                assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                return second;
            });

            assertThat(other.get(30, TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(jdbc.queryForList("select name from tag", String.class)).containsExactly("Brand new");
        assertThat(jdbc.queryForObject("select count(*) from game_tag", Integer.class)).isEqualTo(2);
    }

    // ---- filter ----

    @Test
    void theGamesListSelectsTheGamesWithAnyOfTheTags() {
        long both = createGame(game("\"name\": \"both\", \"tags\": [\"a\", \"b\"]"));
        long onlyA = createGame(game("\"name\": \"a\", \"tags\": [\"a\"]"));
        long onlyC = createGame(game("\"name\": \"c\", \"tags\": [\"c\"]"));
        createGame(game("\"name\": \"none\""));
        long a = tagId("a");
        long b = tagId("b");
        long c = tagId("c");

        // A game with several of them is listed once.
        assertThat(ids("?tagId=" + a + "," + b)).containsExactlyInAnyOrder(both, onlyA);
        assertThat(totalItems("?tagId=" + a + "&tagId=" + b)).isEqualTo(2);
        assertThat(ids("?tagId=" + c + "&tagId=" + b)).containsExactlyInAnyOrder(both, onlyC);
        // With the other filters: AND.
        assertThat(ids("?tagId=" + a + "&q=both")).containsExactly(both);
        // Empty is no filter.
        assertThat(totalItems("?tagId=")).isEqualTo(4);
    }

    // ---- /tags ----

    @Test
    void listsEveryTagWithItsGamesByName() {
        createGame(game("\"tags\": [\"satellite\", \"Friends\"]"));
        createGame(game("\"tags\": [\"Satellite\"]"));
        createGame(game("\"tags\": [\"zebra\"]"));
        jdbc.update("delete from game_tag where tag_id = ?", tagId("zebra"));

        var json = assertThat(mvc.get().uri("/tags")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].name").asArray().containsExactly("Friends", "satellite", "zebra");
        json.extractingPath("$[*].games").asArray().containsExactly(1, 2, 0);
    }

    @Test
    void renamesATag() {
        long id = createGame(game("\"tags\": [\"chalenge\"]"));
        long tag = tagId("chalenge");

        assertThat(putJson("/tags/" + tag, "{\"name\": \"  Challenge \"}")).hasStatusOk().bodyJson()
                .isEqualTo("{\"id\": %d, \"name\": \"Challenge\", \"games\": 1}".formatted(tag));
        assertThat(mvc.get().uri("/games/" + id)).bodyJson()
                .extractingPath("$.tags[0].name").isEqualTo("Challenge");
        // Its own name in other capitals is a rename too.
        assertThat(putJson("/tags/" + tag, "{\"name\": \"CHALLENGE\"}")).hasStatusOk().bodyJson()
                .extractingPath("$.name").isEqualTo("CHALLENGE");
    }

    @Test
    void renamingATagToTheNameOfAnotherMergesThem() {
        long both = createGame(game("\"tags\": [\"Friends\", \"amigos\"]"));
        long onlyAmigos = createGame(game("\"tags\": [\"amigos\"]"));
        createGame(game("\"tags\": [\"Friends\"]"));
        long friends = tagId("Friends");
        long amigos = tagId("amigos");

        assertThat(putJson("/tags/" + amigos, "{\"name\": \"friends\"}")).hasStatusOk().bodyJson()
                .isEqualTo("{\"id\": %d, \"name\": \"Friends\", \"games\": 3}".formatted(friends));

        assertThat(jdbc.queryForList("select name from tag", String.class)).containsExactly("Friends");
        for (long game : List.of(both, onlyAmigos)) {
            assertThat(mvc.get().uri("/games/" + game)).bodyJson()
                    .extractingPath("$.tags[*].name").asArray().containsExactly("Friends");
        }
    }

    @Test
    void deletingATagRemovesItFromItsGames() {
        long id = createGame(game("\"tags\": [\"a\", \"b\"]"));

        assertThat(mvc.delete().uri("/tags/" + tagId("a"))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/games/" + id)).bodyJson()
                .extractingPath("$.tags[*].name").asArray().containsExactly("b");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
    }

    @Test
    void anUnknownTagIsNotFoundAndANameIsRequired() {
        assertThat(putJson("/tags/999999", "{\"name\": \"x\"}")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(mvc.delete().uri("/tags/999999")).hasStatus(HttpStatus.NOT_FOUND);

        createGame(game("\"tags\": [\"a\"]"));
        long tag = tagId("a");
        assertThat(putJson("/tags/" + tag, "{}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.errors[*].code").asArray().containsExactly("NotNull");
        assertThat(putJson("/tags/" + tag, "{\"name\": \" \"}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.errors[*].code").asArray().containsExactly("TagName");
        assertThat(putJson("/tags/" + tag, "{\"name\": \"x;y\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deletingAGameKeepsItsTags() {
        long id = createGame(game("\"tags\": [\"a\"]"));

        assertThat(mvc.delete().uri("/games/" + id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/tags")).bodyJson().extractingPath("$[*].games").asArray().containsExactly(0);
    }

    // ---- helpers ----

    private String game(String extra) {
        return """
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1%s}"""
                .formatted(winamax, extra.isEmpty() ? "" : ", " + extra);
    }

    private long createGame(String json) {
        MvcTestResult result = postJson("/games", json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private long tagId(String name) {
        return jdbc.queryForObject("select id from tag where name = ?", Long.class, name);
    }

    private List<Long> ids(String query) {
        List<Number> ids = JsonPath.read(body(mvc.get().uri("/games" + query).exchange()), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private int totalItems(String query) {
        return JsonPath.read(body(mvc.get().uri("/games" + query).exchange()), "$.totalItems");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
