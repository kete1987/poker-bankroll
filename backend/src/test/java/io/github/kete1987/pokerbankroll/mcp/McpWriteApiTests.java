package io.github.kete1987.pokerbankroll.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import io.github.kete1987.pokerbankroll.game.GameRepository;
import io.github.kete1987.pokerbankroll.tag.TagService;
import io.github.kete1987.pokerbankroll.template.GameTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "poker-bankroll.mcp.enabled=true",
        "poker-bankroll.mcp.write-enabled=true",
        "poker-bankroll.mcp.token=" + McpApiTests.TOKEN
})
class McpWriteApiTests extends ApiIntegrationTest {

    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean GameTemplateService templates;
    @MockitoSpyBean GameRepository gameRepository;
    @MockitoSpyBean TagService tagService;
    private final HttpClient client = HttpClient.newHttpClient();
    long room;

    @BeforeEach
    void createRoom() { room = insertRoom("Write test", "EUR"); }

    @Test
    void writesAllSupportedOperationsWithRestParityAndAccurateAnnotations() throws Exception {
        JsonNode tools = rpc("tools/list", Map.of()).path("result").path("tools");
        assertThat(tools.size()).isEqualTo(26);
        for (JsonNode tool : tools) {
            String name = tool.path("name").asString();
            if (List.of("start_game", "finish_game", "add_re_entry", "add_rebuy", "record_movement",
                    "set_game_notes", "set_game_tags").contains(name)) {
                assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isFalse();
                assertThat(tool.path("description").asString()).contains("data", "retries", "permission");
                assertThat(tool.path("annotations").path("destructiveHint").asBoolean())
                        .isEqualTo(name.startsWith("set_game_"));
            }
        }
        JsonNode game = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "5.10"))));
        long id = game.path("id").asLong();
        assertParity(game, "/games/" + id);
        assertThat(game.path("status").asString()).isEqualTo("IN_PLAY");
        assertParity(success("set_game_notes", keyed(Map.of("gameId", id, "update", Map.of("notes", "new note")))), "/games/" + id);
        assertParity(success("set_game_tags", keyed(Map.of("gameId", id, "update", Map.of("tags", List.of("Challenge", "challenge"))))), "/games/" + id);
        JsonNode reentered = success("add_re_entry", keyed(Map.of("gameId", id)));
        assertThat(reentered.path("entries").asInt()).isEqualTo(2);
        assertParity(reentered, "/games/" + id);
        JsonNode finished = success("finish_game", keyed(Map.of("gameId", id, "result",
                Map.of("prize", new BigDecimal("20.25"), "bounty", new BigDecimal("1.20"),
                        "ticketPrizeValue", new BigDecimal("10"), "ticketDescription", "ticket"))));
        assertThat(finished.path("net").decimalValue()).isEqualByComparingTo("11.25");
        assertThat(finished.path("notes").asString()).isEqualTo("new note");
        assertThat(finished.path("tags").size()).isEqualTo(1);
        assertParity(finished, "/games/" + id);
        JsonNode cash = success("start_game", keyed(Map.of("game", gameFields("CASH", "20"))));
        long cashId = cash.path("id").asLong();
        JsonNode rebought = success("add_rebuy", keyed(Map.of("gameId", cashId, "rebuy", Map.of("amount", new BigDecimal("2.05")))));
        assertThat(rebought.path("buyIn").decimalValue()).isEqualByComparingTo("22.05");
        assertParity(rebought, "/games/" + cashId);
        for (String type : List.of("DEPOSIT", "WITHDRAWAL", "BONUS")) {
            JsonNode movement = success("record_movement", keyed(Map.of("movement", Map.of(
                    "occurredOn", "2026-01-19", "type", type, "roomId", room, "amount", new BigDecimal("12.34")))));
            assertParity(movement, "/bankroll/movements/" + movement.path("id").asLong());
        }
        JsonNode independent = success("record_movement", keyed(Map.of("movement", Map.of(
                "occurredOn", "2026-01-19", "type", "DEPOSIT", "currencyCode", "USD", "amount", new BigDecimal("9999999999.99")))));
        assertThat(independent.path("amount").decimalValue()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void startsTemplatesWithoutAmbiguousOverridesAndRecordsFinishedGamesDirectly() throws Exception {
        JsonNode template = restPost("/game-templates", Map.of("roomId", room, "gameType", "SIT_AND_GO",
                "buyIn", new BigDecimal("5.55"), "name", "Regular", "label", "Quick start"));
        long templateId = template.path("id").asLong();
        JsonNode templates = success("list_game_templates", Map.of());
        assertThat(templates.path("items").get(0).path("id").asLong()).isEqualTo(templateId);
        JsonNode game = success("start_game", keyed(Map.of("templateId", templateId, "playedOn", "2026-01-19",
                "playedAt", "21:30", "notes", "from template", "tags", List.of("friendly"))));
        assertThat(game.path("buyIn").decimalValue()).isEqualByComparingTo("5.55");
        assertThat(game.path("status").asString()).isEqualTo("IN_PLAY");
        assertThat(game.path("playedAt").asString()).isEqualTo("21:30:00");
        assertError("start_game", keyed(Map.of("game", gameFields("CASH", "1"), "templateId", templateId,
                "playedOn", "2026-01-19")), "VALIDATION_FAILED");
        assertError("start_game", keyed(Map.of("templateId", templateId)), "VALIDATION_FAILED");
        assertError("start_game", keyed(Map.of("templateId", templateId, "playedOn", "2026-01-19", "buyIn", 99)), "VALIDATION_FAILED");
        var direct = new java.util.HashMap<>(gameFields("SIT_AND_GO", "1"));
        direct.put("status", "FINISHED");
        direct.put("prize", 0);
        assertThat(success("start_game", keyed(Map.of("game", direct))).path("status").asString()).isEqualTo("FINISHED");
        jdbc.update("update room set active = false where id = ?", room);
        assertError("start_game", keyed(Map.of("templateId", templateId, "playedOn", "2026-01-19")), "ROOM_INACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROOM", "VARIANT"})
    void templateStartsSeeDeactivationCommittedAfterReadingTheTemplate(String target) throws Exception {
        long variantId = builtInVariantId("SIT_AND_GO", "EXPRESSO");
        long templateId = restPost("/game-templates", Map.of("roomId", room, "gameType", "SIT_AND_GO",
                "variantId", variantId, "buyIn", 5)).path("id").asLong();
        var snapshotRead = new CountDownLatch(1);
        var continueStart = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object snapshot = invocation.callRealMethod();
            snapshotRead.countDown();
            if (!continueStart.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the concurrent deactivation");
            }
            return snapshot;
        }).when(templates).startFields(templateId);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = pool.submit(() -> result("start_game",
                    keyed(Map.of("templateId", templateId, "playedOn", "2026-01-19"))));
            try {
                assertThat(snapshotRead.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                if (target.equals("ROOM")) {
                    jdbc.update("update room set active = false where id = ?", room);
                } else {
                    jdbc.update("update variant set active = false where id = ?", variantId);
                }
            } finally {
                continueStart.countDown();
            }
            JsonNode failure = future.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(failure.path("isError").asBoolean()).as(failure.toString()).isTrue();
            assertThat(failure.path("structuredContent").path("code").asString()).isEqualTo(target + "_INACTIVE");
        }
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isZero();
    }

    @Test
    void validatesExistingRequestConstraintsAndDoesNotExposeExcludedOperations() throws Exception {
        assertError("start_game", Map.of("game", gameFields("CASH", "1")), "VALIDATION_FAILED");
        assertError("start_game", Map.of("idempotencyKey", "not-a-uuid", "game", gameFields("CASH", "1")), "VALIDATION_FAILED");
        var cash = new java.util.HashMap<>(gameFields("CASH", "1"));
        cash.put("entries", 2);
        JsonNode invalidCash = assertError("start_game", keyed(Map.of("game", cash)), "VALIDATION_FAILED");
        assertThat(invalidCash.path("structuredContent").path("errors").toString()).contains("game.entries", "CashGameFields");
        var inPlay = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        inPlay.put("status", "IN_PLAY");
        inPlay.put("prize", 2);
        assertError("start_game", keyed(Map.of("game", inPlay)), "VALIDATION_FAILED");
        assertError("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1.001"))), "VALIDATION_FAILED");
        assertError("record_movement", keyed(Map.of("movement", Map.of("occurredOn", "2026-01-19",
                "type", "ADJUSTMENT", "roomId", room, "amount", 1))), "VALIDATION_FAILED");
        assertError("record_movement", keyed(Map.of("movement", Map.of("occurredOn", "2026-01-19",
                "type", "DEPOSIT", "roomId", room, "currencyCode", "EUR", "amount", 1))), "VALIDATION_FAILED");
        assertError("record_movement", keyed(Map.of("movement", Map.of("occurredOn", "2026-01-19",
                "type", "DEPOSIT", "roomId", room, "amount", 0))), "VALIDATION_FAILED");
        for (String excluded : List.of("delete_game", "update_game", "restore_backup", "create_room", "set_currency")) {
            assertThat(rpc("tools/call", Map.of("name", excluded, "arguments", Map.of())).has("error")).isTrue();
        }
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
    }

    @Test
    void preservesBusinessErrorsAndRollsBackFailedReceipts() throws Exception {
        var request = keyed(Map.of("game", gameFields("TOURNAMENT", "1")));
        long variant = builtInVariantId("TOURNAMENT", "KO");
        jdbc.update("update variant set active = false where id = ?", variant);
        var withVariant = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        withVariant.put("variantId", variant);
        assertError("start_game", keyed(Map.of("game", withVariant)), "VARIANT_INACTIVE");
        jdbc.update("update variant set active = true where id = ?", variant);
        jdbc.update("update room set active = false where id = ?", room);
        assertError("start_game", request, "ROOM_INACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isZero();
        jdbc.update("update room set active = true where id = ?", room);
        long game = success("start_game", request).path("id").asLong();
        success("finish_game", keyed(Map.of("gameId", game, "result", Map.of())));
        assertError("finish_game", keyed(Map.of("gameId", game, "result", Map.of())), "GAME_NOT_IN_PLAY");
        assertError("add_re_entry", keyed(Map.of("gameId", game)), "GAME_NOT_IN_PLAY");
        assertError("add_rebuy", keyed(Map.of("gameId", game, "rebuy", Map.of("amount", 2))), "GAME_NOT_IN_PLAY");
        assertError("set_game_notes", keyed(Map.of("gameId", Long.MAX_VALUE, "update", Map.of("notes", "no game"))), "NOT_FOUND");
        long cash = success("start_game", keyed(Map.of("game", gameFields("CASH", "10")))).path("id").asLong();
        assertError("add_re_entry", keyed(Map.of("gameId", cash)), "RE_ENTRY_NOT_FOR_CASH_GAMES");
        assertError("finish_game", keyed(Map.of("gameId", cash, "result", Map.of("bounty", 1))), "CASH_GAME_RESULT");
        long tournament = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "10")))).path("id").asLong();
        assertError("add_rebuy", keyed(Map.of("gameId", tournament, "rebuy", Map.of("amount", 2))), "REBUY_ONLY_FOR_CASH_GAMES");
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isEqualTo(4);
    }

    @Test
    void aDatabaseFailureRollsBackTheResourceTagsAndReceiptTogether() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "5"));
        fields.put("tags", List.of("must roll back"));
        var request = keyed(Map.of("game", fields));
        // Reject the completed receipt after the service has saved the game and resolved tags.
        // A failure of that final database write must roll back the resource as well.
        jdbc.execute("alter table mcp_operation add constraint mcp_test_reject_receipt check (result is null)");
        try {
            assertError("start_game", request, "CONFLICT");
        } finally {
            jdbc.execute("alter table mcp_operation drop constraint mcp_test_reject_receipt");
        }
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tag", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isZero();
        success("start_game", request);
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
    }

    @Test
    void retriesReturnTheOriginalReceiptAndConflictingKeyReuseIsRejected() throws Exception {
        var request = keyed(Map.of("game", gameFields("TOURNAMENT", "2.50")));
        JsonNode first = result("start_game", request);
        long id = first.path("structuredContent").path("id").asLong();
        jdbc.update("update game set notes = 'changed later' where id = ?", id);
        var same = new java.util.HashMap<>(request);
        same.put("game", gameFields("TOURNAMENT", "2.5"));
        JsonNode replay = result("start_game", same);
        assertThat(replay.path("structuredContent")).isEqualTo(first.path("structuredContent"));
        assertThat(first.path("_meta").path("pokerBankrollOperation").path("replayed").asBoolean()).isFalse();
        assertThat(replay.path("_meta").path("pokerBankrollOperation").path("replayed").asBoolean()).isTrue();
        same.put("game", gameFields("TOURNAMENT", "3"));
        assertError("start_game", same, "MCP_IDEMPOTENCY_CONFLICT");
        assertError("record_movement", Map.of("idempotencyKey", request.get("idempotencyKey"), "movement",
                Map.of("occurredOn", "2026-01-19", "type", "BONUS", "roomId", room, "amount", 1)), "MCP_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
        JsonNode traces = success("list_mcp_operations", Map.of("resourceType", "GAME", "resourceId", id));
        assertThat(traces.path("totalItems").asInt()).isEqualTo(1);
        assertThat(traces.path("items").get(0).path("idempotencyKey").asString()).isEqualTo(request.get("idempotencyKey"));
        assertThat(traces.toString()).doesNotContain("changed later", "request_hash", "result");
    }

    @Test
    void concurrentRetriesCreateOneGameAndDistinctOperationsPreserveEveryChange() throws Exception {
        var request = keyed(Map.of("game", gameFields("TOURNAMENT", "5")));
        var responses = parallel(List.of(() -> result("start_game", request), () -> result("start_game", request),
                () -> result("start_game", request), () -> result("start_game", request)));
        long id = responses.getFirst().path("structuredContent").path("id").asLong();
        assertThat(responses.stream().filter(r -> !r.path("_meta").path("pokerBankrollOperation").path("replayed").asBoolean()).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
        parallel(List.of(
                () -> result("add_re_entry", keyed(Map.of("gameId", id))),
                () -> result("add_re_entry", keyed(Map.of("gameId", id)))));
        parallel(List.of(
                () -> result("set_game_notes", keyed(Map.of("gameId", id, "update", Map.of("notes", "concurrent note")))),
                () -> result("set_game_tags", keyed(Map.of("gameId", id, "update", Map.of("tags", List.of("concurrent tag"))))),
                () -> result("finish_game", keyed(Map.of("gameId", id, "result", Map.of("prize", 20))))));
        JsonNode current = restGet("/games/" + id);
        assertThat(current.path("entries").asInt()).isEqualTo(3);
        assertThat(current.path("status").asString()).isEqualTo("FINISHED");
        assertThat(current.path("notes").asString()).isEqualTo("concurrent note");
        assertThat(current.path("tags").get(0).path("name").asString()).isEqualTo("concurrent tag");
        assertThat(current.path("net").decimalValue()).isEqualByComparingTo("5");
    }

    @Test
    void restoreInvalidatesOldReceiptsWithoutReplayingOrRecreatingTheirWrites() throws Exception {
        var request = keyed(Map.of("game", gameFields("TOURNAMENT", "5")));
        long id = success("start_game", request).path("id").asLong();
        String backup = client.send(HttpRequest.newBuilder(uri("/backup")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        restPostJson("/backup/restore?dryRun=true", backup);
        assertThat(result("start_game", request).path("isError").asBoolean()).isFalse();
        JsonNode restored = restPostJson("/backup/restore?replace=true", backup);
        assertThat(restored.path("restored").asBoolean()).isTrue();
        assertError("start_game", request, "MCP_OPERATION_INVALIDATED");
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select result is null and resource_id is null from mcp_operation", Boolean.class)).isTrue();
        JsonNode trace = success("list_mcp_operations", Map.of()).path("items").get(0);
        assertThat(trace.path("invalidated").asBoolean()).isTrue();
        assertThat(trace.path("resourceId").isNull()).isTrue();
    }

    @Test
    void metadataCleanupViaMcpPreservesFinancialFiguresAndReplaysSafely() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "5"));
        fields.put("name", "Demo old name");
        fields.put("notes", "Existing note");
        fields.put("tags", List.of("Series", "Excess"));
        fields.put("status", "FINISHED");
        fields.put("prize", 12);
        JsonNode game = success("start_game", keyed(Map.of("game", fields)));
        long id = game.path("id").asLong();
        JsonNode statsBefore = success("get_stats_summary", Map.of());
        JsonNode bankrollBefore = success("get_bankroll_summary", Map.of());
        JsonNode inventory = success("list_games", Map.of("size", 1, "page", 0));
        assertThat(inventory.path("totalPages").asInt()).isEqualTo(1);
        assertThat(inventory.path("items").get(0).path("id").asLong()).isEqualTo(id);
        JsonNode safety = success("create_safety_backup", Map.of());
        assertThat(success("read_safety_backup_chunk", Map.of("snapshotId", safety.path("snapshotId").asString(), "offset", 0))
                .path("complete").asBoolean()).isTrue();
        JsonNode current = success("get_game", Map.of("gameId", id));
        Map<String, Object> change = Map.of("gameId", id, "expectedUpdatedAt", current.path("updatedAt").asString(),
                "expectedTags", List.of("Excess", "Series"), "name", Map.of("value", "Demo"),
                "notes", Map.of("mode", "APPEND", "value", "Event 12"),
                "tags", Map.of("mode", "REMOVE", "values", List.of("excess")));
        var preview = success("preview_game_metadata", Map.of("changes", List.of(change)));
        assertThat(preview.get(0).path("name").asString()).isEqualTo("Demo");
        assertThat(preview.get(0).path("notes").asString()).isEqualTo("Existing note\nEvent 12");
        assertThat(preview.get(0).path("tags").toString()).isEqualTo("[\"Series\"]");
        var request = keyed(Map.of("changes", List.of(change)));
        JsonNode applied = success("update_game_metadata", request);
        assertThat(success("update_game_metadata", request)).isEqualTo(applied);
        JsonNode after = success("get_game", Map.of("gameId", id));
        assertThat(after.path("name").asString()).isEqualTo("Demo");
        assertThat(after.path("notes").asString()).isEqualTo("Existing note\nEvent 12");
        assertThat(after.path("tags").size()).isEqualTo(1);
        JsonNode allTags = success("list_tags", Map.of()).path("items");
        long excess = 0;
        for (JsonNode tag : allTags) if (tag.path("name").asString().equals("Excess")) excess = tag.path("id").asLong();
        JsonNode deletion = success("preview_tag_change", Map.of("tagId", excess));
        assertThat(deletion.path("source").path("games").asInt()).isZero();
        success("apply_tag_change", keyed(Map.of("tagId", excess, "expected", mapper.convertValue(deletion, Map.class))));
        assertThat(success("list_tags", Map.of()).path("items").size()).isEqualTo(1);
        assertThat(success("get_stats_summary", Map.of())).isEqualTo(statsBefore);
        assertThat(success("get_bankroll_summary", Map.of())).isEqualTo(bankrollBefore);
        assertError("update_game_metadata", keyed(Map.of("changes", List.of(change))), "MCP_STALE_METADATA");
        var mismatch = new java.util.HashMap<>(request);
        mismatch.put("changes", List.of(Map.of("gameId", id, "expectedUpdatedAt", current.path("updatedAt").asString(),
                "expectedTags", List.of("Excess", "Series"), "name", Map.of("value", "Different"))));
        assertError("update_game_metadata", mismatch, "MCP_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void tagPreviewMergeDeleteAndBackupChunksWorkOverMcp() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "2"));
        fields.put("tags", List.of("Source", "Target"));
        long game = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        JsonNode list = success("list_tags", Map.of()).path("items");
        long source = 0, target = 0;
        for (JsonNode tag : list) {
            if (tag.path("name").asString().equals("Source")) source = tag.path("id").asLong();
            if (tag.path("name").asString().equals("Target")) target = tag.path("id").asLong();
        }
        JsonNode preview = success("preview_tag_change", Map.of("tagId", source, "newName", "target"));
        assertThat(preview.path("source").path("games").asInt()).isEqualTo(1);
        assertThat(preview.path("target").path("id").asLong()).isEqualTo(target);
        Map<String, Object> expected = mapper.convertValue(preview, Map.class);
        assertError("apply_tag_change", keyed(Map.of("tagId", source, "newName", "Other", "expected", expected)), "MCP_STALE_METADATA");
        assertError("apply_tag_change", keyed(Map.of("tagId", source, "expected", expected)), "MCP_STALE_METADATA");
        Map<String, Object> merge = keyed(Map.of("tagId", source, "newName", "target", "expected", expected));
        JsonNode merged = success("apply_tag_change", merge);
        assertThat(success("apply_tag_change", merge)).isEqualTo(merged);
        assertThat(success("get_game", Map.of("gameId", game)).path("tags").size()).isEqualTo(1);
        JsonNode rename = success("preview_tag_change", Map.of("tagId", target, "newName", "Renamed"));
        success("apply_tag_change", keyed(Map.of("tagId", target, "newName", "Renamed",
                "expected", mapper.convertValue(rename, Map.class))));
        assertThat(success("get_game", Map.of("gameId", game)).path("tags").get(0).path("name").asString()).isEqualTo("Renamed");
        assertError("preview_tag_change", Map.of("tagId", 99999999), "NOT_FOUND");
        JsonNode deletion = success("preview_tag_change", Map.of("tagId", target));
        Map<String, Object> delete = keyed(Map.of("tagId", target, "expected", mapper.convertValue(deletion, Map.class)));
        success("apply_tag_change", delete);
        assertThat(success("get_game", Map.of("gameId", game)).path("tags").size()).isZero();
        assertThat(success("apply_tag_change", delete).path("before").path("source").path("id").asLong()).isEqualTo(target);
        JsonNode manifest = success("create_safety_backup", Map.of());
        String snapshot = manifest.path("snapshotId").asString();
        var bytes = new java.io.ByteArrayOutputStream();
        int offset = 0;
        while (true) {
            JsonNode chunk = success("read_safety_backup_chunk", Map.of("snapshotId", snapshot, "offset", offset));
            bytes.write(java.util.Base64.getDecoder().decode(chunk.path("base64").asString()));
            offset = chunk.path("nextOffset").asInt();
            if (chunk.path("complete").asBoolean()) break;
        }
        assertThat(bytes.size()).isEqualTo(manifest.path("size").asInt());
        assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())))
                .isEqualTo(manifest.path("sha256").asString());
        assertThat(mapper.readTree(bytes.toByteArray()).path("games").size()).isEqualTo(1);
    }

    @Test
    void metadataBatchRejectsStaleUnknownAndOverLimitWithoutPartialChanges() throws Exception {
        long first = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        long second = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "3")))).path("id").asLong();
        JsonNode a = success("get_game", Map.of("gameId", first));
        JsonNode b = success("get_game", Map.of("gameId", second));
        Map<String, Object> editA = Map.of("gameId", first, "expectedUpdatedAt", a.path("updatedAt").asString(),
                "expectedTags", List.of(), "name", Map.of("value", "First"));
        Map<String, Object> editB = Map.of("gameId", second, "expectedUpdatedAt", b.path("updatedAt").asString(),
                "expectedTags", List.of("not current"), "name", Map.of("value", "Second"));
        JsonNode stale = assertError("update_game_metadata", keyed(Map.of("changes", List.of(editA, editB))), "MCP_STALE_METADATA");
        assertThat(stale.path("structuredContent").path("index").asInt()).isEqualTo(1);
        assertThat(success("get_game", Map.of("gameId", first)).path("name").isNull()).isTrue();
        Map<String, Object> unknown = Map.of("gameId", 99999999, "expectedUpdatedAt", b.path("updatedAt").asString(), "expectedTags", List.of());
        JsonNode missing = assertError("update_game_metadata", keyed(Map.of("changes", List.of(editA, unknown))), "NOT_FOUND");
        assertThat(missing.path("structuredContent").path("index").asInt()).isEqualTo(1);
        assertError("update_game_metadata", keyed(Map.of("changes", List.of(editA, editA))), "MCP_DUPLICATE_GAME");
        assertError("preview_game_metadata", Map.of("changes", java.util.Collections.nCopies(21, editA)), "VALIDATION_FAILED");
        jdbc.update("update room set active = false where id = ?", room);
        success("update_game_metadata", keyed(Map.of("changes", List.of(editA))));
        assertThat(success("get_game", Map.of("gameId", first)).path("name").asString()).isEqualTo("First");
    }

    @Test
    void concurrentMetadataEditsRejectTheStaleSnapshotAndPreserveTags() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("tags", List.of("Kept"));
        long id = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        JsonNode before = success("get_game", Map.of("gameId", id));
        var change = Map.of("gameId", id, "expectedUpdatedAt", before.path("updatedAt").asString(),
                "expectedTags", List.of("Kept"), "notes", Map.of("mode", "APPEND", "value", "once"));
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> { gate.await(); return result("update_game_metadata", keyed(Map.of("changes", List.of(change)))); });
            var second = pool.submit(() -> { gate.await(); return result("update_game_metadata", keyed(Map.of("changes", List.of(change)))); });
            gate.countDown();
            JsonNode a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
            JsonNode b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(a.path("isError").asBoolean(), b.path("isError").asBoolean())).containsExactlyInAnyOrder(true, false);
            JsonNode failure = a.path("isError").asBoolean() ? a : b;
            assertThat(failure.path("structuredContent").path("code").asString()).isEqualTo("MCP_STALE_METADATA");
        }
        JsonNode after = success("get_game", Map.of("gameId", id));
        assertThat(after.path("notes").asString()).isEqualTo("once");
        assertThat(after.path("tags").get(0).path("name").asString()).isEqualTo("Kept");
    }

    @Test
    void tagPreviewDetectsChangedAssociationsEvenWhenCountIsUnchanged() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("tags", List.of("Demo tag"));
        long first = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        long second = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        long tag = success("list_tags", Map.of()).path("items").get(0).path("id").asLong();
        JsonNode preview = success("preview_tag_change", Map.of("tagId", tag));
        success("set_game_tags", keyed(Map.of("gameId", first, "update", Map.of("tags", List.of()))));
        success("set_game_tags", keyed(Map.of("gameId", second, "update", Map.of("tags", List.of("Demo tag")))));
        assertError("apply_tag_change", keyed(Map.of("tagId", tag, "expected", mapper.convertValue(preview, Map.class))), "MCP_STALE_METADATA");
        assertThat(success("get_game", Map.of("gameId", second)).path("tags").size()).isEqualTo(1);
    }

    @Test
    void metadataTagAdditionUsesCanonicalSpellingAndRejectsResultLimits() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("tags", List.of("Canonical"));
        success("start_game", keyed(Map.of("game", fields)));
        long id = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        JsonNode current = success("get_game", Map.of("gameId", id));
        Map<String, Object> add = Map.of("gameId", id, "expectedUpdatedAt", current.path("updatedAt").asString(),
                "expectedTags", List.of(), "tags", Map.of("mode", "ADD", "values", List.of("canonical", "New")));
        JsonNode preview = success("preview_game_metadata", Map.of("changes", List.of(add)));
        assertThat(preview.get(0).path("tags").toString()).isEqualTo("[\"Canonical\",\"New\"]");
        success("update_game_metadata", keyed(Map.of("changes", List.of(add))));
        JsonNode after = success("get_game", Map.of("gameId", id));
        assertThat(after.path("tags").get(0).path("name").asString()).isEqualTo("Canonical");
        var tooMany = Map.of("gameId", id, "expectedUpdatedAt", after.path("updatedAt").asString(),
                "expectedTags", List.of("Canonical", "New"), "tags", Map.of("mode", "ADD", "values",
                        List.of("a", "b", "c", "d", "e", "f", "g", "h", "i")));
        assertError("update_game_metadata", keyed(Map.of("changes", List.of(tooMany))), "MCP_METADATA_LIMIT");
        assertThat(success("get_game", Map.of("gameId", id)).path("tags").size()).isEqualTo(2);
    }

    @Test
    void restoreInvalidatesMetadataAndDeletedTagReceipts() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("tags", List.of("Temporary"));
        long id = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        String backup = client.send(HttpRequest.newBuilder(uri("/backup")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        JsonNode current = success("get_game", Map.of("gameId", id));
        Map<String, Object> change = Map.of("gameId", id, "expectedUpdatedAt", current.path("updatedAt").asString(),
                "expectedTags", List.of("Temporary"), "name", Map.of("value", "Demo"));
        Map<String, Object> edit = keyed(Map.of("changes", List.of(change)));
        success("update_game_metadata", edit);
        long tag = success("list_tags", Map.of()).path("items").get(0).path("id").asLong();
        JsonNode preview = success("preview_tag_change", Map.of("tagId", tag));
        Map<String, Object> delete = keyed(Map.of("tagId", tag, "expected", mapper.convertValue(preview, Map.class)));
        success("apply_tag_change", delete);
        assertThat(success("list_mcp_operations", Map.of("resourceType", "TAG", "resourceId", tag)).path("totalItems").asInt()).isEqualTo(1);
        assertThat(restPostJson("/backup/restore?replace=true", backup).path("restored").asBoolean()).isTrue();
        assertError("update_game_metadata", edit, "MCP_OPERATION_INVALIDATED");
        assertError("apply_tag_change", delete, "MCP_OPERATION_INVALIDATED");
    }

    @Test
    void existingGameWriterAndMetadataBatchDoNotLoseEitherEdit() throws Exception {
        long id = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        JsonNode before = success("get_game", Map.of("gameId", id));
        var change = Map.of("gameId", id, "expectedUpdatedAt", before.path("updatedAt").asString(),
                "expectedTags", List.of(), "name", Map.of("value", "Demo"));
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var metadata = pool.submit(() -> { gate.await(); return result("update_game_metadata", keyed(Map.of("changes", List.of(change)))); });
            var other = pool.submit(() -> { gate.await(); return result("set_game_tags", keyed(Map.of("gameId", id, "update", Map.of("tags", List.of("Other"))))); });
            gate.countDown();
            JsonNode a = metadata.get(30, java.util.concurrent.TimeUnit.SECONDS);
            JsonNode b = other.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(b.path("isError").asBoolean()).as(b.toString()).isFalse();
            if (a.path("isError").asBoolean()) assertThat(a.path("structuredContent").path("code").asString()).isEqualTo("MCP_STALE_METADATA");
            JsonNode after = success("get_game", Map.of("gameId", id));
            assertThat(after.path("tags").get(0).path("name").asString()).isEqualTo("Other");
            if (!a.path("isError").asBoolean()) assertThat(after.path("name").asString()).isEqualTo("Demo");
        }
    }

    @Test
    void oversizedBatchResponseDoesNotCommitChangesOrAReceipt() throws Exception {
        List<Map<String, Object>> edits = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long id = insertGame(room, "TOURNAMENT", null);
            jdbc.update("update game set notes = ? where id = ?", String.valueOf((char) 1).repeat(5000), id);
            JsonNode game = success("get_game", Map.of("gameId", id));
            edits.add(Map.of("gameId", id, "expectedUpdatedAt", game.path("updatedAt").asString(),
                    "expectedTags", List.of(), "name", Map.of("value", "Should not be saved")));
        }
        assertError("update_game_metadata", keyed(Map.of("changes", edits)), "MCP_RESULT_TOO_LARGE");
        assertThat(jdbc.queryForObject("select count(*) from game where name = 'Should not be saved'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mcp_operation", Integer.class)).isZero();
    }

    @Test
    void explicitEmptyValuesClearMetadataAndAppendEnforcesLength() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("name", "Demo");
        fields.put("notes", "x".repeat(5000));
        fields.put("tags", List.of("One"));
        long id = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        JsonNode game = success("get_game", Map.of("gameId", id));
        var append = Map.of("gameId", id, "expectedUpdatedAt", game.path("updatedAt").asString(),
                "expectedTags", List.of("One"), "notes", Map.of("mode", "APPEND", "value", "more"));
        assertError("update_game_metadata", keyed(Map.of("changes", List.of(append))), "MCP_METADATA_LIMIT");
        var clear = Map.of("gameId", id, "expectedUpdatedAt", game.path("updatedAt").asString(),
                "expectedTags", List.of("One"), "name", Map.of("value", ""),
                "notes", Map.of("mode", "REPLACE", "value", ""),
                "tags", Map.of("mode", "REPLACE", "values", List.of()));
        success("update_game_metadata", keyed(Map.of("changes", List.of(clear))));
        JsonNode after = success("get_game", Map.of("gameId", id));
        assertThat(after.path("name").isNull()).isTrue();
        assertThat(after.path("notes").isNull()).isTrue();
        assertThat(after.path("tags").size()).isZero();
        assertThat(after.path("buyIn").decimalValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void largeSafetyBackupIsDeliveredAcrossBoundedChunks() throws Exception {
        for (int i = 0; i < 60; i++) {
            long id = insertGame(room, "TOURNAMENT", null);
            jdbc.update("update game set notes = ? where id = ?", "demo".repeat(1250), id);
        }
        JsonNode manifest = success("create_safety_backup", Map.of());
        String id = manifest.path("snapshotId").asString();
        JsonNode first = success("read_safety_backup_chunk", Map.of("snapshotId", id, "offset", 0));
        assertThat(first.path("complete").asBoolean()).isFalse();
        assertThat(java.util.Base64.getDecoder().decode(first.path("base64").asString()).length).isEqualTo(256 * 1024);
        JsonNode second = success("read_safety_backup_chunk", Map.of("snapshotId", id, "offset", first.path("nextOffset").asInt()));
        assertThat(second.path("complete").asBoolean()).isTrue();
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.write(java.util.Base64.getDecoder().decode(first.path("base64").asString()));
        bytes.write(java.util.Base64.getDecoder().decode(second.path("base64").asString()));
        assertThat(bytes.size()).isEqualTo(manifest.path("size").asInt());
        assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())))
                .isEqualTo(manifest.path("sha256").asString());
    }

    @Test
    void batchPreviewUsesFirstSpellingForNewTagsAcrossGames() throws Exception {
        long first = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        long second = success("start_game", keyed(Map.of("game", gameFields("TOURNAMENT", "1")))).path("id").asLong();
        JsonNode a = success("get_game", Map.of("gameId", first));
        JsonNode b = success("get_game", Map.of("gameId", second));
        var one = Map.of("gameId", first, "expectedUpdatedAt", a.path("updatedAt").asString(), "expectedTags", List.of(),
                "tags", Map.of("mode", "ADD", "values", List.of("Series")));
        var two = Map.of("gameId", second, "expectedUpdatedAt", b.path("updatedAt").asString(), "expectedTags", List.of(),
                "tags", Map.of("mode", "ADD", "values", List.of("series")));
        var changes = List.of(one, two);
        JsonNode preview = success("preview_game_metadata", Map.of("changes", changes));
        assertThat(preview.get(0).path("tags").get(0).asString()).isEqualTo("Series");
        assertThat(preview.get(1).path("tags").get(0).asString()).isEqualTo("Series");
        success("update_game_metadata", keyed(Map.of("changes", changes)));
        assertThat(success("get_game", Map.of("gameId", second)).path("tags").get(0).path("name").asString()).isEqualTo("Series");
    }

    @Test
    void tagMergeRacingMetadataRollsBackTheDeadlockVictimAndItsReceipt() throws Exception {
        var sourceFields = new java.util.HashMap<>(gameFields("TOURNAMENT", "5"));
        sourceFields.put("tags", List.of("Source"));
        long gameId = success("start_game", keyed(Map.of("game", sourceFields))).path("id").asLong();
        var targetFields = new java.util.HashMap<>(gameFields("TOURNAMENT", "2"));
        targetFields.put("tags", List.of("Target"));
        success("start_game", keyed(Map.of("game", targetFields)));
        JsonNode before = success("get_game", Map.of("gameId", gameId));
        JsonNode tagList = success("list_tags", Map.of()).path("items");
        long source = 0;
        for (JsonNode tag : tagList) if (tag.path("name").asString().equals("Source")) source = tag.path("id").asLong();
        final long sourceId = source;
        JsonNode preview = success("preview_tag_change", Map.of("tagId", sourceId, "newName", "Target"));
        Map<String, Object> merge = keyed(Map.of("tagId", sourceId, "newName", "Target",
                "expected", mapper.convertValue(preview, Map.class)));
        var change = Map.of("gameId", gameId, "expectedUpdatedAt", before.path("updatedAt").asString(),
                "expectedTags", List.of("Source"), "name", Map.of("value", "Reviewed name"));
        Map<String, Object> batch = keyed(Map.of("changes", List.of(change)));

        var gameLocked = new CountDownLatch(1);
        var continueBatch = new CountDownLatch(1);
        var tagLocked = new CountDownLatch(1);
        var continueMerge = new CountDownLatch(1);
        var firstLock = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            // The repository is an interface proxy; take the same real row lock on the
            // transaction-bound JDBC connection, then load the managed entity.
            jdbc.queryForObject("select id from game where id = ? for update", Long.class, gameId);
            Object found = gameRepository.findWithRoomAndVariantById(gameId);
            if (firstLock.getAndSet(false)) {
                gameLocked.countDown();
                if (!continueBatch.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("batch latch timeout");
            }
            return found;
        }).when(gameRepository).findForUpdateById(gameId);
        org.mockito.Mockito.doAnswer(invocation -> {
            tagLocked.countDown();
            if (!continueMerge.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("merge latch timeout");
            return invocation.callRealMethod();
        }).when(tagService).rename(org.mockito.ArgumentMatchers.eq(sourceId), org.mockito.ArgumentMatchers.any());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var batchFuture = pool.submit(() -> result("update_game_metadata", batch));
            try {
                assertThat(gameLocked.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var mergeFuture = pool.submit(() -> result("apply_tag_change", merge));
                try {
                    assertThat(tagLocked.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    continueBatch.countDown();
                    // Wait until the batch is actually blocked on the tag-table lock, not merely scheduled.
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                    boolean waiting = false;
                    while (System.nanoTime() < deadline && !waiting) {
                        waiting = jdbc.queryForObject("""
                                select count(*) > 0 from pg_stat_activity
                                where datname = current_database() and wait_event_type = 'Lock'
                                  and lower(query) like 'lock table tag, game_tag%'
                                """, Boolean.class);
                        if (!waiting) Thread.sleep(25);
                    }
                    assertThat(waiting).as("metadata batch waiting on the tag lock").isTrue();
                } finally {
                    continueMerge.countDown();
                }
                JsonNode batchResult = batchFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);
                JsonNode mergeResult = mergeFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(List.of(batchResult.path("isError").asBoolean(), mergeResult.path("isError").asBoolean()))
                        .containsExactlyInAnyOrder(true, false);
                JsonNode failure = batchResult.path("isError").asBoolean() ? batchResult : mergeResult;
                assertThat(failure.path("structuredContent").path("code").asString()).isEqualTo("CONFLICT");
                String batchKey = batch.get("idempotencyKey").toString();
                String mergeKey = merge.get("idempotencyKey").toString();
                assertThat(jdbc.queryForObject("select count(*) from mcp_operation where idempotency_key = cast(? as uuid)",
                        Integer.class, batchKey)).isEqualTo(batchResult.path("isError").asBoolean() ? 0 : 1);
                assertThat(jdbc.queryForObject("select count(*) from mcp_operation where idempotency_key = cast(? as uuid)",
                        Integer.class, mergeKey)).isEqualTo(mergeResult.path("isError").asBoolean() ? 0 : 1);
                JsonNode after = success("get_game", Map.of("gameId", gameId));
                assertThat(after.path("buyIn").decimalValue()).isEqualByComparingTo(before.path("buyIn").decimalValue());
                assertThat(after.path("net").decimalValue()).isEqualByComparingTo(before.path("net").decimalValue());
                if (batchResult.path("isError").asBoolean()) {
                    assertThat(after.path("name").isNull()).isTrue();
                    assertThat(after.path("tags").get(0).path("name").asString()).isEqualTo("Target");
                    assertThat(success("apply_tag_change", merge)).isEqualTo(mergeResult.path("structuredContent"));
                } else {
                    assertThat(after.path("name").asString()).isEqualTo("Reviewed name");
                    assertThat(after.path("tags").get(0).path("name").asString()).isEqualTo("Source");
                    assertThat(success("update_game_metadata", batch)).isEqualTo(batchResult.path("structuredContent"));
                }
            } finally {
                continueBatch.countDown();
                continueMerge.countDown();
            }
        }
    }

    @Test
    void tagColorsUseDurableReceiptsAndInvalidateStaleTagPreviews() throws Exception {
        var fields = new java.util.HashMap<>(gameFields("TOURNAMENT", "1"));
        fields.put("tags", List.of("Colored"));
        long id = success("start_game", keyed(Map.of("game", fields))).path("id").asLong();
        long tag = success("get_game", Map.of("gameId", id)).path("tags").get(0).path("id").asLong();
        Map<String, Object> blue = keyed(Map.of("tagId", tag, "color", "BLUE"));
        JsonNode original = success("set_tag_color", blue);
        assertThat(original.path("color").asString()).isEqualTo("BLUE");
        JsonNode preview = success("preview_tag_change", Map.of("tagId", tag, "newName", "Renamed"));
        assertThat(preview.path("source").path("color").asString()).isEqualTo("BLUE");
        success("set_tag_color", keyed(Map.of("tagId", tag, "color", "TEAL")));
        assertError("apply_tag_change", keyed(Map.of("tagId", tag, "newName", "Renamed",
                "expected", mapper.convertValue(preview, Map.class))), "MCP_STALE_METADATA");
        assertThat(success("set_tag_color", blue)).isEqualTo(original);
        assertThat(success("get_game", Map.of("gameId", id)).path("tags").get(0).path("color").asString()).isEqualTo("TEAL");
        var conflict = new java.util.HashMap<>(blue);
        conflict.put("color", "RED");
        assertError("set_tag_color", conflict, "MCP_IDEMPOTENCY_CONFLICT");
        assertError("set_tag_color", keyed(Map.of("tagId", tag, "color", "#ffffff")), "VALIDATION_FAILED");
        assertError("set_tag_color", keyed(Map.of("tagId", 99999999, "color", "BLUE")), "NOT_FOUND");
        assertThat(success("set_tag_color", keyed(Map.of("tagId", tag))).has("color")).isFalse();
        JsonNode current = success("preview_tag_change", Map.of("tagId", tag, "newName", "Renamed"));
        success("apply_tag_change", keyed(Map.of("tagId", tag, "newName", "Renamed", "expected", mapper.convertValue(current, Map.class))));
        String backup = client.send(HttpRequest.newBuilder(uri("/backup")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(restPostJson("/backup/restore?replace=true", backup).path("restored").asBoolean()).isTrue();
        assertError("set_tag_color", blue, "MCP_OPERATION_INVALIDATED");
    }

    @FunctionalInterface interface Call { JsonNode call() throws Exception; }

    private List<JsonNode> parallel(List<Call> calls) throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<JsonNode>>();
            for (Call call : calls) futures.add(pool.submit(() -> { gate.await(); return call.call(); }));
            gate.countDown();
            var results = new ArrayList<JsonNode>();
            for (var future : futures) {
                JsonNode result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse();
                results.add(result);
            }
            return results;
        }
    }

    private Map<String, Object> gameFields(String type, String amount) {
        return Map.of("playedOn", "2026-01-19", "roomId", room, "gameType", type, "buyIn", new BigDecimal(amount));
    }

    private Map<String, Object> keyed(Map<String, Object> args) {
        var request = new java.util.LinkedHashMap<>(args);
        request.put("idempotencyKey", UUID.randomUUID().toString());
        return request;
    }

    private JsonNode assertError(String name, Map<String, Object> args, String code) throws Exception {
        JsonNode result = result(name, args);
        assertThat(result.path("isError").asBoolean()).as(result.toString()).isTrue();
        assertThat(result.path("structuredContent").path("code").asString()).isEqualTo(code);
        return result;
    }

    private void assertParity(JsonNode value, String path) throws Exception {
        // A fresh entity can serialize BigDecimal.ZERO as 0; reading NUMERIC(12,2) gives 0.00.
        // Compare JSON values, not the parser's choice of integer versus decimal node.
        org.skyscreamer.jsonassert.JSONAssert.assertEquals(value.toString(), restGet(path).toString(), true);
    }

    private JsonNode success(String name, Map<String, Object> args) throws Exception {
        JsonNode result = result(name, args);
        assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse();
        JsonNode value = result.path("structuredContent");
        assertThat(mapper.readTree(result.path("content").get(0).path("text").asString())).isEqualTo(value);
        return value;
    }

    private JsonNode result(String name, Map<String, Object> args) throws Exception {
        return rpc("tools/call", Map.of("name", name, "arguments", args)).path("result");
    }

    private JsonNode rpc(String method, Map<String, Object> params) throws Exception {
        var request = HttpRequest.newBuilder(uri("/mcp"))
                .header("Authorization", "Bearer " + McpApiTests.TOKEN)
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-06-18").POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private JsonNode restGet(String path) throws Exception {
        return mapper.readTree(client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode restPost(String path, Object body) throws Exception { return restPostJson(path, mapper.writeValueAsString(body)); }

    private JsonNode restPostJson(String path, String body) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        return mapper.readTree(response.body());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
}

