package io.github.kete1987.pokerbankroll.backup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The file of a backup: one JSON document that says the version of its format in
 * {@code formatVersion}.
 *
 * <p>A backup is always written in the {@linkplain #CURRENT_VERSION current version}; every version
 * up to it can be read, each by its own records, and a newer one is refused. Adding version 2 is:
 * a {@code BackupV2} class, {@link #CURRENT_VERSION} and {@link #write} moved to it, and a case in
 * {@link #read} — {@link BackupV1} stays as it is, to keep reading the files of today.
 */
final class BackupFormat {

    static final int CURRENT_VERSION = BackupV1.VERSION;

    /**
     * Its own mapper, not the one of the API: the format of the file must not change with the
     * configuration of the application. Dates are ISO-8601 text, amounts are plain decimal numbers
     * read as {@code BigDecimal} (never through a {@code double}), images are Base64, and what is
     * missing is left out.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            // A newer build of the same format may add properties: they are ignored.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    private BackupFormat() {
    }

    static byte[] write(BackupData data) {
        return JSON.writeValueAsBytes(BackupV1.File.of(data));
    }

    /**
     * Reads a file of any version up to the current one.
     *
     * @throws ApiException {@code BACKUP_FILE_MALFORMED} when it is not a backup or a value cannot
     *                      be read (saying where), {@code BACKUP_FORMAT_TOO_NEW} when it was made
     *                      by a newer version of the application
     */
    static BackupData read(byte[] content) {
        try {
            // Only the version is read first: the rest depends on it.
            Header header = JSON.readValue(content, Header.class);
            Integer version = header == null ? null : header.formatVersion();
            if (version == null || version < 1) {
                throw malformed(in("formatVersion"));
            }
            if (version > CURRENT_VERSION) {
                throw new ApiException(ErrorCode.BACKUP_FORMAT_TOO_NEW,
                        String.valueOf(version), String.valueOf(CURRENT_VERSION));
            }
            // One case per version, each with its own records.
            return switch (version) {
                case BackupV1.VERSION -> readV1(content);
                default -> throw new IllegalStateException("No reader for the format " + version);
            };
        } catch (JacksonException ex) {
            throw malformed(where(ex));
        }
    }

    private static BackupData readV1(byte[] content) {
        BackupV1.File file = JSON.readValue(content, BackupV1.File.class);
        String missing = file.missingList();
        if (missing != null) {
            throw malformed(in(missing));
        }
        return file.toData();
    }

    /** Only what every version has. */
    private record Header(@Nullable Integer formatVersion) {
    }

    private static ApiException malformed(MessageSourceResolvable where) {
        return new ApiException(ErrorCode.BACKUP_FILE_MALFORMED, where);
    }

    /** The value that could not be read, e.g. {@code games[12].buyIn}, or the line when it is not JSON. */
    private static MessageSourceResolvable where(JacksonException ex) {
        String path = path(ex.getPath());
        if (!path.isEmpty()) {
            return in(path);
        }
        int line = ex.getLocation() == null ? 0 : Math.max(ex.getLocation().getLineNr(), 1);
        return new DefaultMessageSourceResolvable(
                new String[] {"backup.where.line"}, new Object[] {String.valueOf(line)}, "line " + line);
    }

    private static MessageSourceResolvable in(String path) {
        return new DefaultMessageSourceResolvable(new String[] {"backup.where.value"}, new Object[] {path}, path);
    }

    private static String path(List<JacksonException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : references) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
