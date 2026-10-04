package io.github.kete1987.pokerbankroll.export;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.github.kete1987.pokerbankroll.bankroll.MovementFilter;
import io.github.kete1987.pokerbankroll.bankroll.MovementType;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exports")
@Tag(name = "Export", description = "Games and bankroll movements as files")
// Method names are the operation ids of the contract: unique ones keep the ids of other endpoints.
class ExportController {

    private final ExportService service;

    ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping("/games")
    @Operation(summary = "Export the finished games as a CSV or Excel file",
            description = """
                    Every **finished** game the filters select (those of the games list, combined with \
                    AND), not a page of them, oldest first. Games in play are never exported.

                    `CSV` is the format of the import (`docs/import.md`): importing the file into an empty \
                    database records the same games. `XLSX` is made to be read: dates and amounts are typed \
                    cells, it adds what was invested and the net of each game, and its headers and values \
                    are in the language of `Accept-Language`.

                    The file comes as an attachment named after its content and the day, e.g. \
                    `poker-bankroll-games-2026-10-02.csv`.""")
    @ApiResponse(responseCode = "200", description = "OK", content = {
        @Content(mediaType = ExportFormat.CSV_TYPE, schema = @Schema(type = "string")),
        @Content(mediaType = ExportFormat.XLSX_TYPE, schema = @Schema(type = "string", format = "binary"))})
    ResponseEntity<byte[]> exportGames(
            @RequestParam ExportFormat format,
            @Parameter(description = "Played on or after this date")
            @RequestParam(required = false) @Nullable LocalDate from,
            @Parameter(description = "Played on or before this date")
            @RequestParam(required = false) @Nullable LocalDate to,
            @Parameter(description = "One or more game types: games of any of them")
            @RequestParam(required = false) @Nullable List<GameType> gameType,
            @RequestParam(required = false) @Nullable Modality modality,
            @Parameter(description = "One or more rooms: games in any of them")
            @RequestParam(required = false) @Nullable List<Long> roomId,
            @Parameter(description = "One or more variants: games of any of them")
            @RequestParam(required = false) @Nullable List<Long> variantId,
            @Parameter(description = "One or more tags: games that have any of them")
            @RequestParam(required = false) @Nullable List<Long> tagId,
            @Parameter(description = "Currency of the room, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency,
            @Parameter(description = "Text contained in the name or the notes, ignoring case")
            @RequestParam(required = false) @Nullable String q) {
        // The status is not a filter here: the service only exports finished games.
        GameFilter filter = new GameFilter(from, to, gameType, modality, roomId, variantId, tagId, null, currency, q);
        return download(service.games(format, filter));
    }

    @GetMapping("/movements")
    @Operation(summary = "Export the bankroll movements as a CSV or Excel file",
            description = """
                    Every movement the filters select (those of the list of movements, combined with AND), \
                    not a page of them, oldest first: date, type, room (empty when it belongs to no room), \
                    currency, amount and notes. The amount is signed (a withdrawal is negative), so the \
                    amounts of a currency add up to what they did to the bankroll.

                    `CSV` carries codes; `XLSX` is made to be read: dates and amounts are typed cells and \
                    its headers and types are in the language of `Accept-Language`.

                    The file comes as an attachment named after its content and the day, e.g. \
                    `poker-bankroll-movements-2026-10-02.xlsx`.""")
    @ApiResponse(responseCode = "200", description = "OK", content = {
        @Content(mediaType = ExportFormat.CSV_TYPE, schema = @Schema(type = "string")),
        @Content(mediaType = ExportFormat.XLSX_TYPE, schema = @Schema(type = "string", format = "binary"))})
    ResponseEntity<byte[]> exportMovements(
            @RequestParam ExportFormat format,
            @Parameter(description = "On or after this date")
            @RequestParam(required = false) @Nullable LocalDate from,
            @Parameter(description = "On or before this date")
            @RequestParam(required = false) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable MovementType type,
            @Parameter(description = "One or more rooms: movements of any of them")
            @RequestParam(required = false) @Nullable List<Long> roomId,
            @Parameter(description = "true: only movements that belong to no room; false: only those of a room")
            @RequestParam(required = false) @Nullable Boolean withoutRoom,
            @Parameter(description = "Currency of the amount, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency) {
        return download(service.movements(format, new MovementFilter(from, to, type, roomId, withoutRoom, currency)));
    }

    private static ResponseEntity<byte[]> download(ExportFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                // What is exported is what there is now: never a copy kept by the browser.
                .cacheControl(CacheControl.noStore())
                // The content type is the one of the file: browsers must not guess another.
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
