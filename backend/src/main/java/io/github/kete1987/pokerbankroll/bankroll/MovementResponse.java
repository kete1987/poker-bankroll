package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.room.Room;
import org.jspecify.annotations.Nullable;

public record MovementResponse(
        long id,
        LocalDate occurredOn,
        MovementType type,
        @Schema(description = "Null for a movement of the bankroll as a whole")
        @Nullable RoomRef room,
        @Schema(description = "Currency of the amount: the one of the room, or its own without a room")
        String currencyCode,
        BigDecimal amount,
        @Schema(description = "Effect on the bankroll: the amount, negative for a withdrawal")
        BigDecimal signedAmount,
        @Nullable String notes,
        Instant createdAt,
        Instant updatedAt) {

    static MovementResponse of(BankrollMovement movement) {
        Room room = movement.getRoom();
        return new MovementResponse(
                movement.getId(),
                movement.getOccurredOn(),
                movement.getType(),
                room == null ? null : new RoomRef(room.getId(), room.getName()),
                movement.getEffectiveCurrencyCode(),
                movement.getAmount(),
                movement.getSignedAmount(),
                movement.getNotes(),
                movement.getCreatedAt(),
                movement.getUpdatedAt());
    }
}
