package io.github.kete1987.pokerbankroll.game;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.github.kete1987.pokerbankroll.tag.TagName;
import org.jspecify.annotations.Nullable;

/**
 * An explicit, guarded change to one game's metadata. Empty strings clear name/notes; a variant
 * change with a null {@code variantId} clears the variant.
 */
public record GameMetadataChange(
        @NotNull @Positive Long gameId,
        @NotNull Instant expectedUpdatedAt,
        @NotNull @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> expectedTags,
        @Valid @Nullable TextChange name,
        @Valid @Nullable NotesChange notes,
        @Valid @Nullable TagsChange tags,
        // Left out when omitted, so receipts of changes made before it keep their fingerprint.
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid @Nullable VariantChange variant) {
    public record TextChange(@NotNull @Size(max = 150) String value) { }
    public enum NotesMode { REPLACE, APPEND }
    public record NotesChange(@NotNull NotesMode mode, @NotNull @Size(max = 5000) String value) { }
    public enum TagsMode { REPLACE, ADD, REMOVE }
    public record TagsChange(@NotNull TagsMode mode,
            @NotNull @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> values) { }
    /** A variant of the game's own type, or {@code null} to clear it. */
    public record VariantChange(@JsonInclude(JsonInclude.Include.ALWAYS) @Positive @Nullable Long variantId) { }
}
