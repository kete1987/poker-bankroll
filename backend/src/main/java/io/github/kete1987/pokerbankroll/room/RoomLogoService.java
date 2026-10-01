package io.github.kete1987.pokerbankroll.room;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomLogoService {

    /** Largest image accepted: 256 kB, also enforced by the database. */
    public static final int MAX_BYTES = 256 * 1024;

    private final RoomLogoRepository logos;
    private final RoomRepository rooms;
    private final RoomService roomService;

    RoomLogoService(RoomLogoRepository logos, RoomRepository rooms, RoomService roomService) {
        this.logos = logos;
        this.rooms = rooms;
        this.roomService = roomService;
    }

    /**
     * Sets the logo of a room, replacing the one it had. The format is the one found in the
     * content, whatever the client declared.
     */
    public RoomResponse replace(long roomId, byte[] content) {
        if (!rooms.existsById(roomId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (content.length == 0) {
            throw new ApiException(ErrorCode.LOGO_EMPTY);
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.LOGO_TOO_LARGE, MAX_BYTES / 1024);
        }
        LogoImageType type = LogoImageType.detect(content)
                .orElseThrow(() -> new ApiException(ErrorCode.LOGO_UNSUPPORTED_TYPE));

        // The precision of the column, so that the version is the same when read back.
        logos.put(roomId, content, type.contentType(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        return roomService.get(roomId);
    }

    @Transactional(readOnly = true)
    public RoomLogoImage get(long roomId) {
        RoomLogo logo = logos.findById(roomId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new RoomLogoImage(logo.getContent(), logo.getContentType(),
                new RoomLogoVersion(roomId, logo.getUpdatedAt()).value(), hash(logo.getContent()));
    }

    public void delete(long roomId) {
        if (logos.deleteByRoomId(roomId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /** First half of the SHA-256 of the content, in hexadecimal. */
    private static String hash(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
