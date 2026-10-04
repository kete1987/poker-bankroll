package io.github.kete1987.pokerbankroll.backup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** What a backup file holds, what the installation held, and what was done. */
public record BackupRestoreResponse(
        @Schema(description = "The file was only checked: nothing was changed")
        boolean dryRun,
        @Schema(description = "The installation now holds what the file holds: not a dry run, and no errors")
        boolean restored,
        @Schema(description = "Version of the format of the file")
        int formatVersion,
        @Schema(description = "Version of the application that made the file")
        @Nullable String appVersion,
        @Schema(description = "When the file was made")
        @Nullable Instant exportedAt,
        @Schema(description = "What the file holds")
        BackupContents file,
        @Schema(description = "What the installation held before the restore: it is deleted by it "
                + "(in a dry run or with errors, what it holds and would lose)")
        BackupContents current,
        @Schema(description = "Errors found in the content of the file; `errors` lists only the first ones")
        int errorCount,
        List<BackupError> errors) {

    /**
     * What a backup, or an installation, holds.
     *
     * @param variants    variants defined by the user (the built-in ones are always there)
     * @param games       every game, those in play included
     * @param gamesInPlay how many of the games are in play
     * @param templates   templates of games, which do not count to say it is empty (there are none
     *                    without a room)
     * @param empty       nothing recorded: no rooms, user-defined variants, games or movements
     */
    public record BackupContents(
            int rooms,
            int variants,
            int games,
            int gamesInPlay,
            int movements,
            int templates,
            @Schema(description = "Date of the first game")
            @Nullable LocalDate from,
            @Schema(description = "Date of the last game")
            @Nullable LocalDate to,
            boolean empty) {
    }

    /**
     * Something wrong in the content of the file.
     *
     * @param path    where, as the document names it: {@code games[12].buyIn} is the buy-in of the
     *                thirteenth game (positions start at 0)
     * @param code    stable code: a constraint name, an error code of the API or a backup one
     * @param message localized fallback message
     */
    public record BackupError(String path, String code, String message) {
    }
}
