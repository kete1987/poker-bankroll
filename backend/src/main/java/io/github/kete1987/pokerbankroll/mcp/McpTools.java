package io.github.kete1987.pokerbankroll.mcp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import jakarta.validation.Validator;

import io.github.kete1987.pokerbankroll.bankroll.BankrollService;
import io.github.kete1987.pokerbankroll.catalog.CatalogService;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.game.GameService;
import io.github.kete1987.pokerbankroll.game.GameSort;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.room.RoomService;
import io.github.kete1987.pokerbankroll.stats.GroupBy;
import io.github.kete1987.pokerbankroll.stats.StatsService;
import io.github.kete1987.pokerbankroll.stats.TimePeriod;
import io.github.kete1987.pokerbankroll.tag.TagService;
import io.github.kete1987.pokerbankroll.variant.VariantService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** An explicit read-only allowlist. The SDK handles MCP; services own all domain calculations. */
@Component
@ConditionalOnProperty(prefix = "poker-bankroll.mcp", name = "enabled", havingValue = "true")
final class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);
    static final int MAX_RESULT_BYTES = 1024 * 1024;

    private final JsonMapper mapper;
    private final JsonMapper inputMapper;
    private final Validator validator;
    private final JsonSchemaValidator schemas = McpJsonDefaults.getSchemaValidator();
    private final MessageSource messages;
    private final List<SyncToolSpecification> specifications;

    McpTools(JsonMapper mapper, Validator validator, MessageSource messages, GameService games, StatsService stats,
            BankrollService bankroll, RoomService rooms, VariantService variants, TagService tags, CatalogService catalog) {
        this.mapper = mapper;
        this.inputMapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
        this.validator = validator;
        this.messages = messages;

        Map<String, Object> paging = Map.of("page", integer(0, 1000000, "Zero-based page; default 0"),
                "size", integer(1, 200, "Page size; default 50"));
        Map<String, Object> dates = Map.of("from", date("Inclusive first day, yyyy-MM-dd"),
                "to", date("Inclusive last day, yyyy-MM-dd"));
        Map<String, Object> roomIds = Map.of("roomIds", ids("Any of these rooms; empty means all"));
        Map<String, Object> filters = merge(dates, roomIds, Map.of(
                "gameTypes", Map.of("type", "array", "maxItems", 3, "items", enumeration(GameType.values())),
                "modality", enumeration(Modality.values()),
                "variantIds", ids("Any of these variants; empty means all"),
                "tagIds", ids("Any of these tags; empty means all"),
                "currencyCode", Map.of("type", "string", "pattern", "^[A-Z]{3}$", "description", "Room currency code"),
                "text", Map.of("type", "string", "maxLength", 500, "description", "Literal name/notes substring, ignoring case")));

        var tools = new ArrayList<SyncToolSpecification>();
        tools.add(tool("get_catalog", "Discover supported currencies, game types and modalities. Codes are not translated.",
                Map.of(), List.of(), q -> catalog.catalog()));
        tools.add(tool("list_rooms", "Discover room IDs, names, currencies and active state, including inactive rooms by default.",
                merge(paging, Map.of("active", Map.of("type", "boolean"))), List.of(),
                q -> page(rooms.list(q.active()), q)));
        tools.add(tool("list_variants", "Discover variant IDs, codes and custom names; filter by gameType and active state.",
                merge(paging, Map.of("active", Map.of("type", "boolean"), "gameType", enumeration(GameType.values()))), List.of(),
                q -> page(variants.list(q.gameType(), q.active()), q)));
        tools.add(tool("list_tags", "Discover tag IDs, names and counts of games (finished and in play).", paging, List.of(),
                q -> page(tags.list(), q)));
        tools.add(tool("list_games", "Recorded games with their original currency. Filters combine with AND; values within a list with OR. "
                        + "Includes finished and in-play games by default. Follow totalPages; a page is not a total.",
                merge(filters, paging, Map.of("status", enumeration(GameStatus.values()), "sort", Map.of("type", "string",
                        "maxLength", 40, "description", "field,asc|desc; field: playedOn, net, buyIn, prize, won, createdAt. Default playedOn,desc; ties use date/id."))),
                List.of(), q -> games.list(q.filter(), q.pageNumber(), q.pageSize(), GameSort.parse(q.gameSort()))));
        tools.add(tool("get_stats_summary", "Results overall and per game type, per currency and converted. "
                        + "Only finished games count in result statistics; in-play figures are reported apart. ROI is a fraction. Report missingRates.",
                filters, List.of(), q -> stats.summary(q.filter())));
        tools.add(tool("get_stats_groups", "Group finished-game statistics by period or dimension. Weeks start Monday; period cumulativeNet "
                        + "starts at zero within the filtered range. TAG groups overlap. Preserves currencies, converted and missingRates.",
                merge(filters, Map.of("groupBy", enumeration(GroupBy.values()), "byGameType", Map.of("type", "boolean"))),
                List.of("groupBy"), q -> stats.groups(q.filter(), q.groupBy(), Boolean.TRUE.equals(q.byGameType()))));
        tools.add(tool("get_bankroll_summary", "Poker bankroll and result per currency and room, plus converted values and missingRates. "
                        + "With from: change during the period, not closing balance. Without from: balance as of to or today. "
                        + "roomIds excludes unassigned movements. In-play games count. It is not the room account balance.",
                merge(dates, roomIds), List.of(), q -> bankroll.summary(q.from(), q.to(), q.roomIds())));
        tools.add(tool("get_bankroll_evolution", "Bankroll by period including startingBankroll before from; in-play games count. "
                        + "Includes rooms, currencies, converted values and missingRates. Balances use period-end exchange rates; "
                        + "exchange differences can change converted balances without activity. Weeks start Monday.",
                merge(dates, roomIds, Map.of("groupBy", enumeration(TimePeriod.values()))), List.of("groupBy"),
                q -> bankroll.evolution(TimePeriod.valueOf(q.groupBy().name()), q.from(), q.to(), q.roomIds())));
        specifications = List.copyOf(tools);
    }

    List<SyncToolSpecification> specifications() { return specifications; }

    private SyncToolSpecification tool(String name, String description, Map<String, Object> properties,
            List<String> required, Function<McpQuery, Object> action) {
        var schema = Map.<String, Object>of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
        Tool tool = Tool.builder().name(name).description(description).inputSchema(schema)
                .annotations(ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false)
                        .idempotentHint(true).openWorldHint(false).build()).build();
        return new SyncToolSpecification(tool, (context, request) -> {
            Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
            var validation = schemas.validate(schema, arguments);
            if (!validation.valid()) {
                return error(ErrorCode.VALIDATION_FAILED, List.of(Map.of("field", "arguments", "code", "Schema",
                        "message", validation.errorMessage())));
            }
            McpQuery query;
            try {
                query = inputMapper.convertValue(arguments, McpQuery.class);
            }
            catch (IllegalArgumentException | tools.jackson.core.JacksonException ex) {
                return error(ErrorCode.VALIDATION_FAILED, List.of(Map.of("field", "arguments", "code", "Type",
                        "message", "Invalid parameter type or format; see the tool input schema.")));
            }
            var violations = validator.validate(query);
            if (!violations.isEmpty()) {
                return error(ErrorCode.VALIDATION_FAILED, violations.stream()
                        .map(v -> Map.of("field", v.getPropertyPath().toString(),
                                "code", v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                                "message", v.getMessage())).toList());
            }
            try {
                Object result = action.apply(query);
                String json = mapper.writeValueAsString(result);
                if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
                    return error(ErrorCode.MCP_RESULT_TOO_LARGE, List.of());
                }
                return CallToolResult.builder().structuredContent(result).addTextContent(json).isError(false).build();
            }
            catch (ApiException ex) {
                return error(ex.getCode(), List.of(), ex.getArgs());
            }
            catch (Exception ex) {
                log.error("MCP tool {} failed", name, ex);
                return error(ErrorCode.INTERNAL_ERROR, List.of());
            }
        });
    }

    private CallToolResult error(ErrorCode code, List<?> violations, Object... args) {
        Map<String, Object> problem = Map.of("code", code.name(), "detail",
                messages.getMessage(code.messageKey(), args, code.name(), Locale.ENGLISH), "errors", violations);
        return CallToolResult.builder().isError(true).structuredContent(problem)
                .addTextContent(mapper.writeValueAsString(problem)).build();
    }

    private static <T> PageResponse<T> page(List<T> values, McpQuery query) {
        int size = query.pageSize();
        int from = (int) Math.min((long) query.pageNumber() * size, values.size());
        int to = Math.min(from + size, values.size());
        return new PageResponse<>(values.subList(from, to), query.pageNumber(), size, values.size(),
                (values.size() + size - 1) / size);
    }

    @SafeVarargs
    private static Map<String, Object> merge(Map<String, Object>... parts) {
        var result = new LinkedHashMap<String, Object>();
        for (var part : parts) result.putAll(part);
        return result;
    }

    private static Map<String, Object> enumeration(Enum<?>[] values) {
        return Map.of("type", "string", "enum", Arrays.stream(values).map(Enum::name).toList());
    }

    private static Map<String, Object> date(String description) {
        return Map.of("type", "string", "format", "date", "description", description);
    }

    private static Map<String, Object> ids(String description) {
        return Map.of("type", "array", "maxItems", 100, "items", Map.of("type", "integer", "minimum", 1), "description", description);
    }

    private static Map<String, Object> integer(int min, int max, String description) {
        return Map.of("type", "integer", "minimum", min, "maximum", max, "description", description);
    }
}
