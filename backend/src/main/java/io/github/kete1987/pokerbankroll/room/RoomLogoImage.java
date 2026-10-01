package io.github.kete1987.pokerbankroll.room;

/**
 * The logo of a room as it is served. {@code version} is the {@code logoVersion} of the room;
 * {@code contentHash} identifies the bytes (it is the ETag).
 */
public record RoomLogoImage(byte[] content, String contentType, String version, String contentHash) {
}
