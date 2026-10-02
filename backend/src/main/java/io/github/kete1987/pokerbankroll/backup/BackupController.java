package io.github.kete1987.pokerbankroll.backup;

import java.io.IOException;
import java.io.InputStream;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backup")
@Tag(name = "Backup", description = "Everything the installation holds as one file, and restoring it")
// Method names are the operation ids of the contract: unique ones keep the ids of other endpoints.
class BackupController {

    private final BackupService service;

    BackupController(BackupService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Download a backup of everything",
            description = """
                    One JSON document with everything the user created: rooms (with their logos), \
                    user-defined variants and whether each built-in one is active, every game (those in \
                    play too) and every bankroll movement. It says the version of its format \
                    (`formatVersion`) and the version of the application that made it.

                    The file comes as an attachment named after the day, e.g. \
                    `poker-bankroll-backup-2026-10-02.json`. Restore it with `POST /backup/restore`.""")
    @ApiResponse(responseCode = "200", description = "OK", content =
        @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "string", format = "binary")))
    ResponseEntity<byte[]> downloadBackup() {
        BackupFile file = service.backup();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.name()).build().toString())
                // A backup is what there is now: never a copy kept by the browser.
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @PostMapping(path = "/restore", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Restore a backup, replacing everything",
            description = """
                    The body is the file of `GET /backup` itself, up to 32 MB, in any version of the \
                    format up to the current one. Restoring **deletes everything the installation holds** \
                    (games, bankroll movements, rooms and their logos, user-defined variants) and writes \
                    what the file holds; built-in variants are active or not as the file says.

                    It is all or nothing: with an error in the content of the file nothing changes, and \
                    the response (still 200) lists the errors with `restored: false`, each with its place \
                    in the document. With `dryRun=true` the file is checked in the same way and nothing \
                    changes either: the response says what the file holds and what the installation holds \
                    and would lose.

                    An installation that has data is only replaced with `replace=true`; without it the \
                    restore fails with `BACKUP_REPLACE_NOT_CONFIRMED` (409). A file that cannot be read \
                    as a whole fails with `BACKUP_FILE_MALFORMED` or `BACKUP_FORMAT_TOO_NEW` (made by a \
                    newer version of the application) (400), or `BACKUP_FILE_TOO_LARGE` (413).""",
            requestBody = @RequestBody(required = true, content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "string", format = "binary"))))
    BackupRestoreResponse restoreBackup(
            @Parameter(description = "Only check the file and report what it holds and what would be deleted")
            @RequestParam(defaultValue = "false") boolean dryRun,
            @Parameter(description = "Delete what the installation holds; needed unless it is empty")
            @RequestParam(defaultValue = "false") boolean replace,
            @Parameter(hidden = true) InputStream body) throws IOException {
        // Never more than one byte over the limit in memory, however large the body is.
        return service.restore(body.readNBytes(BackupService.MAX_BYTES + 1), dryRun, replace);
    }
}
