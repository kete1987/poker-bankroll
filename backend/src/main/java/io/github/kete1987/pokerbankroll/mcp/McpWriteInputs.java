package io.github.kete1987.pokerbankroll.mcp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.bankroll.MovementRequest;
import io.github.kete1987.pokerbankroll.game.FinishGameRequest;
import io.github.kete1987.pokerbankroll.game.GameNotesRequest;
import io.github.kete1987.pokerbankroll.game.GameRequest;
import io.github.kete1987.pokerbankroll.game.GameTagsRequest;
import io.github.kete1987.pokerbankroll.game.RebuyRequest;
import io.github.kete1987.pokerbankroll.tag.TagName;
import org.jspecify.annotations.Nullable;

/** MCP envelopes reuse the application's request records, including their class-level constraints. */
final class McpWriteInputs {
    private McpWriteInputs() { }

    record Start(@NotNull UUID idempotencyKey, @Valid @Nullable GameRequest game,
            @Positive @Nullable Long templateId, @Nullable LocalDate playedOn, @Nullable LocalTime playedAt,
            @Size(max = 5000) @Nullable String notes,
            @Size(max = GameRequest.MAX_TAGS) @Nullable List<@NotNull @TagName String> tags) {
        @AssertTrue(message = "{McpGameSource}")
        public boolean isSourceValid() {
            return game != null
                    ? templateId == null && playedOn == null && playedAt == null && notes == null && tags == null
                    : templateId != null && playedOn != null;
        }
    }

    record Finish(@NotNull UUID idempotencyKey, @NotNull @Positive Long gameId,
            @Valid @NotNull FinishGameRequest result) { }

    record ReEntry(@NotNull UUID idempotencyKey, @NotNull @Positive Long gameId) { }

    record Rebuy(@NotNull UUID idempotencyKey, @NotNull @Positive Long gameId,
            @Valid @NotNull RebuyRequest rebuy) { }

    record Movement(@NotNull UUID idempotencyKey, @Valid @NotNull MovementRequest movement) { }

    record Notes(@NotNull UUID idempotencyKey, @NotNull @Positive Long gameId,
            @Valid @NotNull GameNotesRequest update) { }

    record Tags(@NotNull UUID idempotencyKey, @NotNull @Positive Long gameId,
            @Valid @NotNull GameTagsRequest update) { }
}

