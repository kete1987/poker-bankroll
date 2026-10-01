package io.github.kete1987.pokerbankroll.gameimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.jspecify.annotations.Nullable;

/**
 * What a file of games contains and what was done with it. The figures describe the rows without
 * errors: the games that were imported or, in a dry run or when there are errors, that would be.
 */
public record GameImportResponse(
        @Schema(description = "The file was only checked: nothing was stored")
        boolean dryRun,
        @Schema(description = "The games were stored: not a dry run, and no row had errors")
        boolean imported,
        @Schema(description = "Rows of the file with something in them, the header apart")
        int rows,
        @Schema(description = "Rows without errors")
        int games,
        List<ImportGameTypeCount> gamesByType,
        @Schema(description = "Date of the first game")
        @Nullable LocalDate from,
        @Schema(description = "Date of the last game")
        @Nullable LocalDate to,
        @Schema(description = "Games and their net per currency, to compare with the source of the file")
        List<ImportCurrencyTotal> totals,
        @Schema(description = "Rooms that did not exist and are created by the import")
        List<ImportedRoom> newRooms,
        @Schema(description = "Variants that did not exist and are created by the import")
        List<ImportedVariant> newVariants,
        @Schema(description = "Errors found in the rows; `errors` lists only the first ones")
        int errorCount,
        List<ImportRowError> errors) {

    public record ImportGameTypeCount(GameType gameType, int games) {
    }

    public record ImportCurrencyTotal(String currencyCode, int games, BigDecimal net) {
    }

    public record ImportedRoom(String name, String currencyCode) {
    }

    public record ImportedVariant(GameType gameType, String name) {
    }

    /**
     * Something wrong in a row.
     *
     * @param row     row of the file as a spreadsheet numbers it: the header is row 1
     * @param field   column of the value at fault; {@code null} when it is the row as a whole
     * @param code    stable code: a constraint name, an error code of the API or an import one
     * @param message localized fallback message
     */
    public record ImportRowError(int row, @Nullable String field, String code, String message) {
    }
}
