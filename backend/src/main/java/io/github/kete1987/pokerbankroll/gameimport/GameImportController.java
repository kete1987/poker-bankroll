package io.github.kete1987.pokerbankroll.gameimport;

import java.io.IOException;
import java.io.InputStream;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports")
@Tag(name = "Import")
class GameImportController {

    static final String CSV = "text/csv";

    private final GameImportService service;

    GameImportController(GameImportService service) {
        this.service = service;
    }

    @PostMapping(path = "/games", consumes = CSV)
    @Operation(summary = "Import games from a CSV file",
            description = """
                    The body is the file itself, in the format of `docs/import.md`: UTF-8, comma separated, \
                    a header row and one game per row, up to 5 MB and 50,000 rows. Every game is imported as \
                    finished; rooms and variants that do not exist are created.

                    It is all or nothing: with an error in any row nothing is stored, and the response \
                    (still 200) lists the errors with `imported: false`. With `dryRun=true` the file is \
                    checked in the same way and nothing is stored either. Importing the same file twice \
                    records its games twice.

                    A file that cannot be read as a whole fails with `IMPORT_FILE_EMPTY`, \
                    `IMPORT_FILE_NOT_UTF8`, `IMPORT_FILE_MALFORMED`, `IMPORT_UNKNOWN_COLUMN`, \
                    `IMPORT_DUPLICATE_COLUMN`, `IMPORT_MISSING_COLUMN`, `IMPORT_TOO_MANY_ROWS` (400) or \
                    `IMPORT_FILE_TOO_LARGE` (413).""",
            requestBody = @RequestBody(required = true,
                    content = @Content(mediaType = CSV, schema = @Schema(type = "string"))))
    GameImportResponse importGames(
            @Parameter(description = "Only check the file and report what it would import")
            @RequestParam(defaultValue = "false") boolean dryRun,
            @Parameter(hidden = true) InputStream body) throws IOException {
        // Never more than one byte over the limit in memory, however large the body is.
        return service.importGames(body.readNBytes(GameImportService.MAX_BYTES + 1), dryRun);
    }
}
