package io.github.kete1987.pokerbankroll.game;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/games")
@Tag(name = "Games", description = "Recorded results")
class GameController {

    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_NAMES = 20;

    private final GameService service;

    GameController(GameService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List games", description = "All filters are optional and combined with AND.")
    PageResponse<GameResponse> list(
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
            @RequestParam(required = false) @Nullable GameStatus status,
            @Parameter(description = "Currency of the room, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency,
            @Parameter(description = "Text contained in the name or the notes, ignoring case")
            @RequestParam(required = false) @Nullable String q,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @Parameter(description = "`<field>,<asc|desc>` with field one of playedOn, net, buyIn, prize, won, createdAt")
            @RequestParam(defaultValue = GameSort.DEFAULT) String sort) {
        GameFilter filter = new GameFilter(from, to, gameType, modality, roomId, variantId, status, currency, q);
        return service.list(filter, page, size, GameSort.parse(sort));
    }

    // A plain list, not a page: it is bounded by `limit` and nobody asks for the rest.
    @GetMapping("/names")
    @Operation(summary = "Suggest names of recorded games",
            description = "The names containing the text, most used first, then most recent, then by name. "
                    + "Names that differ only in case or surrounding spaces are one, written as in its most "
                    + "recent game (latest date, then latest id), whose type, modality, buy-in and variant "
                    + "come with it. Games in play count too.")
    List<GameNameResponse> names(
            @Parameter(description = "Text contained in the name, ignoring case and searched literally; "
                    + "with less than 2 characters, surrounding spaces apart, nothing is suggested")
            @RequestParam(defaultValue = "") String q,
            @Parameter(description = "Only the games of this type: their names, counts and figures")
            @RequestParam(required = false) @Nullable GameType gameType,
            @RequestParam(defaultValue = "8") @Min(1) @Max(MAX_NAMES) int limit) {
        return service.names(q, gameType, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a game")
    GameResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Record a game",
            description = "With only the required fields the game is recorded as in play; "
                    + "send a result (or `status: FINISHED`) to record it finished.")
    @ApiResponse(responseCode = "201", description = "Created")
    ResponseEntity<GameResponse> create(@Valid @RequestBody GameRequest request) {
        GameResponse game = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(game.id());
        return ResponseEntity.created(location).body(game);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a game", description = "Replaces every field; omitted optional fields take their default.")
    GameResponse update(@PathVariable long id, @Valid @RequestBody GameRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Finish a game in play",
            description = "Sets its result; with no body (or an empty one) nothing was won.")
    GameResponse finish(@PathVariable long id,
            @Valid @RequestBody(required = false) @Nullable FinishGameRequest result) {
        return service.finish(id, result == null ? FinishGameRequest.NOTHING_WON : result);
    }

    @PostMapping("/{id}/re-entries")
    @Operation(summary = "Add a re-entry to a tournament or Sit&Go in play",
            description = "Adds one entry, paid in cash.")
    GameResponse addReEntry(@PathVariable long id) {
        return service.addReEntry(id);
    }

    @PostMapping("/{id}/rebuys")
    @Operation(summary = "Add a rebuy to a cash game in play", description = "Adds the amount to the buy-in.")
    GameResponse addRebuy(@PathVariable long id, @Valid @RequestBody RebuyRequest rebuy) {
        return service.addRebuy(id, rebuy);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a game")
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
