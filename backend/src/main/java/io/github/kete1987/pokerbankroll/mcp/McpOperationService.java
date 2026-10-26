package io.github.kete1987.pokerbankroll.mcp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The unique key serializes retries across sessions, processes and restarts in PostgreSQL. */
@Service
class McpOperationService {

    enum ResourceType { GAME, MOVEMENT }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper mapper;

    McpOperationService(JdbcTemplate jdbc, PlatformTransactionManager manager, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.mapper = mapper.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    }

    <T> OperationResult execute(String tool, UUID key, Object parameters, ResourceType type,
            Supplier<T> action, ToLongFunction<T> resourceId) {
        String hash = fingerprint(parameters);
        return java.util.Objects.requireNonNull(transaction.execute(status -> {
            int inserted = jdbc.update("""
                    insert into mcp_operation (idempotency_key, tool_name, request_hash, resource_type)
                    values (?, ?, ?, ?) on conflict (idempotency_key) do nothing
                    """, key, tool, hash, type.name());
            if (inserted == 0) {
                // ON CONFLICT waits for the first transaction. Its completed receipt is now visible.
                return jdbc.queryForObject("""
                        select * from mcp_operation where idempotency_key = ?
                        """, (row, number) -> {
                    if (!tool.equals(row.getString("tool_name")) || !hash.equals(row.getString("request_hash"))) {
                        throw new ApiException(ErrorCode.MCP_IDEMPOTENCY_CONFLICT);
                    }
                    if (row.getBoolean("invalidated")) {
                        throw new ApiException(ErrorCode.MCP_OPERATION_INVALIDATED);
                    }
                    String saved = row.getString("result");
                    if (saved == null) throw new IllegalStateException("Incomplete MCP operation receipt");
                    return new OperationResult(mapper.readTree(saved), new Receipt(key, tool, type,
                            row.getLong("resource_id"), row.getTimestamp("completed_at").toInstant(), true));
                }, key);
            }
            T resource = action.get();
            long id = resourceId.applyAsLong(resource);
            Instant completed = jdbc.queryForObject("""
                    update mcp_operation set resource_id = ?, result = cast(? as jsonb), completed_at = clock_timestamp()
                    where idempotency_key = ? returning completed_at
                    """, (row, number) -> row.getTimestamp(1).toInstant(), id, mapper.writeValueAsString(resource), key);
            return new OperationResult(resource, new Receipt(key, tool, type, id, completed, false));
        }));
    }

    @Transactional(readOnly = true)
    PageResponse<OperationTrace> list(McpQuery query) {
        var args = new ArrayList<Object>();
        String where = " where completed_at is not null";
        if (query.resourceType() != null) {
            where += " and resource_type = ?";
            args.add(query.resourceType().name());
        }
        if (query.resourceId() != null) {
            where += " and resource_id = ?";
            args.add(query.resourceId());
        }
        long total = java.util.Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from mcp_operation" + where, Long.class, args.toArray()));
        args.add(query.pageSize());
        args.add((long) query.pageNumber() * query.pageSize());
        List<OperationTrace> items = jdbc.query("""
                select idempotency_key, tool_name, resource_type, resource_id, created_at, completed_at, invalidated
                from mcp_operation
                """ + where + " order by created_at desc, idempotency_key limit ? offset ?",
                (row, number) -> new OperationTrace(row.getObject("idempotency_key", UUID.class),
                        row.getString("tool_name"), ResourceType.valueOf(row.getString("resource_type")),
                        row.getObject("resource_id", Long.class), row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("completed_at").toInstant(), row.getBoolean("invalidated")), args.toArray());
        return new PageResponse<>(items, query.pageNumber(), query.pageSize(), total,
                (int) ((total + query.pageSize() - 1) / query.pageSize()));
    }

    private String fingerprint(Object parameters) {
        // Canonicalize object key order and decimal scale: 1.20 and 1.2 represent the same request.
        String canonical = mapper.writeValueAsString(canonical(mapper.convertValue(parameters, Map.class)));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
        }
    }

    private static @Nullable Object canonical(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(key.toString(), canonical(item)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(McpOperationService::canonical).toList();
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros();
        return value;
    }

    record OperationResult(Object resource, Receipt receipt) { }
    record Receipt(UUID idempotencyKey, String tool, ResourceType resourceType, long resourceId,
            Instant completedAt, boolean replayed) { }
    record OperationTrace(UUID idempotencyKey, String tool, ResourceType resourceType, @Nullable Long resourceId,
            Instant createdAt, Instant completedAt, boolean invalidated) { }
}

