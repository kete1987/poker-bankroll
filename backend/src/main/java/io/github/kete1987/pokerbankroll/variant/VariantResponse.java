package io.github.kete1987.pokerbankroll.variant;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.jspecify.annotations.Nullable;

public record VariantResponse(
        long id,
        GameType gameType,
        @Schema(description = "Code of a built-in variant, translated by the client; null for user-defined ones")
        @Nullable String code,
        @Schema(description = "Name of a user-defined variant; null for built-in ones")
        @Nullable String name,
        boolean builtIn,
        boolean active,
        @Schema(description = "The variant is used by games: it cannot be deleted")
        boolean inUse) {

    static VariantResponse of(Variant variant, boolean inUse) {
        return new VariantResponse(variant.getId(), variant.getGameType(), variant.getCode(), variant.getName(),
                variant.isBuiltIn(), variant.isActive(), inUse);
    }
}
