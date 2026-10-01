package io.github.kete1987.pokerbankroll.room;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record RoomResponse(
        long id,
        String name,
        String currencyCode,
        boolean active,
        @Schema(description = "The room has games: it cannot be deleted and its currency cannot change")
        boolean inUse,
        Instant createdAt,
        Instant updatedAt) {

    static RoomResponse of(Room room, boolean inUse) {
        return new RoomResponse(room.getId(), room.getName(), room.getCurrencyCode(), room.isActive(), inUse,
                room.getCreatedAt(), room.getUpdatedAt());
    }
}
