package io.github.kete1987.pokerbankroll.variant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.catalog.GameType;

/** Data to create a user-defined variant. */
public record VariantCreateRequest(
        @NotNull GameType gameType,
        @NotBlank @Size(max = 100) String name) {
}
