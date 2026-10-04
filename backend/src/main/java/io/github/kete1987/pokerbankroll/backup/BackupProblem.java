package io.github.kete1987.pokerbankroll.backup;

/**
 * What can be wrong in the content of a backup that no rule of the API covers, as the {@code code}
 * of the error. The message is resolved from {@code messages*.properties} with the key
 * {@code backup.problem.<CODE>}.
 */
enum BackupProblem {

    REQUIRED,
    /** Two rooms, or two variants, with the same id: what names them would be ambiguous. */
    DUPLICATE_ID,
    /** Two exchange rates typed by hand of the same currency and day. */
    DUPLICATE_EXCHANGE_RATE,
    /** A variant with both a code and a name, or with neither. */
    VARIANT_CODE_OR_NAME,
    /** A game of a built-in variant this installation does not have. */
    UNKNOWN_BUILT_IN_VARIANT;

    String messageKey() {
        return "backup.problem." + name();
    }
}
