package io.github.kete1987.pokerbankroll.game;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Several games recorded at once, all of them or none. */
public record GameBatchRequest(
        @Schema(description = "The games, recorded in this order: each one as `POST /games` records it")
        @NotNull @Size(min = 1, max = GameBatchRequest.MAX_GAMES) List<@NotNull @Valid GameRequest> games) {

    /** Games recorded by one request at most. */
    public static final int MAX_GAMES = 50;
}
