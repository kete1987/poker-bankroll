package io.github.kete1987.pokerbankroll.room;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Image of a room, in its own table so that reading rooms never loads images. Only read through
 * JPA: it is written with {@link RoomLogoRepository#put}.
 */
@Entity
@Table(name = "room_logo")
public class RoomLogo {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomLogo() {
    }

    public Long getRoomId() {
        return roomId;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
