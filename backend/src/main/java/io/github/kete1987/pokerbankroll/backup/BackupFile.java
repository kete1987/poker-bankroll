package io.github.kete1987.pokerbankroll.backup;

/**
 * A backup, ready to be downloaded.
 *
 * @param name    says what it is and the day it was made, e.g. {@code poker-bankroll-backup-2026-10-02.json}
 * @param content the JSON document, in UTF-8
 */
record BackupFile(String name, byte[] content) {
}
