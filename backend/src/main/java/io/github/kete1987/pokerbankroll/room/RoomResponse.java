package io.github.kete1987.pokerbankroll.room;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

public record RoomResponse(
        long id,
        String name,
        String currencyCode,
        boolean active,
        @Schema(description = "The room has games or bankroll movements: it cannot be deleted and its currency cannot change")
        boolean inUse,
        @Schema(description = "Null when the room has no logo. Otherwise an opaque value that changes every time "
                + "the logo is uploaded: get the image from `/rooms/{id}/logo?v=<logoVersion>`")
        @Nullable String logoVersion,
        Instant createdAt,
        Instant updatedAt) {

    static RoomResponse of(Room room, boolean inUse, @Nullable String logoVersion) {
        return new RoomResponse(room.getId(), room.getName(), room.getCurrencyCode(), room.isActive(), inUse,
                logoVersion, room.getCreatedAt(), room.getUpdatedAt());
    }
}
