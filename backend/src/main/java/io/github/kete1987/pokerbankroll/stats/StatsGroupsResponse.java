package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import org.jspecify.annotations.Nullable;

/** Amounts in different currencies are never added up: the groups are per currency. */
public record StatsGroupsResponse(GroupBy groupBy, List<CurrencyGroups> currencies) {

    public record CurrencyGroups(
            String currencyCode,
            @Schema(description = "Groups with finished games. Periods from oldest to newest, buy-ins from "
                    + "lowest to highest, anything else from most to fewest games")
            List<Group> groups) {
    }

    public record Group(
            GroupKey key,
            StatsFigures figures,
            @Schema(description = "Net of this period and the earlier ones in the response, so it starts "
                    + "from zero at the beginning of the filtered range; only for groups in time")
            @Nullable BigDecimal cumulativeNet,
            @Schema(description = "The group broken down by game type (those with games, in catalog "
                    + "order); only when asked for with `byGameType=true`")
            @Nullable List<GameTypeSummary> byGameType) {
    }

    /** What the games of a group have in common: only the fields of the requested grouping are set. */
    public record GroupKey(
            @Schema(description = "DAY: 2026-01-19; WEEK: its Monday, 2026-01-19; MONTH: 2026-01; YEAR: 2026")
            @Nullable String period,
            @Schema(description = "GAME_TYPE, and VARIANT (the type the variant belongs to)")
            @Nullable GameType gameType,
            @Schema(description = "VARIANT; null for the games of the type without a variant")
            @Nullable VariantRef variant,
            @Schema(description = "ROOM")
            @Nullable RoomRef room,
            @Schema(description = "MODALITY")
            @Nullable Modality modality,
            @Schema(description = "BUY_IN")
            @Nullable BigDecimal buyIn) {
    }
}
