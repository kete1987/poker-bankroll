package io.github.kete1987.pokerbankroll.variant;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Data to update a variant. The game type of a variant cannot change. */
public record VariantUpdateRequest(
        @Schema(description = "New name of a user-defined variant. Must be omitted (or null) for built-in variants.")
        @Nullable @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "{jakarta.validation.constraints.NotBlank.message}")
        String name,

        @Schema(description = "Inactive variants keep their history but are not offered for new games")
        @NotNull Boolean active) {
}
