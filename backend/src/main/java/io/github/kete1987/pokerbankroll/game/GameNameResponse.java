package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import org.jspecify.annotations.Nullable;

/**
 * A name given to recorded games, to suggest it for the next one, with what the most recent game
 * of that name (latest date, then latest id) had, so the client can fill it in. The buy-in is only
 * worth filling in for a room of the same currency.
 */
public record GameNameResponse(
        @Schema(description = "As it was written in the most recent game of that name; names that "
                + "differ only in case or surrounding spaces are the same one")
        String name,
        @Schema(description = "How many games have that name")
        long games,
        GameType gameType,
        Modality modality,
        BigDecimal buyIn,
        @Schema(description = "Currency of the buy-in: the one of the room of that game")
        String currencyCode,
        @Nullable VariantRef variant) {

    static GameNameResponse of(GameRepository.NameUse use) {
        Long variantId = use.getVariantId();
        return new GameNameResponse(
                use.getName(),
                use.getGames(),
                GameType.valueOf(use.getGameType()),
                Modality.valueOf(use.getModality()),
                use.getBuyIn(),
                use.getCurrencyCode(),
                variantId == null ? null : new VariantRef(variantId, use.getVariantCode(), use.getVariantName()));
    }
}
