package io.github.kete1987.pokerbankroll.tag;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagResponse(
        long id,
        String name,
        @Schema(description = "Games that have the tag, in play or finished")
        long games) {
}
