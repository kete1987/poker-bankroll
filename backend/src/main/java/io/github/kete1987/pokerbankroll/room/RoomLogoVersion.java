package io.github.kete1987.pokerbankroll.room;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** When the logo of a room was last uploaded, read without its image. */
record RoomLogoVersion(long roomId, Instant updatedAt) {

    /** Short opaque text that changes with every upload: the instant in microseconds, in base 36. */
    String value() {
        return Long.toString(ChronoUnit.MICROS.between(Instant.EPOCH, updatedAt), Character.MAX_RADIX);
    }
}
