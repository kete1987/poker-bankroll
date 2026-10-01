package io.github.kete1987.pokerbankroll.catalog;

/**
 * Format of a game. Mirrors the {@code game_type} table (same codes, same order): the list is
 * fixed because the application behaves differently per type.
 */
public enum GameType {
    TOURNAMENT,
    /** Sit &amp; Go, including spins / lottery Sit&amp;Go such as Expresso. */
    SIT_AND_GO,
    CASH
}
