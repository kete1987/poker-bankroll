package io.github.kete1987.pokerbankroll.mcp;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.github.kete1987.pokerbankroll.backup.BackupService;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One bounded in-memory snapshot, split into bounded chunks for stateless MCP clients. */
@Component
@ConditionalOnProperty(prefix = "poker-bankroll.mcp", name = "enabled", havingValue = "true")
class McpBackupTools {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int CHUNK = 256 * 1024;
    private static final long LIFETIME_SECONDS = 600;
    private final BackupService backups;
    private final List<SyncToolSpecification> tools;
    private @Nullable Snapshot snapshot;

    McpBackupTools(McpToolExecutor executor, BackupService backups) {
        this.backups = backups;
        ToolAnnotations read = ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false)
                .idempotentHint(true).openWorldHint(false).build();
        tools = List.of(executor.tool("create_safety_backup", "Create a read-only, consistent JSON app backup snapshot. "
                        + "It expires after ten minutes, on server restart, or when another snapshot is created. "
                        + "Read all chunks, concatenate decoded bytes and verify SHA-256. Maximum 32 MiB; no restore is exposed.",
                        McpWriteTools.object(Map.of()), Empty.class, ToolAnnotations.builder().readOnlyHint(true)
                                .destructiveHint(false).idempotentHint(false).openWorldHint(false).build(), q -> create()),
                executor.tool("read_safety_backup_chunk", "Read one base64-encoded chunk from a safety backup by snapshotId and offset. "
                        + "Offsets must equal nextOffset from the preceding chunk; verify the final SHA-256 after decoding.",
                        McpWriteTools.object(Map.of("snapshotId", Map.of("type", "string", "format", "uuid", "pattern",
                                        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
                                "offset", Map.of("type", "integer", "minimum", 0)), "snapshotId", "offset"),
                        ChunkInput.class, read, q -> chunk(q.snapshotId(), q.offset())));
    }
    List<SyncToolSpecification> specifications() { return tools; }
    private synchronized Manifest create() {
        var file = backups.backup();
        if (file.content().length > MAX_BYTES) throw new ApiException(ErrorCode.BACKUP_FILE_TOO_LARGE, 32);
        UUID id = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(LIFETIME_SECONDS);
        snapshot = new Snapshot(id, file.content(), expires);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.content()));
            return new Manifest(id, file.name(), file.content().length, CHUNK, hash, expires);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private synchronized Chunk chunk(UUID id, long offset) {
        Snapshot saved = snapshot;
        if (saved == null || !Instant.now().isBefore(saved.expires())) {
            snapshot = null;
            throw new ApiException(ErrorCode.MCP_BACKUP_EXPIRED);
        }
        if (!saved.id().equals(id)) throw new ApiException(ErrorCode.MCP_BACKUP_EXPIRED);
        if (offset > saved.bytes().length || offset % CHUNK != 0 || offset == saved.bytes().length && offset != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        int end = (int) Math.min(offset + CHUNK, saved.bytes().length);
        String content = Base64.getEncoder().encodeToString(Arrays.copyOfRange(saved.bytes(), (int) offset, end));
        return new Chunk(id, offset, content, end, end == saved.bytes().length);
    }
    record Empty() { }
    record ChunkInput(@NotNull UUID snapshotId, @NotNull @Min(0) Long offset) { }
    record Manifest(UUID snapshotId, String filename, int size, int chunkSize, String sha256, Instant expiresAt) { }
    record Chunk(UUID snapshotId, long offset, String base64, int nextOffset, boolean complete) { }
    private record Snapshot(UUID id, byte[] bytes, Instant expires) { }
}
