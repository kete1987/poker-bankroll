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
    private final HttpClient client = HttpClient.newHttpClient();
    long room;

    @BeforeEach
    void createRoom() { room = insertRoom("Write test", "EUR"); }

    @Test
    void writesAllSupportedOperationsWithRestParityAndAccurateAnnotations() throws Exception {
        JsonNode tools = rpc("tools/list", Map.of()).path("result").path("tools");
        assertThat(tools.size()).isEqualTo(18);
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

