package io.github.kete1987.pokerbankroll.room;

import java.util.Arrays;
import java.util.Optional;

/**
 * Image formats accepted as the logo of a room, recognised by the first bytes of the file. Only
 * raster formats: SVG is left out because it can carry scripts.
 */
enum LogoImageType {

    PNG("image/png") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        }
    },
    JPEG("image/jpeg") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0xFF, 0xD8, 0xFF);
        }
    },
    WEBP("image/webp") {
        @Override
        boolean matches(byte[] content) {
            // A RIFF container ("RIFF", four bytes of length, then the kind of content).
            return startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P');
        }
    };

    private final String contentType;

    LogoImageType(String contentType) {
        this.contentType = contentType;
    }

    String contentType() {
        return contentType;
    }

    abstract boolean matches(byte[] content);

    static Optional<LogoImageType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
