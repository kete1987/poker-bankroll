package io.github.kete1987.pokerbankroll.game;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replace only the notes; an empty string clears them. */
public record GameNotesRequest(@NotNull @Size(max = 5000) String notes) {
}

