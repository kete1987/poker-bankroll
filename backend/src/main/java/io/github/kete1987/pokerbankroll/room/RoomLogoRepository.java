package io.github.kete1987.pokerbankroll.room;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RoomLogoRepository extends JpaRepository<RoomLogo, Long> {

    /** The rooms that have a logo, without loading the images. */
    @Query("select new io.github.kete1987.pokerbankroll.room.RoomLogoVersion(l.roomId, l.updatedAt) from RoomLogo l")
    List<RoomLogoVersion> findVersions();

    @Query("""
            select new io.github.kete1987.pokerbankroll.room.RoomLogoVersion(l.roomId, l.updatedAt)
            from RoomLogo l where l.roomId = :roomId
            """)
    Optional<RoomLogoVersion> findVersionByRoomId(long roomId);

    /**
     * Sets the logo of a room, replacing the one it had. A single statement, so two simultaneous
     * uploads to a room without logo end with the last one instead of failing on the primary key.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into room_logo (room_id, content, content_type, size_bytes, updated_at)
            values (:roomId, :content, :contentType, octet_length(:content), :updatedAt)
            on conflict (room_id) do update
                set content = excluded.content,
                    content_type = excluded.content_type,
                    size_bytes = excluded.size_bytes,
                    updated_at = excluded.updated_at
            """, nativeQuery = true)
    void put(long roomId, byte[] content, String contentType, Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from room_logo where room_id = :roomId", nativeQuery = true)
    int deleteByRoomId(long roomId);
}
