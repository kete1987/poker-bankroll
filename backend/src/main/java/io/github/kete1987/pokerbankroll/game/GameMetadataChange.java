package io.github.kete1987.pokerbankroll.game;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.github.kete1987.pokerbankroll.tag.TagName;
import org.jspecify.annotations.Nullable;

/** An explicit, guarded change to one game's metadata. Empty strings clear name/notes. */
public record GameMetadataChange(
        @NotNull @Positive Long gameId,
        @NotNull Instant expectedUpdatedAt,
        @NotNull @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> expectedTags,
        @Valid @Nullable TextChange name,
        @Valid @Nullable NotesChange notes,
        @Valid @Nullable TagsChange tags) {
    public record TextChange(@NotNull @Size(max = 150) String value) { }
    public enum NotesMode { REPLACE, APPEND }
    public record NotesChange(@NotNull NotesMode mode, @NotNull @Size(max = 5000) String value) { }
    public enum TagsMode { REPLACE, ADD, REMOVE }
    public record TagsChange(@NotNull TagsMode mode,
            @NotNull @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> values) { }
}
