package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import io.github.kete1987.pokerbankroll.tag.TagRef;
import org.jspecify.annotations.Nullable;

/** Amounts in different currencies are never added up: the groups are per currency. */
public record StatsGroupsResponse(GroupBy groupBy, List<CurrencyGroups> currencies) {

    public record CurrencyGroups(
            String currencyCode,
            @Schema(description = "Groups with finished games. Periods from oldest to newest, buy-ins and "
                    + "their ranges from lowest to highest, days of the week from Monday, anything else from "
                    + "most to fewest games (games without a name or without tags last)")
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
            @Nullable BigDecimal buyIn,
            @Schema(description = "BUY_IN_RANGE")
            @Nullable BuyInRange buyInRange,
            @Schema(description = "NAME, as written in most of its games; null for the games without a name")
            @Nullable String name,
            @Schema(description = "WEEKDAY: 1 (Monday) to 7 (Sunday)")
            @Nullable Integer weekday,
            @Schema(description = "TAG; null for the games without tags")
            @Nullable TagRef tag) {

        static GroupKey ofPeriod(String period) {
            return new GroupKey(period, null, null, null, null, null, null, null, null, null);
        }

        static GroupKey ofGameType(GameType gameType) {
            return ofVariant(gameType, null);
        }

        static GroupKey ofVariant(GameType gameType, @Nullable VariantRef variant) {
            return new GroupKey(null, gameType, variant, null, null, null, null, null, null, null);
        }

        static GroupKey ofRoom(RoomRef room) {
            return new GroupKey(null, null, null, room, null, null, null, null, null, null);
        }

        static GroupKey ofModality(Modality modality) {
            return new GroupKey(null, null, null, null, modality, null, null, null, null, null);
        }

        static GroupKey ofBuyIn(BigDecimal buyIn) {
            return new GroupKey(null, null, null, null, null, buyIn, null, null, null, null);
        }

        static GroupKey ofBuyInRange(BuyInRange range) {
            return new GroupKey(null, null, null, null, null, null, range, null, null, null);
        }

        static GroupKey ofName(@Nullable String name) {
            return new GroupKey(null, null, null, null, null, null, null, name, null, null);
        }

        static GroupKey ofTag(@Nullable TagRef tag) {
            return new GroupKey(null, null, null, null, null, null, null, null, null, tag);
        }

        static GroupKey ofWeekday(DayOfWeek day) {
            return new GroupKey(null, null, null, null, null, null, null, null, day.getValue(), null);
        }
    }

    /**
     * Buy-ins from {@code from} up to, but not including, {@code to}. Free games are the range from
     * 0 to 0, and the first range with a price starts above 0.
     */
    public record BuyInRange(
            BigDecimal from,
            @Schema(description = "Null for the last range, which has no upper end")
            @Nullable BigDecimal to) {
    }
}
