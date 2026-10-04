package io.github.kete1987.pokerbankroll.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class BackupApiTests extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Only the first bytes of an image are checked: the signature of each format and some content. */
    private static final byte[] PNG = image(300, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] WEBP = image(64, 'R', 'I', 'F', 'F', 16, 0, 0, 0, 'W', 'E', 'B', 'P');

    /** What identifies a row in one database only: left out when two installations are compared. */
    private static final Set<String> LOCAL = Set.of("id", "roomId", "variantId", "createdAt", "updatedAt", "logoVersion");

    /** Every view of the data the API gives: lists, statistics and bankroll. */
    private static final List<String> VIEWS = List.of(
            "/rooms",
            "/variants",
            "/games?size=100",
            "/games?size=100&status=IN_PLAY",
            "/bankroll/movements?size=100",
            "/game-templates",
            "/stats/summary",
            "/stats/groups?groupBy=MONTH",
            "/stats/groups?groupBy=GAME_TYPE",
            "/stats/groups?groupBy=TAG",
            "/tags",
            "/bankroll/summary",
            "/bankroll/summary?from=2026-02-01&to=2026-02-28",
            "/settings/currency",
            "/exchange-rates/manual");

    // ---- backup ----

    @Test
    void theBackupIsAnAttachmentNamedAfterTheDayThatIsNeverCached() {
        MvcTestResult result = mvc.get().uri("/backup").exchange();

        assertThat(result).hasStatusOk().headers()
                .hasValue("Content-Type", "application/json")
                .hasValue("Content-Disposition",
                        "attachment; filename=\"poker-bankroll-backup-" + LocalDate.now() + ".json\"")
                .hasValue("Cache-Control", "no-store")
                .hasValue("X-Content-Type-Options", "nosniff");
    }

    @Test
    void theFileSaysItsVersionAndHoldsEverything() {
        populate();

        byte[] file = backup();
        JsonNode document = JSON.readTree(file);

        assertThat(document.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(document.get("appVersion").asString()).isNotBlank();
        assertThat(document.get("exportedAt").asString()).startsWith(LocalDate.now().getYear() + "-").endsWith("Z");
        assertThat(document.get("rooms")).hasSize(4);
        // The twelve built-in variants, with whether they are active, and the two of the user.
        assertThat(document.get("variants")).hasSize(14);
        assertThat(document.get("games")).hasSize(9);
        assertThat(document.get("movements")).hasSize(6);
        assertThat(document.get("templates")).hasSize(4);

        JsonNode winamax = document.get("rooms").get(0);
        assertThat(winamax.get("name").asString()).isEqualTo("Winamax");
        assertThat(winamax.get("currencyCode").asString()).isEqualTo("EUR");
        assertThat(winamax.get("logo").get("contentType").asString()).isEqualTo("image/png");
        assertThat(winamax.get("logo").get("content").binaryValue()).isEqualTo(PNG);
        // Amounts are decimal numbers as they are stored, dates and times are ISO-8601.
        String text = new String(file, StandardCharsets.UTF_8);
        assertThat(text).contains("\"buyIn\":10.00", "\"prize\":80.50", "\"amount\":-12.34",
                "\"playedOn\":\"2026-01-19\"", "\"playedAt\":\"21:30:00\"");
        // Games name their room and variant by the ids of the file.
        JsonNode first = document.get("games").get(0);
        assertThat(first.get("roomId")).isEqualTo(winamax.get("id"));
        assertThat(first.get("status").asString()).isEqualTo("FINISHED");
        // Each game names its tags, by name; a game without tags has no such property.
        assertThat(first.get("tags").toString()).isEqualTo("[\"challenge\",\"Series\"]");
        assertThat(document.get("games").get(1).has("tags")).isFalse();
        // So do templates.
        JsonNode template = document.get("templates").get(0);
        assertThat(template.get("roomId")).isEqualTo(winamax.get("id"));
        assertThat(template.get("label").asString()).isEqualTo("Sunday KO");
        assertThat(template.get("name").asString()).isEqualTo("Kill The Fish");
        assertThat(template.get("buyIn").decimalValue()).isEqualByComparingTo("10");
        // The base currency chosen and the rates typed by hand; not the downloaded ones.
        assertThat(document.get("baseCurrencyCode").asString()).isEqualTo("USD");
        assertThat(text).contains(
                "\"exchangeRates\":[{\"currencyCode\":\"USD\",\"date\":\"2026-02-14\",\"rate\":1.20000000}]");
    }

    @Test
    void theBaseCurrencyAndTheRatesTypedByHandAreReplacedAndTheDownloadedOnesStay() {
        populate();
        byte[] file = backup();
        ok(putJson("/settings/currency", """
                {"baseCurrencyCode": "EUR"}"""));
        ok(putJson("/exchange-rates/manual/USD/2026-03-01", """
                {"rate": 1.3}"""));
        insertRate("USD", "2026-03-02", "1.15");

        assertThat(restore(file, "?replace=true")).hasStatusOk().bodyJson().extractingPath("$.restored").isEqualTo(true);

        assertThat(jdbc.queryForObject("select base_currency_code from currency_setting", String.class)).isEqualTo("USD");
        assertThat(jdbc.queryForList("select rate_date || ' ' || source from exchange_rate order by rate_date",
                String.class)).containsExactly("2026-01-01 ECB", "2026-02-14 MANUAL", "2026-03-02 ECB");

        // A file without them (made before they existed): an automatic base currency and no manual rates.
        byte[] older = edited(file, document -> {
            document.remove("baseCurrencyCode");
            document.remove("exchangeRates");
        });
        assertThat(restore(older, "?replace=true")).hasStatusOk().bodyJson().extractingPath("$.restored")
                .isEqualTo(true);
        assertThat(jdbc.queryForObject("select base_currency_code from currency_setting", String.class)).isNull();
        assertThat(count("exchange_rate where source = 'MANUAL'")).isZero();
        assertThat(count("exchange_rate where source = 'ECB'")).isEqualTo(2);
    }

    @Test
    void theCurrenciesOfTheFileAreChecked() {
        byte[] file = file("""
                {"formatVersion": 1, "rooms": [], "variants": [], "games": [], "movements": [],
                 "baseCurrencyCode": "XYZ",
                 "exchangeRates": [
                   {"currencyCode": "USD", "date": "2026-01-02", "rate": 1.1},
                   {"currencyCode": "USD", "date": "2026-01-02", "rate": 1.2},
                   {"currencyCode": "EUR", "date": "2026-01-02", "rate": 1},
                   {"currencyCode": "GBP", "date": "2026-01-02", "rate": 0.8},
                   {"currencyCode": "USD", "rate": -1},
                   {"date": "2026-01-03", "rate": 1},
                   null]}
                """);

        MvcTestResult result = restore(file, "");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.restored").isEqualTo(false);
        assertThat(errors(result)).containsExactly(
                "baseCurrencyCode UNKNOWN_CURRENCY The currency XYZ does not exist.",
                "exchangeRates[1] DUPLICATE_EXCHANGE_RATE There is already a rate of USD on 2026-01-02.",
                "exchangeRates[2].currencyCode EXCHANGE_RATE_OF_EUR "
                        + "EUR has no exchange rate: rates are given per 1 EUR, so it is always 1.",
                "exchangeRates[3].currencyCode UNKNOWN_CURRENCY The currency GBP does not exist.",
                "exchangeRates[4].rate Positive must be greater than 0",
                "exchangeRates[4].date REQUIRED A value is required.",
                "exchangeRates[5].currencyCode REQUIRED A value is required.",
                "exchangeRates[6] REQUIRED A value is required.");
        assertThat(count("exchange_rate")).isZero();
    }

    @Test
    void anEmptyInstallationGivesABackupThatRestoresToAnEmptyOne() {
        byte[] file = backup();

        var json = assertThat(restore(file, "")).hasStatusOk().bodyJson();
        json.extractingPath("$.restored").isEqualTo(true);
        json.extractingPath("$.file.empty").isEqualTo(true);
        json.extractingPath("$.current.empty").isEqualTo(true);
        assertThat(count("room")).isZero();
    }

    // ---- round trip ----

    @Test
    void restoringABackupIntoAnEmptyInstallationGivesTheSameData() {
        populate();
        Map<String, JsonNode> before = views();
        byte[] file = backup();
        wipe();
        assertThat(views()).isNotEqualTo(before);

        MvcTestResult result = restore(file, "");

        var json = assertThat(result).hasStatusOk().bodyJson();
        json.extractingPath("$.dryRun").isEqualTo(false);
        json.extractingPath("$.restored").isEqualTo(true);
        json.extractingPath("$.formatVersion").isEqualTo(1);
        json.extractingPath("$.file.rooms").isEqualTo(4);
        json.extractingPath("$.file.variants").isEqualTo(2);
        json.extractingPath("$.file.games").isEqualTo(9);
        json.extractingPath("$.file.gamesInPlay").isEqualTo(2);
        json.extractingPath("$.file.movements").isEqualTo(6);
        json.extractingPath("$.file.templates").isEqualTo(4);
        json.extractingPath("$.file.from").isEqualTo("2026-01-19");
        json.extractingPath("$.file.to").isEqualTo("2026-03-02");
        json.extractingPath("$.file.empty").isEqualTo(false);
        json.extractingPath("$.current.empty").isEqualTo(true);
        json.extractingPath("$.errorCount").isEqualTo(0);

        // Rooms, variants, games and their tags, movements, statistics and bankroll: all as they were.
        assertThat(views()).isEqualTo(before);
        assertThat(jdbc.queryForList("select name from tag order by name", String.class))
                .containsExactly("Friends", "Series", "challenge");
        // And a backup of the restored installation holds the same.
        assertThat(comparable(JSON.readTree(backup()))).isEqualTo(comparable(JSON.readTree(file)));
    }

    @Test
    void logosSurviveByteForByte() {
        populate();
        byte[] file = backup();
        wipe();

        assertThat(restore(file, "")).hasStatusOk();

        assertThat(logoOf("Winamax")).hasStatusOk().hasContentType("image/png");
        assertThat(logoOf("Winamax").getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(logoOf("PokerStars")).hasStatusOk().hasContentType("image/webp");
        assertThat(logoOf("PokerStars").getResponse().getContentAsByteArray()).isEqualTo(WEBP);
        assertThat(logoOf("888poker")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(count("room_logo")).isEqualTo(2);
    }

    @Test
    void statesTheApiDoesNotCreateAfreshAreRestored() {
        populate();
        byte[] file = backup();
        wipe();

        assertThat(restore(file, "")).hasStatusOk();

        // Games in an inactive room and with inactive variants, built-in and of the user.
        assertThat(jdbc.queryForObject("""
                select count(*) from game g join room r on r.id = g.room_id where not r.active
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from game g join variant v on v.id = g.variant_id where not v.active
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select code from variant where not active and code is not null order by code",
                String.class)).containsExactly("HEADS_UP", "MYSTERY_KO");
        assertThat(jdbc.queryForObject("select count(*) from game where status = 'IN_PLAY'", Integer.class))
                .isEqualTo(2);
        // A template in an inactive room and with an inactive variant.
        assertThat(mvc.get().uri("/game-templates")).hasStatusOk().bodyJson()
                .extractingPath("$[?(@.room.name == 'Unibet')].usable").asArray().containsExactly(false);
    }

    // ---- an installation that has data ----

    @Test
    void anInstallationWithDataIsNotReplacedUnlessAskedTo() {
        populate();
        byte[] file = backup();
        wipe();
        otherData();
        Map<String, JsonNode> other = rawViews();

        MvcTestResult result = restore(file, "");

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("BACKUP_REPLACE_NOT_CONFIRMED");
        assertThat(restore(file, "?replace=false")).hasStatus(HttpStatus.CONFLICT);
        assertThat(rawViews()).isEqualTo(other);
    }

    @Test
    void replacingDeletesEverythingThereWasAndLeavesWhatTheFileHolds() {
        populate();
        Map<String, JsonNode> before = views();
        byte[] file = backup();
        wipe();
        otherData();

        MvcTestResult result = restore(file, "?replace=true");

        var json = assertThat(result).hasStatusOk().bodyJson();
        json.extractingPath("$.restored").isEqualTo(true);
        // What the installation held, and lost.
        json.extractingPath("$.current.rooms").isEqualTo(1);
        json.extractingPath("$.current.variants").isEqualTo(1);
        json.extractingPath("$.current.games").isEqualTo(2);
        json.extractingPath("$.current.gamesInPlay").isEqualTo(1);
        json.extractingPath("$.current.movements").isEqualTo(1);
        json.extractingPath("$.current.templates").isEqualTo(1);
        json.extractingPath("$.current.from").isEqualTo("2025-05-05");
        json.extractingPath("$.current.to").isEqualTo("2025-05-06");
        json.extractingPath("$.current.empty").isEqualTo(false);
        assertThat(views()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from room where name = 'Other room'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tag where name = 'Other tag'", Integer.class)).isZero();
        // A built-in variant the file has as active is active again.
        assertThat(jdbc.queryForObject("select active from variant where code = 'SPACE_KO'", Boolean.class)).isTrue();
    }

    @Test
    void aDryRunSaysWhatTheFileHoldsAndWhatWouldBeLostAndChangesNothing() {
        populate();
        byte[] file = backup();
        wipe();
        otherData();
        Map<String, JsonNode> other = rawViews();

        // No need to ask for the replacement: nothing is replaced.
        MvcTestResult result = restore(file, "?dryRun=true");

        var json = assertThat(result).hasStatusOk().bodyJson();
        json.extractingPath("$.dryRun").isEqualTo(true);
        json.extractingPath("$.restored").isEqualTo(false);
        json.extractingPath("$.file.games").isEqualTo(9);
        json.extractingPath("$.file.rooms").isEqualTo(4);
        json.extractingPath("$.current.games").isEqualTo(2);
        json.extractingPath("$.current.rooms").isEqualTo(1);
        json.extractingPath("$.current.empty").isEqualTo(false);
        json.extractingPath("$.errorCount").isEqualTo(0);
        // The very same rows, ids included.
        assertThat(rawViews()).isEqualTo(other);
        assertThat(logoOf("Other room")).hasStatusOk();
    }

    // ---- all or nothing ----

    @Test
    void anErrorInTheMiddleOfTheFileLeavesTheInstallationAsItWas() {
        populate();
        byte[] file = edited(backup(), document -> {
            ObjectNode game = (ObjectNode) document.get("games").get(4);
            game.put("roomId", 987654);
            game.put("buyIn", -5);
        });
        wipe();
        otherData();
        Map<String, JsonNode> other = rawViews();

        MvcTestResult result = restore(file, "?replace=true");

        var json = assertThat(result).hasStatusOk().bodyJson();
        json.extractingPath("$.restored").isEqualTo(false);
        json.extractingPath("$.errorCount").isEqualTo(2);
        assertThat(errors(result)).containsExactlyInAnyOrder(
                "games[4].roomId UNKNOWN_ROOM The room 987654 does not exist.",
                "games[4].buyIn DecimalMin must be greater than or equal to 0");
        assertThat(rawViews()).isEqualTo(other);
    }

    @Test
    void anErrorFoundWhileWritingRollsEverythingBack() {
        populate();
        // Not an image: only found when the logo is stored, once the installation was emptied.
        byte[] file = edited(backup(), document -> ((ObjectNode) document.get("rooms").get(0).get("logo"))
                .put("content", "not an image".getBytes(StandardCharsets.UTF_8)));
        wipe();
        otherData();
        Map<String, JsonNode> other = rawViews();

        MvcTestResult result = restore(file, "?replace=true");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.restored").isEqualTo(false);
        assertThat(errors(result)).containsExactly(
                "rooms[0].logo.content LOGO_UNSUPPORTED_TYPE The logo must be a PNG, JPEG or WebP image.");
        assertThat(rawViews()).isEqualTo(other);
        assertThat(logoOf("Other room")).hasStatusOk();
    }

    @Test
    void theRulesOfTheApiAndOfTheFileAreCheckedSayingWhere() {
        byte[] file = file("""
                {"formatVersion": 1,
                 "rooms": [
                   {"id": 1, "name": "Winamax", "currencyCode": "EUR"},
                   {"id": 1, "name": "winamax ", "currencyCode": "EUR"},
                   {"name": " ", "currencyCode": "EUR"},
                   null],
                 "variants": [
                   {"id": 1, "gameType": "TOURNAMENT", "code": "KO"},
                   {"id": 2, "gameType": "TOURNAMENT", "code": "FROM_THE_FUTURE"},
                   {"id": 3, "gameType": "TOURNAMENT", "name": "Turbo"},
                   {"id": 4, "gameType": "TOURNAMENT", "name": "TURBO"},
                   {"id": 5, "gameType": "CASH"},
                   {"id": 6, "gameType": "SIT_AND_GO", "name": "Flash"}],
                 "games": [
                   {"playedOn": "2026-01-19", "roomId": 1, "gameType": "TOURNAMENT", "variantId": 1,
                    "status": "FINISHED", "buyIn": 10, "tags": ["fine", "a;b", null]},
                   {"playedOn": "2026-01-19", "roomId": 1, "gameType": "TOURNAMENT", "variantId": 2,
                    "status": "FINISHED", "buyIn": 10},
                   {"playedOn": "2026-01-19", "roomId": 1, "gameType": "TOURNAMENT", "variantId": 6,
                    "status": "IN_PLAY", "buyIn": 10, "prize": 5},
                   {"roomId": 1, "gameType": "CASH", "variantId": 99, "buyIn": 10.123, "entries": 2}],
                 "movements": [
                   {"occurredOn": "2026-01-01", "type": "DEPOSIT", "roomId": 1, "amount": -5},
                   {"occurredOn": "2026-01-01", "type": "DEPOSIT", "amount": 5}],
                 "templates": [
                   {"roomId": 1, "gameType": "SIT_AND_GO", "variantId": 6, "buyIn": 2},
                   {"roomId": 99, "gameType": "TOURNAMENT", "variantId": 2, "buyIn": -1, "label": ""},
                   {"gameType": "CASH", "variantId": 98, "name": "%s"},
                   null]}
                """.formatted("x".repeat(151)));

        MvcTestResult result = restore(file, "?dryRun=true");

        assertThat(result).hasStatusOk();
        assertThat(errors(result).stream().map(error -> error.substring(0, error.indexOf(' ', error.indexOf(' ') + 1))))
                .containsExactlyInAnyOrder(
                        "rooms[1].id DUPLICATE_ID",
                        "rooms[1].name ROOM_NAME_TAKEN",
                        "rooms[2].id REQUIRED",
                        "rooms[2].name NotBlank",
                        "rooms[3] REQUIRED",
                        "variants[3].name VARIANT_NAME_TAKEN",
                        "variants[4] VARIANT_CODE_OR_NAME",
                        "games[0].tags[1] TagName",
                        "games[0].tags[2] NotNull",
                        "games[1].variantId UNKNOWN_BUILT_IN_VARIANT",
                        "games[2].variantId VARIANT_GAME_TYPE_MISMATCH",
                        "games[2].prize InPlayGameHasNoResult",
                        "games[3].playedOn NotNull",
                        "games[3].status REQUIRED",
                        "games[3].variantId UNKNOWN_VARIANT",
                        "games[3].buyIn Digits",
                        "games[3].entries CashGameFields",
                        "movements[0].amount MovementAmountSign",
                        "movements[1].roomId RoomOrCurrency",
                        "movements[1].currencyCode RoomOrCurrency",
                        "templates[1].roomId UNKNOWN_ROOM",
                        "templates[1].variantId UNKNOWN_BUILT_IN_VARIANT",
                        "templates[1].buyIn DecimalMin",
                        "templates[2].roomId NotNull",
                        "templates[2].buyIn NotNull",
                        "templates[2].name Size",
                        "templates[2].variantId UNKNOWN_VARIANT",
                        "templates[3] REQUIRED");
        assertThat(count("room")).isZero();
    }

    @Test
    void aCurrencyTheInstallationDoesNotHaveIsAnError() {
        byte[] file = file("""
                {"formatVersion": 1,
                 "rooms": [{"id": 1, "name": "Stake", "currencyCode": "BTC"}],
                 "variants": [],
                 "games": [],
                 "movements": [{"occurredOn": "2026-01-01", "type": "DEPOSIT", "currencyCode": "GBP", "amount": 5}]}
                """);

        MvcTestResult result = restore(file, "");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.restored").isEqualTo(false);
        assertThat(errors(result)).containsExactly(
                "rooms[0].currencyCode UNKNOWN_CURRENCY The currency BTC does not exist.",
                "movements[0].currencyCode UNKNOWN_CURRENCY The currency GBP does not exist.");
        assertThat(count("room")).isZero();
        assertThat(count("bankroll_movement")).isZero();
    }

    @Test
    void errorsAreInTheLanguageOfTheRequest() {
        byte[] file = file("""
                {"formatVersion": 1, "rooms": [{"name": "Stake", "currencyCode": "BTC"}],
                 "variants": [], "games": [], "movements": []}
                """);

        MvcTestResult result = mvc.post().uri("/backup/restore").contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "es").content(file).exchange();

        assertThat(errors(result)).containsExactly(
                "rooms[0].id REQUIRED El valor es obligatorio.",
                "rooms[0].currencyCode UNKNOWN_CURRENCY La moneda BTC no existe.");
    }

    // ---- versions of the format ----

    @Test
    void aFileOfANewerFormatIsRejected() {
        populate();
        byte[] file = edited(backup(), document -> document.put("formatVersion", 2));
        Map<String, JsonNode> before = rawViews();

        MvcTestResult result = restore(file, "?replace=true");

        var json = assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.code").isEqualTo("BACKUP_FORMAT_TOO_NEW");
        json.extractingPath("$.detail").asString()
                .contains("made by a newer version", "format 2", "up to format 1");
        assertThat(restore(file, "?dryRun=true")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(rawViews()).isEqualTo(before);
    }

    @Test
    void propertiesTheFormatDoesNotHaveAreIgnoredAndWhatIsOptionalTakesItsDefault() {
        byte[] file = file("""
                {"formatVersion": 1, "appVersion": "0.2.0", "exportedAt": "2026-10-02T10:15:30Z", "madeWith": "love",
                 "rooms": [{"id": 7, "name": "Winamax", "currencyCode": "EUR", "colour": "red"}],
                 "variants": [{"id": 3, "gameType": "TOURNAMENT", "code": "KO", "active": false, "since": 2020}],
                 "games": [{"playedOn": "2026-01-19", "roomId": 7, "gameType": "TOURNAMENT", "variantId": 3,
                            "status": "FINISHED", "buyIn": "10.50", "prize": 30, "table": 12}],
                 "movements": [{"occurredOn": "2026-01-01", "type": "DEPOSIT", "roomId": 7, "amount": 100}]}
                """);

        MvcTestResult result = restore(file, "");

        var json = assertThat(result).hasStatusOk().bodyJson();
        json.extractingPath("$.restored").isEqualTo(true);
        json.extractingPath("$.appVersion").isEqualTo("0.2.0");
        json.extractingPath("$.exportedAt").isEqualTo("2026-10-02T10:15:30Z");
        // A file without templates (made before they existed) has none.
        json.extractingPath("$.file.templates").isEqualTo(0);
        var game = assertThat(mvc.get().uri("/games").exchange()).hasStatusOk().bodyJson();
        game.extractingPath("$.items[0].room.name").isEqualTo("Winamax");
        game.extractingPath("$.items[0].variant.code").isEqualTo("KO");
        game.extractingPath("$.items[0].modality").isEqualTo("NLHE");
        game.extractingPath("$.items[0].entries").isEqualTo(1);
        game.extractingPath("$.items[0].buyIn").isEqualTo(10.5);
        game.extractingPath("$.items[0].net").isEqualTo(19.5);
        game.extractingPath("$.items[0].paidWithTicket").isEqualTo(false);
        // A file made before tags existed: its games have none.
        game.extractingPath("$.items[0].tags").asArray().isEmpty();
        assertThat(jdbc.queryForObject("select active from room", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("select code from variant where not active", String.class)).containsExactly("KO");
    }

    // ---- files that cannot be read ----

    @Test
    void whatIsNotABackupIsRejectedSayingWhere() {
        populate();
        Map<String, JsonNode> before = rawViews();

        assertMalformed("this is not JSON", "line 1");
        assertMalformed("", "line 1");
        assertMalformed("{\"formatVersion\": 1, \"rooms\": [\n\n{\"id\": 1, \"name\": \"Winamax\"", "line 3");
        assertMalformed("[]", "line 1");
        assertMalformed("{}", "in formatVersion");
        assertMalformed("{\"formatVersion\": \"one\"}", "in formatVersion");
        assertMalformed("{\"formatVersion\": 0}", "in formatVersion");
        assertMalformed("{\"formatVersion\": 1}", "in rooms");
        assertMalformed("{\"formatVersion\": 1, \"rooms\": [], \"variants\": [], \"games\": []}", "in movements");
        assertMalformed("""
                {"formatVersion": 1, "rooms": [], "variants": [], "movements": [],
                 "games": [{"playedOn": "2026-01-19"}, {"playedOn": "yesterday"}]}
                """, "in games[1].playedOn");
        assertMalformed("""
                {"formatVersion": 1, "rooms": [], "variants": [], "movements": [],
                 "games": [{"gameType": "BINGO"}]}
                """, "in games[0].gameType");
        assertMalformed("""
                {"formatVersion": 1, "rooms": [], "variants": [], "games": [],
                 "movements": [{"amount": "a lot"}]}
                """, "in movements[0].amount");
        assertMalformed("{\"formatVersion\": 1, \"rooms\": [], \"variants\": [], \"games\": [], \"movements\": []} x",
                "line 1");

        assertThat(rawViews()).isEqualTo(before);
    }

    @Test
    void aFileOverTheLimitIsRejected() {
        byte[] file = new byte[BackupService.MAX_BYTES + 1];
        java.util.Arrays.fill(file, (byte) ' ');

        MvcTestResult result = restore(file, "?dryRun=true");

        var json = assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE).bodyJson();
        json.extractingPath("$.code").isEqualTo("BACKUP_FILE_TOO_LARGE");
        json.extractingPath("$.detail").asString().contains("32 MB");
    }

    @Test
    void theBodyMustBeJson() {
        MvcTestResult result = mvc.post().uri("/backup/restore").contentType("text/csv").content("a,b").exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ---- data ----

    /** An installation with a bit of everything. */
    private void populate() {
        long winamax = room("Winamax", "EUR");
        long tripleEight = room("888poker", "EUR");
        long pokerStars = room("PokerStars", "USD");
        long unibet = room("Unibet", "EUR");
        logo(winamax, "image/png", PNG);
        logo(pokerStars, "image/webp", WEBP);

        long hyperTurbo = variant("SIT_AND_GO", "Hyper Turbo 6-max");
        long oldFormat = variant("TOURNAMENT", "Old format");
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long mysteryKo = builtInVariantId("TOURNAMENT", "MYSTERY_KO");

        game("""
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "variantId": %d, "modality": "PLO", "name": "Kill The Fish", "buyIn": 10, "entries": 3,
                 "prize": 80.5, "bounty": 12.25, "notes": "Final table", "tags": ["Series", "challenge"]}"""
                .formatted(winamax, ko));
        game("""
                {"playedOn": "2026-01-19", "playedAt": "19:00", "roomId": %d, "gameType": "TOURNAMENT",
                 "name": "Satellite", "buyIn": 2, "status": "FINISHED", "ticketPrizeValue": 20,
                 "ticketDescription": "Series 20"}""".formatted(winamax));
        game("""
                {"playedOn": "2026-01-20", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "name": "Series", "buyIn": 20, "entries": 2, "paidWithTicket": true, "status": "FINISHED",
                 "tags": ["series"]}""".formatted(winamax, mysteryKo));
        game("""
                {"playedOn": "2026-02-03", "roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d,
                 "buyIn": 5, "prize": 15}""".formatted(tripleEight, hyperTurbo));
        game("""
                {"playedOn": "2026-02-10", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "buyIn": 3.30, "status": "FINISHED"}""".formatted(unibet, oldFormat));
        game("""
                {"playedOn": "2026-02-14", "playedAt": "23:05", "roomId": %d, "gameType": "CASH",
                 "name": "NL10", "buyIn": 10, "prize": 27.4}""".formatted(pokerStars));
        game("""
                {"playedOn": "2026-02-28", "roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d,
                 "buyIn": 1, "prize": 0, "status": "FINISHED"}"""
                .formatted(pokerStars, builtInVariantId("SIT_AND_GO", "EXPRESSO")));
        game("""
                {"playedOn": "2026-03-02", "playedAt": "20:00", "roomId": %d, "gameType": "TOURNAMENT",
                 "variantId": %d, "name": "Main Event", "buyIn": 50, "entries": 2, "tags": ["Friends"]}"""
                .formatted(winamax, ko));
        game("""
                {"playedOn": "2026-03-02", "roomId": %d, "gameType": "CASH", "buyIn": 40}""".formatted(pokerStars));

        movement("""
                {"occurredOn": "2026-01-01", "type": "DEPOSIT", "roomId": %d, "amount": 500,
                 "notes": "Initial bankroll"}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-02-15", "type": "WITHDRAWAL", "roomId": %d, "amount": 100}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-02-20", "type": "BONUS", "roomId": %d, "amount": 7.5}""".formatted(pokerStars));
        movement("""
                {"occurredOn": "2026-02-21", "type": "ADJUSTMENT", "roomId": %d, "amount": -12.34}""".formatted(unibet));
        movement("""
                {"occurredOn": "2026-01-01", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 1000}""");
        movement("""
                {"occurredOn": "2026-01-02", "type": "DEPOSIT", "currencyCode": "USD", "amount": 250.75}""");

        template("""
                {"label": "Sunday KO", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "name": "Kill The Fish", "buyIn": 10}""".formatted(winamax, ko));
        template("""
                {"roomId": %d, "gameType": "SIT_AND_GO", "variantId": %d, "modality": "PLO", "buyIn": 5}"""
                .formatted(tripleEight, hyperTurbo));
        template("""
                {"roomId": %d, "gameType": "CASH", "buyIn": 40}""".formatted(pokerStars));
        template("""
                {"roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 3.30}"""
                .formatted(unibet, oldFormat));

        // What can no longer be used for new games, once it has them.
        ok(putJson("/rooms/" + unibet, """
                {"name": "Unibet", "currencyCode": "EUR", "active": false}"""));
        ok(putJson("/variants/" + oldFormat, """
                {"name": "Old format", "active": false}"""));
        ok(putJson("/variants/" + mysteryKo, """
                {"active": false}"""));
        ok(putJson("/variants/" + builtInVariantId("SIT_AND_GO", "HEADS_UP"), """
                {"active": false}"""));

        // Amounts in dollars are converted with a downloaded rate and one typed by hand, to dollars.
        insertRate("USD", "2026-01-01", "1.10");
        ok(putJson("/exchange-rates/manual/USD/2026-02-14", """
                {"rate": 1.2}"""));
        ok(putJson("/settings/currency", """
                {"baseCurrencyCode": "USD"}"""));
    }

    /** Another installation: nothing in common with {@link #populate()}. */
    private void otherData() {
        long room = room("Other room", "USD");
        logo(room, "image/png", PNG);
        long variant = variant("TOURNAMENT", "Other variant");
        game("""
                {"playedOn": "2025-05-05", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d,
                 "buyIn": 7, "prize": 70, "tags": ["Other tag", "Friends"]}""".formatted(room, variant));
        game("""
                {"playedOn": "2025-05-06", "roomId": %d, "gameType": "CASH", "buyIn": 25}""".formatted(room));
        movement("""
                {"occurredOn": "2025-05-01", "type": "DEPOSIT", "roomId": %d, "amount": 300}""".formatted(room));
        template("""
                {"roomId": %d, "gameType": "CASH", "buyIn": 25}""".formatted(room));
        ok(putJson("/variants/" + builtInVariantId("TOURNAMENT", "SPACE_KO"), """
                {"active": false}"""));
    }

    /** Leaves the database as a new installation has it. */
    private void wipe() {
        jdbc.update("delete from game_template");
        jdbc.update("delete from game");
        jdbc.update("delete from tag");
        jdbc.update("delete from bankroll_movement");
        jdbc.update("delete from room");
        jdbc.update("delete from variant where code is null");
        jdbc.update("update variant set active = true");
        // The downloaded rates stay: a new installation downloads them again.
        jdbc.update("delete from exchange_rate where source = 'MANUAL'");
        jdbc.update("update currency_setting set base_currency_code = null");
    }

    private long room(String name, String currency) {
        return created(postJson("/rooms", """
                {"name": "%s", "currencyCode": "%s"}""".formatted(name, currency)));
    }

    private void logo(long room, String contentType, byte[] content) {
        assertThat(mvc.put().uri("/rooms/{id}/logo", room).contentType(contentType).content(content).exchange())
                .hasStatusOk();
    }

    private long variant(String gameType, String name) {
        return created(postJson("/variants", """
                {"gameType": "%s", "name": "%s"}""".formatted(gameType, name)));
    }

    private void game(String json) {
        created(postJson("/games", json));
    }

    private void movement(String json) {
        created(postJson("/bankroll/movements", json));
    }

    private void template(String json) {
        created(postJson("/game-templates", json));
    }

    private long created(org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder request) {
        MvcTestResult result = request.exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JSON.readTree(body(result)).get("id").asLong();
    }

    private void ok(org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder request) {
        assertThat(request.exchange()).hasStatusOk();
    }

    // ---- calls ----

    private byte[] backup() {
        MvcTestResult result = mvc.get().uri("/backup").exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsByteArray();
    }

    private MvcTestResult restore(byte[] file, String query) {
        return mvc.post().uri("/backup/restore" + query).contentType(MediaType.APPLICATION_JSON).content(file)
                .exchange();
    }

    private void assertMalformed(String content, String where) {
        MvcTestResult result = restore(file(content), "?replace=true");
        var json = assertThat(result).as(content).hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.code").isEqualTo("BACKUP_FILE_MALFORMED");
        json.extractingPath("$.detail").asString().as(content).endsWith("(" + where + ").");
    }

    /** The errors of a restore, each as "path code message". */
    private List<String> errors(MvcTestResult result) {
        return JSON.readTree(body(result)).get("errors").valueStream()
                .map(error -> error.get("path").asString() + " " + error.get("code").asString() + " "
                        + error.get("message").asString())
                .toList();
    }

    private MvcTestResult logoOf(String room) {
        long id = jdbc.queryForObject("select id from room where name = ?", Long.class, room);
        return mvc.get().uri("/rooms/{id}/logo", id).exchange();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    /** What the API says the installation holds, without what only identifies rows in this database. */
    private Map<String, JsonNode> views() {
        Map<String, JsonNode> views = rawViews();
        views.replaceAll((uri, view) -> comparable(view));
        return views;
    }

    /** What the API says the installation holds, ids included. */
    private Map<String, JsonNode> rawViews() {
        Map<String, JsonNode> views = new LinkedHashMap<>();
        for (String uri : VIEWS) {
            MvcTestResult result = mvc.get().uri(uri).exchange();
            assertThat(result).as(uri).hasStatusOk();
            views.put(uri, JSON.readTree(body(result)));
        }
        return views;
    }

    private static JsonNode comparable(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode copy = JSON.createObjectNode();
            object.properties().forEach(property -> {
                // When a backup was made is not part of what it holds.
                if (!LOCAL.contains(property.getKey()) && !property.getKey().equals("exportedAt")) {
                    copy.set(property.getKey(), comparable(property.getValue()));
                }
            });
            return copy;
        }
        if (node instanceof ArrayNode array) {
            ArrayNode copy = JSON.createArrayNode();
            array.forEach(element -> copy.add(comparable(element)));
            return copy;
        }
        return node;
    }

    private static byte[] edited(byte[] file, Consumer<ObjectNode> change) {
        ObjectNode document = (ObjectNode) JSON.readTree(file);
        change.accept(document);
        return JSON.writeValueAsBytes(document);
    }

    private static byte[] file(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] image(int length, int... start) {
        byte[] content = new byte[length];
        for (int i = 0; i < start.length; i++) {
            content[i] = (byte) start[i];
        }
        // Not only zeros after the signature: every byte must come back.
        for (int i = start.length; i < length; i++) {
            content[i] = (byte) (i * 31);
        }
        return content;
    }
}
