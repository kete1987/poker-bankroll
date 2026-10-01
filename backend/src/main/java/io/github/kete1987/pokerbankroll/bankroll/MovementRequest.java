package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Data to create or update a bankroll movement. */
@RoomOrCurrency
@MovementAmountSign
public record MovementRequest(
        @NotNull LocalDate occurredOn,

        @NotNull MovementType type,

        @Schema(description = "Room the movement belongs to; the amount is in its currency. "
                + "Omit it, and give a currency, for a movement of the bankroll as a whole")
        @Nullable Long roomId,

        @Schema(description = "Only for a movement without a room, e.g. EUR")
        @Nullable String currencyCode,

        @Schema(description = "Greater than zero: the type gives the direction (a withdrawal subtracts). "
                + "Only an ADJUSTMENT can be negative")
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,

        @Nullable @Size(max = 5000) String notes) {

    boolean hasCurrency() {
        return currencyCode != null && !currencyCode.isBlank();
    }
}
