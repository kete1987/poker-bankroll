package io.github.kete1987.pokerbankroll.game;

/** Whether the result of a game is known yet. Matches the CHECK constraint of {@code game.status}. */
public enum GameStatus {
    /** Registered when it starts; no prize, bounty or ticket yet. Its buy-in already counts in net. */
    IN_PLAY,
    /** The result is known, possibly nothing won. */
    FINISHED
}
