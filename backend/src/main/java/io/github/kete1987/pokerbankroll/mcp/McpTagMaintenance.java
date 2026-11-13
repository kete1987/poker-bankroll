package io.github.kete1987.pokerbankroll.mcp;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.tag.TagRequest;
import io.github.kete1987.pokerbankroll.tag.TagResponse;
import io.github.kete1987.pokerbankroll.tag.TagService;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preview and guard the exact tag definitions and game associations under table locks. */
@Service
class McpTagMaintenance {
    private final TagService tags;
    private final JdbcTemplate jdbc;
    McpTagMaintenance(TagService tags, JdbcTemplate jdbc) { this.tags = tags; this.jdbc = jdbc; }

    @Transactional
    Preview inspect(long tagId, @Nullable String newName) {
        jdbc.execute("lock table tag, game_tag in share row exclusive mode");
        TagResponse source = tags.get(tagId);
        TagResponse target = newName == null ? null : tags.previewTarget(tagId, newName);
        String fingerprint = fingerprint(tagId);
        String targetFingerprint = target == null ? "" : fingerprint(target.id());
        return new Preview(newName == null ? "DELETE" : "RENAME", newName == null ? null : newName.strip(),
                source, target, fingerprint, targetFingerprint);
    }

    /** Stream IDs in order so the fingerprint does not materialize an unbounded history. */
    private String fingerprint(long tagId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            jdbc.query(connection -> {
                var statement = connection.prepareStatement("select game_id from game_tag where tag_id = ? order by game_id");
                statement.setLong(1, tagId);
                statement.setFetchSize(500);
                return statement;
            }, (org.springframework.jdbc.core.RowCallbackHandler) row ->
                    digest.update(ByteBuffer.allocate(Long.BYTES).putLong(row.getLong(1)).array()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
        }
    }

    @Transactional
    Result execute(long tagId, @Nullable String newName, Preview expected) {
        Preview current = inspect(tagId, newName);
        if (!current.equals(expected)) throw new ApiException(ErrorCode.MCP_STALE_METADATA);
        TagResponse result = null;
        if (newName == null) tags.delete(tagId);
        else result = tags.rename(tagId, new TagRequest(newName));
        return new Result(current, result);
    }

    record Preview(String operation, @Nullable String newName, TagResponse source, @Nullable TagResponse target,
            String fingerprint, String targetFingerprint) { }
    record Result(Preview before, @Nullable TagResponse result) { }
}
