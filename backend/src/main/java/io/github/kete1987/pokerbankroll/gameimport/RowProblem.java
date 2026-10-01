package io.github.kete1987.pokerbankroll.gameimport;

/**
 * What can be wrong with a value of a row before it is a game, as the {@code code} of the error.
 * The message is resolved from {@code messages*.properties} with the key {@code import.row.<CODE>}.
 */
enum RowProblem {

    /** The row has more or fewer values than the header has columns. */
    COLUMN_COUNT,
    REQUIRED,
    INVALID_DATE,
    INVALID_TIME,
    INVALID_NUMBER,
    INVALID_INTEGER,
    INVALID_BOOLEAN,
    INVALID_GAME_TYPE,
    INVALID_MODALITY,
    /** The room does not exist and no row gives the currency to create it with. */
    ROOM_NEEDS_CURRENCY,
    /** The currency of the row is not the one of its room. */
    ROOM_CURRENCY_MISMATCH;

    String messageKey() {
        return "import.row." + name();
    }
}
