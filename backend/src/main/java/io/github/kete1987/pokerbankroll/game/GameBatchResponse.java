package io.github.kete1987.pokerbankroll.game;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** The games recorded by a {@link GameBatchRequest}. */
public record GameBatchResponse(
        @Schema(description = "In the order they were sent")
        List<GameResponse> games) {
}
