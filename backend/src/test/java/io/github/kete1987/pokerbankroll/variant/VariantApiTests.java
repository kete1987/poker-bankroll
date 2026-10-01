package io.github.kete1987.pokerbankroll.variant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class VariantApiTests extends ApiIntegrationTest {

    // ---- read ----

    @Test
    void listsBuiltInVariantsFirstThenUserDefinedByName() {
        insertCustomVariant("TOURNAMENT", "turbo");
        insertCustomVariant("TOURNAMENT", "Satellite");

        var json = assertThat(mvc.get().uri("/variants").param("gameType", "TOURNAMENT")).hasStatusOk().bodyJson();

        json.extractingPath("$[*].code").asArray()
                .containsExactly("REGULAR", "KO", "SPACE_KO", "MYSTERY_KO", null, null);
        json.extractingPath("$[*].name").asArray()
                .containsExactly(null, null, null, null, "Satellite", "turbo");
        json.extractingPath("$[*].builtIn").asArray().containsExactly(true, true, true, true, false, false);
    }

    @Test
    void listsAllGameTypesInOrderWhenNotFiltered() {
        var json = assertThat(mvc.get().uri("/variants")).hasStatusOk().bodyJson();

        json.extractingPath("$.length()").isEqualTo(12);
        json.extractingPath("$[0].gameType").isEqualTo("TOURNAMENT");
        json.extractingPath("$[4].gameType").isEqualTo("SIT_AND_GO");
        json.extractingPath("$[5].code").isEqualTo("EXPRESSO");
    }

    @Test
    void tellsWhichVariantsAreInUseAndFiltersByActive() {
        long room = insertRoom("Winamax", "EUR");
        long ko = builtInVariantId("TOURNAMENT", "KO");
        insertGame(room, "TOURNAMENT", ko);
        jdbc.update("update variant set active = false where id = ?", builtInVariantId("TOURNAMENT", "MYSTERY_KO"));

        var active = assertThat(mvc.get().uri("/variants").param("gameType", "TOURNAMENT").param("active", "true"))
                .hasStatusOk().bodyJson();

        active.extractingPath("$[*].code").asArray().containsExactly("REGULAR", "KO", "SPACE_KO");
        active.extractingPath("$[*].inUse").asArray().containsExactly(false, true, false);
    }

    @Test
    void rejectsAnUnknownGameTypeFilter() {
        assertThat(mvc.get().uri("/variants").param("gameType", "BINGO"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- create ----

    @Test
    void createsAUserDefinedVariant() {
        var result = postJson("/variants", """
                {"gameType": "CASH", "name": " Zoom "}""").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long id = jdbc.queryForObject("select id from variant where name = 'Zoom'", Long.class);
        assertThat(result).headers().hasValue("Location", "http://localhost/variants/" + id);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.gameType").isEqualTo("CASH");
        json.extractingPath("$.name").isEqualTo("Zoom");
        json.extractingPath("$.code").isNull();
        json.extractingPath("$.builtIn").isEqualTo(false);
        json.extractingPath("$.active").isEqualTo(true);
        json.extractingPath("$.inUse").isEqualTo(false);
    }

    @Test
    void namesAreUniquePerGameTypeIgnoringCase() {
        insertCustomVariant("CASH", "Zoom");

        var json = assertThat(postJson("/variants", """
                {"gameType": "CASH", "name": "ZOOM"}""")).hasStatus(HttpStatus.CONFLICT).bodyJson();
        json.extractingPath("$.code").isEqualTo("VARIANT_NAME_TAKEN");

        assertThat(postJson("/variants", """
                {"gameType": "TOURNAMENT", "name": "Zoom"}""")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void rejectsInvalidData() {
        var json = assertThat(postJson("/variants", """
                {"name": ""}""")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("gameType", "name");

        assertThat(postJson("/variants", """
                {"gameType": "BINGO", "name": "Zoom"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- update ----

    @Test
    void renamesAndDeactivatesAUserDefinedVariant() {
        long id = insertCustomVariant("CASH", "Zoom");

        var json = assertThat(putJson("/variants/" + id, """
                {"name": "Fast fold", "active": false}""")).hasStatusOk().bodyJson();

        json.extractingPath("$.name").isEqualTo("Fast fold");
        json.extractingPath("$.active").isEqualTo(false);
        json.extractingPath("$.gameType").isEqualTo("CASH");
    }

    @Test
    void cannotRenameToTheNameOfAnotherVariantOfTheSameType() {
        insertCustomVariant("CASH", "Zoom");
        long other = insertCustomVariant("CASH", "Deep");

        assertThat(putJson("/variants/" + other, """
                {"name": "zoom", "active": true}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_NAME_TAKEN");
    }

    @Test
    void builtInVariantsCanOnlyBeActivatedOrDeactivated() {
        long ko = builtInVariantId("TOURNAMENT", "KO");

        var json = assertThat(putJson("/variants/" + ko, """
                {"active": false}""")).hasStatusOk().bodyJson();
        json.extractingPath("$.active").isEqualTo(false);
        json.extractingPath("$.code").isEqualTo("KO");

        assertThat(putJson("/variants/" + ko, """
                {"name": "Knockout", "active": true}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_BUILT_IN");
        assertThat(jdbc.queryForObject("select name from variant where id = ?", String.class, ko)).isNull();
    }

    @Test
    void updateNeedsTheActiveFlagAndANonBlankName() {
        long id = insertCustomVariant("CASH", "Zoom");

        var json = assertThat(putJson("/variants/" + id, """
                {"name": "   "}""")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();

        json.extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
        json.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("name", "active");
    }

    // ---- delete ----

    @Test
    void deletesAUserDefinedVariantNotInUse() {
        long id = insertCustomVariant("CASH", "Zoom");

        assertThat(mvc.delete().uri("/variants/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("select count(*) from variant where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void cannotDeleteAVariantUsedByGames() {
        long room = insertRoom("Winamax", "EUR");
        long id = insertCustomVariant("CASH", "Zoom");
        insertGame(room, "CASH", id);

        assertThat(mvc.delete().uri("/variants/{id}", id))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_IN_USE");
    }

    @Test
    void cannotDeleteABuiltInVariant() {
        assertThat(mvc.delete().uri("/variants/{id}", builtInVariantId("TOURNAMENT", "KO")))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("VARIANT_BUILT_IN");
    }

    @Test
    void unknownVariantIsNotFound() {
        assertThat(putJson("/variants/999999", """
                {"active": true}""")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(mvc.delete().uri("/variants/{id}", 999_999)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
