package io.github.kete1987.pokerbankroll.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Replaces only the color; omitting it or sending null restores the neutral default. */
public record TagColorRequest(
        @Schema(description = "Palette code; null or omitted restores the neutral default")
        @Nullable TagColor color) {
}
