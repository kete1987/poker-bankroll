package io.github.kete1987.pokerbankroll.stats;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@Tag(name = "Statistics", description = "Aggregated results")
class StatsController {

    private final StatsService service;

    StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Results overall and per game type, for each currency",
            description = "Takes the filters of the games list, all optional and combined with AND. "
                    + "Only finished games count in the figures; games in play are reported apart.")
    StatsSummaryResponse summary(
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
            @Parameter(description = "Currency of the room, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency,
            @Parameter(description = "Text contained in the name or the notes, ignoring case")
            @RequestParam(required = false) @Nullable String q) {
        return service.summary(new GameFilter(from, to, gameType, modality, roomId, variantId, null, currency, q));
    }

    @GetMapping("/groups")
    @Operation(summary = "Results of the finished games per group, for each currency",
            description = "Groups by period (day, week, month, year), game type, variant, room, modality or "
                    + "buy-in, with the same figures as the summary. Takes the filters of the games list, so "
                    + "e.g. `groupBy=MONTH&gameType=TOURNAMENT` gives the tournaments per month. "
                    + "Periods without games are not returned.")
    StatsGroupsResponse groups(
            @RequestParam GroupBy groupBy,
            @Parameter(description = "Also break each group down by game type")
            @RequestParam(defaultValue = "false") boolean byGameType,
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
            @Parameter(description = "Currency of the room, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency,
            @Parameter(description = "Text contained in the name or the notes, ignoring case")
            @RequestParam(required = false) @Nullable String q) {
        GameFilter filter = new GameFilter(from, to, gameType, modality, roomId, variantId, null, currency, q);
        return service.groups(filter, groupBy, byGameType);
    }
}
