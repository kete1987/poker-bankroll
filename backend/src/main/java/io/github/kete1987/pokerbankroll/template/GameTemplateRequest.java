package io.github.kete1987.pokerbankroll.template;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import org.jspecify.annotations.Nullable;

/** Data to create or update a template. The buy-in is in the currency of the room. */
public record GameTemplateRequest(
        @Schema(description = "Optional; without it, clients name the template after its room, variant and buy-in")
        @Nullable @Size(max = 80) String label,

        @NotNull Long roomId,

        @NotNull GameType gameType,

        @Schema(description = "Defaults to NLHE")
        @Nullable Modality modality,

        @Schema(description = "Optional; must be a variant of the game type")
        @Nullable Long variantId,

        @Schema(description = "Name of the games started from it")
        @Nullable @Size(max = 150) String name,

        @Schema(description = "Price of one entry. Cash game: amount brought to the table")
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal buyIn) {

    public Modality modalityOrDefault() {
        return modality == null ? Modality.NLHE : modality;
    }
}
