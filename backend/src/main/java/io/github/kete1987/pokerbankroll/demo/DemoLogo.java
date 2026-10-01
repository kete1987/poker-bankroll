package io.github.kete1987.pokerbankroll.demo;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Invented logos for the demo rooms: a shape on a plain background. The real logos of the poker
 * rooms are trademarks and are not shipped. The PNG is written by hand because the runtime of the
 * API image has no {@code java.desktop} (no {@code ImageIO}).
 */
final class DemoLogo {

    enum Shape { CIRCLE, DIAMOND, RING }

    private static final int SIZE = 64;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private DemoLogo() {
    }

    /** A 64×64 PNG with the shape centred; colours are {@code 0xRRGGBB}. */
    static byte[] png(Shape shape, int background, int foreground) {
        // Each row is a filter byte (0: none) followed by its RGB pixels.
        ByteBuffer pixels = ByteBuffer.allocate(SIZE * (1 + 3 * SIZE));
        for (int y = 0; y < SIZE; y++) {
            pixels.put((byte) 0);
            for (int x = 0; x < SIZE; x++) {
                int colour = inside(shape, x, y) ? foreground : background;
                pixels.put((byte) (colour >> 16)).put((byte) (colour >> 8)).put((byte) colour);
            }
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(PNG_SIGNATURE);
        // Width, height, 8 bits per channel, colour type 2 (RGB), default compression, filter and interlace.
        writeChunk(png, "IHDR", ByteBuffer.allocate(13).putInt(SIZE).putInt(SIZE)
                .put(new byte[] {8, 2, 0, 0, 0}).array());
        writeChunk(png, "IDAT", deflate(pixels.array()));
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static boolean inside(Shape shape, int x, int y) {
        // Distances from the centre of the image to the centre of the pixel.
        double dx = x + 0.5 - SIZE / 2.0;
        double dy = y + 0.5 - SIZE / 2.0;
        double distance = Math.hypot(dx, dy);
        return switch (shape) {
            case CIRCLE -> distance <= 18;
            case DIAMOND -> Math.abs(dx) + Math.abs(dy) <= 22;
            case RING -> distance <= 21 && distance >= 12;
        };
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** A PNG chunk: length of the data, type, data and the CRC of type and data. */
    private static void writeChunk(ByteArrayOutputStream png, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        png.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        png.writeBytes(typeBytes);
        png.writeBytes(data);
        png.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
