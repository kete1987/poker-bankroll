package io.github.kete1987.pokerbankroll.template;

import java.math.BigDecimal;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import org.jspecify.annotations.Nullable;

/** Scalar template snapshot: never populates the persistence context with room/variant entities. */
public record GameTemplateStart(long roomId, GameType gameType, Modality modality,
        @Nullable Long variantId, @Nullable String name, BigDecimal buyIn) {
}
