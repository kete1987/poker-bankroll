package io.github.kete1987.pokerbankroll.room;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Data to create or update a room. */
public record RoomRequest(
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "ISO 4217 code of one of the currencies of the catalog", example = "EUR")
        @NotNull @Pattern(regexp = "[A-Z]{3}")
        String currencyCode,

        @Schema(description = "Inactive rooms keep their history but are not offered for new games. Defaults to true.")
        @Nullable Boolean active) {
}
