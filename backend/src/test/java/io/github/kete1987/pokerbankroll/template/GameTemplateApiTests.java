package io.github.kete1987.pokerbankroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class GameTemplateApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    // ---- create ----

    @Test
    void createsATemplateWithOnlyTheRequiredFields() {
        MvcTestResult result = postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 2.50}""".formatted(winamax)).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = jdbc.queryForObject("select id from game_template", Long.class);
        assertThat(result).headers().hasValue("Location", "http://localhost/game-templates/" + id);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.label").isNull();
        json.extractingPath("$.room.id").isEqualTo((int) winamax);
        json.extractingPath("$.room.name").isEqualTo("Winamax");
        json.extractingPath("$.currencyCode").isEqualTo("EUR");
        json.extractingPath("$.gameType").isEqualTo("TOURNAMENT");
        json.extractingPath("$.modality").isEqualTo("NLHE");
        json.extractingPath("$.variant").isNull();
        json.extractingPath("$.name").isNull();
        json.extractingPath("$.buyIn").isEqualTo(2.5);
        json.extractingPath("$.usable").isEqualTo(true);
        json.extractingPath("$.createdAt").isNotNull();
    }

    @Test
    void createsATemplateWithEveryField() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");

        var json = assertThat(postJson("/game-templates", """
                {"label": " Evening Expresso ", "roomId": %d, "gameType": "SIT_AND_GO", "modality": "PLO",
                 "variantId": %d, "name": " Expresso ", "buyIn": 5}""".formatted(winamax, expresso)))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.label").isEqualTo("Evening Expresso");
        json.extractingPath("$.modality").isEqualTo("PLO");
        json.extractingPath("$.variant.id").isEqualTo((int) expresso);
        json.extractingPath("$.variant.code").isEqualTo("EXPRESSO");
        json.extractingPath("$.name").isEqualTo("Expresso");
        json.extractingPath("$.buyIn").isEqualTo(5);
    }

    @Test
    void aCashTableIsATemplateToo() {
        var json = assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "CASH", "buyIn": 10, "label": "NL10"}""".formatted(pokerStars)))
                .hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.gameType").isEqualTo("CASH");
        json.extractingPath("$.currencyCode").isEqualTo("USD");
    }

    @Test
    void blankTextsAreNoText() {
        var json = assertThat(postJson("/game-templates", """
                {"label": "  ", "roomId": %d, "gameType": "TOURNAMENT", "name": "", "buyIn": 1}"""
                .formatted(winamax))).hasStatus(HttpStatus.CREATED).bodyJson();

        json.extractingPath("$.label").isNull();
        json.extractingPath("$.name").isNull();
    }

    // ---- validation ----

    @Test
    void requiredFieldsAndLimitsAreChecked() {
        var json = assertThat(postJson("/game-templates", "{}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("roomId", "gameType", "buyIn");

        json = assertThat(postJson("/game-templates", """
                {"label": "%s", "roomId": %d, "gameType": "TOURNAMENT", "name": "%s", "buyIn": -1}"""
                .formatted("x".repeat(81), winamax, "x".repeat(151)))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.errors[?(@.field == 'label')].code").asArray().containsExactly("Size");
        json.extractingPath("$.errors[?(@.field == 'name')].code").asArray().containsExactly("Size");
        json.extractingPath("$.errors[?(@.field == 'buyIn')].code").asArray().containsExactly("DecimalMin");

        assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1.234}""".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].code").isEqualTo("Digits");
        assertThat(count()).isZero();
    }

    @Test
    void theRoomAndTheVariantMustExist() {
        assertThat(postJson("/game-templates", """
                {"roomId": 987654, "gameType": "TOURNAMENT", "buyIn": 1}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_ROOM");
        assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": 987654, "buyIn": 1}""".formatted(winamax)))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_VARIANT");
    }

    @Test
    void theVariantMustBeOfTheGameType() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");

        assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}""".formatted(winamax, expresso)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_GAME_TYPE_MISMATCH");
    }

    // ---- inactive rooms and variants ----

    @Test
    void noTemplateIsCreatedInAnInactiveRoomOrWithAnInactiveVariant() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        jdbc.update("update variant set active = false where id = ?", ko);
        long closed = insertRoom("Unibet", "EUR");
        jdbc.update("update room set active = false where id = ?", closed);

        assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1}""".formatted(closed)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("ROOM_INACTIVE");
        assertThat(postJson("/game-templates", """
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 1}""".formatted(winamax, ko)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("VARIANT_INACTIVE");
        assertThat(count()).isZero();
    }

    @Test
    void aTemplateKeepsTheInactiveRoomAndVariantItHasButIsNotUsable() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long id = create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 5}""".formatted(winamax, ko));
        jdbc.update("update room set active = false where id = ?", winamax);
        jdbc.update("update variant set active = false where id = ?", ko);

        assertThat(mvc.get().uri("/game-templates")).hasStatusOk()
                .bodyJson().extractingPath("$[0].usable").isEqualTo(false);
        // Renaming it is possible...
        var json = assertThat(putJson("/game-templates/" + id, """
                {"label": "Old KO", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 5}"""
                .formatted(winamax, ko))).hasStatusOk().bodyJson();
        json.extractingPath("$.label").isEqualTo("Old KO");
        json.extractingPath("$.usable").isEqualTo(false);
        // ...moving it to another inactive room or variant is not.
        long closed = insertRoom("Unibet", "EUR");
        jdbc.update("update room set active = false where id = ?", closed);
        assertThat(putJson("/game-templates/" + id, """
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 5}""".formatted(closed, ko)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("ROOM_INACTIVE");
        long mysteryKo = builtInVariantId("TOURNAMENT", "MYSTERY_KO");
        jdbc.update("update variant set active = false where id = ?", mysteryKo);
        assertThat(putJson("/game-templates/" + id, """
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 5}""".formatted(winamax, mysteryKo)))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("VARIANT_INACTIVE");
    }

    @Test
    void aTemplateWithAnInactiveVariantIsNotUsable() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");
        create("""
                {"roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d, "buyIn": 5}""".formatted(winamax, expresso));
        create("""
                {"roomId": %d, "gameType": "SIT_AND_GO", "buyIn": 5}""".formatted(pokerStars));
        jdbc.update("update variant set active = false where id = ?", expresso);

        var json = assertThat(mvc.get().uri("/game-templates")).hasStatusOk().bodyJson();
        json.extractingPath("$[?(@.room.name == 'Winamax')].usable").asArray().containsExactly(false);
        json.extractingPath("$[?(@.room.name == 'PokerStars')].usable").asArray().containsExactly(true);
    }

    // ---- list ----

    @Test
    void listsThemByLabelOrRoomNameThenTypeAndBuyIn() {
        create("""
                {"roomId": %d, "gameType": "CASH", "name": "W cash 10", "buyIn": 10}""".formatted(winamax));
        create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "name": "W 5", "buyIn": 5}""".formatted(winamax));
        create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "name": "W 2", "buyIn": 2}""".formatted(winamax));
        create("""
                {"label": "zebra", "roomId": %d, "gameType": "TOURNAMENT", "name": "Z", "buyIn": 1}"""
                .formatted(pokerStars));
        create("""
                {"label": "Main event", "roomId": %d, "gameType": "TOURNAMENT", "name": "M", "buyIn": 1}"""
                .formatted(winamax));
        create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "name": "P 1", "buyIn": 1}""".formatted(pokerStars));

        assertThat(mvc.get().uri("/game-templates")).hasStatusOk().bodyJson()
                .extractingPath("$[*].name").asArray()
                .containsExactly("M", "P 1", "W 2", "W 5", "W cash 10", "Z");
    }

    @Test
    void anEmptyListWhenThereAreNone() {
        assertThat(mvc.get().uri("/game-templates")).hasStatusOk().bodyJson().isEqualTo("[]");
    }

    // ---- update and delete ----

    @Test
    void updateReplacesEveryField() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long id = create("""
                {"label": "KO", "roomId": %d, "gameType": "TOURNAMENT", "modality": "PLO", "variantId": %d,
                 "name": "Kill The Fish", "buyIn": 5}""".formatted(winamax, ko));

        var json = assertThat(putJson("/game-templates/" + id, """
                {"roomId": %d, "gameType": "CASH", "buyIn": 25}""".formatted(pokerStars))).hasStatusOk().bodyJson();

        json.extractingPath("$.id").isEqualTo((int) id);
        json.extractingPath("$.label").isNull();
        json.extractingPath("$.room.name").isEqualTo("PokerStars");
        json.extractingPath("$.currencyCode").isEqualTo("USD");
        json.extractingPath("$.gameType").isEqualTo("CASH");
        json.extractingPath("$.modality").isEqualTo("NLHE");
        json.extractingPath("$.variant").isNull();
        json.extractingPath("$.name").isNull();
        json.extractingPath("$.buyIn").isEqualTo(25);
    }

    @Test
    void deletesATemplate() {
        long id = create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1}""".formatted(winamax));

        assertThat(mvc.delete().uri("/game-templates/" + id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count()).isZero();
        assertThat(mvc.delete().uri("/game-templates/" + id)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnknownTemplateIsNotFound() {
        assertThat(putJson("/game-templates/987654", """
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 1}""".formatted(winamax)))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    // ---- rooms and variants ----

    @Test
    void aTemplateDoesNotPutItsRoomOrVariantInUseAndGoesWithThem() {
        long turbo = insertCustomVariant("SIT_AND_GO", "Turbo");
        create("""
                {"roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d, "buyIn": 1}""".formatted(pokerStars, turbo));
        create("""
                {"roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d, "buyIn": 2}""".formatted(winamax, turbo));
        create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 3}""".formatted(winamax));

        assertThat(mvc.get().uri("/rooms/" + winamax)).hasStatusOk()
                .bodyJson().extractingPath("$.inUse").isEqualTo(false);
        assertThat(mvc.get().uri("/variants?gameType=SIT_AND_GO")).hasStatusOk()
                .bodyJson().extractingPath("$[?(@.name == 'Turbo')].inUse").asArray().containsExactly(false);

        assertThat(mvc.delete().uri("/variants/" + turbo)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForList("select buy_in::text from game_template order by buy_in", String.class))
                .containsExactly("3.00");

        assertThat(mvc.delete().uri("/rooms/" + winamax)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count()).isZero();
    }

    @Test
    void theCurrencyOfARoomWithOnlyTemplatesCanStillChange() {
        create("""
                {"roomId": %d, "gameType": "TOURNAMENT", "buyIn": 3}""".formatted(winamax));

        assertThat(putJson("/rooms/" + winamax, """
                {"name": "Winamax", "currencyCode": "USD"}""")).hasStatusOk();
        assertThat(mvc.get().uri("/game-templates")).hasStatusOk()
                .bodyJson().extractingPath("$[0].currencyCode").isEqualTo("USD");
    }

    private long create(String json) {
        MvcTestResult result = postJson("/game-templates", json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(body(result), "$.id")).longValue();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from game_template", Integer.class);
    }
}
