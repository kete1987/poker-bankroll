package io.github.kete1987.pokerbankroll.export;

/**
 * An exported file, ready to be downloaded.
 *
 * @param name        says what it holds and the day it was made, e.g. {@code poker-bankroll-games-2026-10-02.csv}
 * @param contentType media type of the content
 */
record ExportFile(String name, String contentType, byte[] content) {
}
