package io.github.kete1987.pokerbankroll.tag;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/** Data to rename a tag. */
public record TagRequest(
        @Schema(description = "New name, from 1 to 40 characters without semicolons; surrounding spaces are "
                + "removed. When another tag already has it (ignoring case), this tag is merged into that one")
        @NotNull @TagName String name) {
}
