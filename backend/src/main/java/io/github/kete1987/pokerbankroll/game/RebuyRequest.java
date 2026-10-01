package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** More money brought to the table of a cash game in play. */
public record RebuyRequest(
        @Schema(description = "Amount added to the buy-in of the game")
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
