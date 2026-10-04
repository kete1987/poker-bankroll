package io.github.kete1987.pokerbankroll.template;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.variant.Variant;
import org.jspecify.annotations.Nullable;

public record GameTemplateResponse(
        long id,
        @Schema(description = "Null when the user gave none: clients name it after its room, variant and buy-in")
        @Nullable String label,
        RoomRef room,
        GameType gameType,
        Modality modality,
        @Nullable VariantRef variant,
        @Schema(description = "Name of the games started from it")
        @Nullable String name,
        @Schema(description = "Currency of the buy-in (the one of its room)")
        String currencyCode,
        @Schema(description = "Price of one entry. Cash game: amount brought to the table")
        BigDecimal buyIn,
        @Schema(description = "Games can be started from it: neither its room nor its variant is inactive")
        boolean usable,
        Instant createdAt,
        Instant updatedAt) {

    static GameTemplateResponse of(GameTemplate template) {
        Variant variant = template.getVariant();
        return new GameTemplateResponse(
                template.getId(),
                template.getLabel(),
                new RoomRef(template.getRoom().getId(), template.getRoom().getName()),
                template.getGameType(),
                template.getModality(),
                variant == null ? null : new VariantRef(variant.getId(), variant.getCode(), variant.getName()),
                template.getGameName(),
                template.getRoom().getCurrencyCode(),
                template.getBuyIn(),
                template.isUsable(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
