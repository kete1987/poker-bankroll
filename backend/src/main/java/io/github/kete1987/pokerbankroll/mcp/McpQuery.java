package io.github.kete1987.pokerbankroll.mcp;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import io.github.kete1987.pokerbankroll.game.GameSort;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.stats.GroupBy;
import io.github.kete1987.pokerbankroll.mcp.McpOperationService.ResourceType;
import org.jspecify.annotations.Nullable;

/** The published schema limits each tool to its own subset of these typed parameters. */
record McpQuery(
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Size(max = 3) @Nullable List<@NotNull GameType> gameTypes,
        @Nullable Modality modality,
        @Size(max = 100) @Nullable List<@NotNull @Positive Long> roomIds,
        @Size(max = 100) @Nullable List<@NotNull @Positive Long> variantIds,
        @Size(max = 100) @Nullable List<@NotNull @Positive Long> tagIds,
        @Nullable GameStatus status,
        @Pattern(regexp = "[A-Z]{3}") @Nullable String currencyCode,
        @Size(max = 500) @Nullable String text,
        @Min(0) @Max(1000000) @Nullable Integer page,
        @Min(1) @Max(200) @Nullable Integer size,
        @Size(max = 40) @Nullable String sort,
        @Nullable GroupBy groupBy,
        @Nullable Boolean byGameType,
        @Nullable Boolean active,
        @Nullable GameType gameType,
        @Nullable ResourceType resourceType,
        @Positive @Nullable Long resourceId) {

    @AssertTrue(message = "{McpDateRange}")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }

    GameFilter filter() {
        return new GameFilter(from, to, gameTypes, modality, roomIds, variantIds, tagIds, status, currencyCode, text);
    }

    int pageNumber() { return page == null ? 0 : page; }
    int pageSize() { return size == null ? 50 : size; }
    String gameSort() { return sort == null ? GameSort.DEFAULT : sort; }
}
