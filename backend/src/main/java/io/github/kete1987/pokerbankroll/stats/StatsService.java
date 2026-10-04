package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.Game;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.BuyInRange;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.CurrencyGroups;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.Group;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.GroupKey;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.CurrencySummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.InPlay;
import io.github.kete1987.pokerbankroll.variant.Variant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatsService {

    /** Columns of every row of sums: currency, game type, then the keys of the query, then the sums. */
    private static final int CURRENCY = 0;
    private static final int GAME_TYPE = 1;
    private static final int FIRST_KEY = 2;

    private final EntityManager entityManager;

    StatsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Results of the games selected by the filter, per currency: finished games in the figures
     * (overall and per game type), games in play apart.
     */
    public StatsSummaryResponse summary(GameFilter filter) {
        Map<String, CurrencyTotals> currencies = new TreeMap<>();
        for (Tuple row : sums(filter, false, (game, cb) -> List.of(game.<GameStatus>get("status")))) {
            CurrencyTotals totals = currencies.computeIfAbsent(row.get(CURRENCY, String.class), code -> new CurrencyTotals());
            GameType gameType = row.get(GAME_TYPE, GameType.class);
            if (row.get(FIRST_KEY, GameStatus.class) == GameStatus.FINISHED) {
                add(row, 1, totals.finished);
                add(row, 1, totals.byGameType.computeIfAbsent(gameType, t -> new GameTotals()));
            } else {
                add(row, 1, totals.inPlay);
            }
        }
        List<CurrencySummary> summaries = new ArrayList<>();
        currencies.forEach((currencyCode, totals) -> summaries.add(new CurrencySummary(
                currencyCode,
                totals.finished.toFigures(),
                totals.byGameType.entrySet().stream()
                        .map(entry -> new GameTypeSummary(entry.getKey(), entry.getValue().toFigures()))
                        .toList(),
                new InPlay(totals.inPlay.games(), totals.inPlay.invested()))));
        return new StatsSummaryResponse(summaries);
    }

    /** Results of the finished games selected by the filter, per currency and group. */
    public StatsGroupsResponse groups(GameFilter filter, GroupBy groupBy, boolean byGameType) {
        Grouping grouping = Grouping.of(groupBy);
        Map<String, Map<GroupKey, GameTotals>> currencies = new TreeMap<>();
        // The same sums again, kept apart per game type within each group.
        Map<String, Map<GroupKey, Map<GameType, GameTotals>>> perGameType = new HashMap<>();
        // Names are grouped ignoring case: how each is written in a currency, with the games written
        // that way.
        Map<String, Map<String, Map<String, Long>>> spellings = new HashMap<>();
        for (Tuple row : sums(filter, true, grouping.keys)) {
            if ((groupBy == GroupBy.BUY_IN || groupBy == GroupBy.BUY_IN_RANGE)
                    && row.get(GAME_TYPE, GameType.class) == GameType.CASH) {
                continue;
            }
            String currency = row.get(CURRENCY, String.class);
            GroupKey groupKey = grouping.toKey.apply(row);
            if (groupBy == GroupBy.NAME && groupKey.name() != null) {
                spellings.computeIfAbsent(currency, code -> new HashMap<>())
                        .computeIfAbsent(groupKey.name(), key -> new HashMap<>())
                        .merge(row.get(FIRST_KEY, String.class).strip(), count(row, FIRST_KEY + 1), Long::sum);
            }
            add(row, grouping.keyCount, currencies
                    .computeIfAbsent(currency, code -> new HashMap<>())
                    .computeIfAbsent(groupKey, key -> new GameTotals()));
            if (byGameType) {
                add(row, grouping.keyCount, perGameType
                        .computeIfAbsent(currency, code -> new HashMap<>())
                        .computeIfAbsent(groupKey, key -> new EnumMap<>(GameType.class))
                        .computeIfAbsent(row.get(GAME_TYPE, GameType.class), type -> new GameTotals()));
            }
        }
        List<CurrencyGroups> result = new ArrayList<>();
        currencies.forEach((currencyCode, totalsByKey) -> {
            List<Group> groups = totalsByKey.entrySet().stream()
                    .map(entry -> new Group(written(entry.getKey(), spellings.get(currencyCode)), entry.getValue().toFigures(), null,
                            byGameType ? gameTypesOf(perGameType.get(currencyCode).get(entry.getKey())) : null))
                    .sorted(order(groupBy))
                    .toList();
            result.add(new CurrencyGroups(currencyCode, groupBy.isPeriod() ? withCumulativeNet(groups) : groups));
        });
        return new StatsGroupsResponse(groupBy, result);
    }

    /** A name as most of its games write it. */
    private static GroupKey written(GroupKey key, @Nullable Map<String, Map<String, Long>> spellings) {
        if (key.name() == null || spellings == null) {
            return key;
        }
        String name = spellings.get(key.name()).entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow().getKey();
        return GroupKey.ofName(name);
    }

    private static List<GameTypeSummary> gameTypesOf(Map<GameType, GameTotals> totals) {
        return totals.entrySet().stream()
                .map(entry -> new GameTypeSummary(entry.getKey(), entry.getValue().toFigures()))
                .toList();
    }

    private static Comparator<Group> order(GroupBy groupBy) {
        if (groupBy.isPeriod()) {
            // ISO dates, months and years sort as text.
            return Comparator.comparing(group -> group.key().period());
        }
        if (groupBy == GroupBy.BUY_IN) {
            return Comparator.comparing(group -> group.key().buyIn());
        }
        if (groupBy == GroupBy.BUY_IN_RANGE) {
            // Free games (from 0 to 0) before the range that starts above 0.
            return Comparator.<Group, BigDecimal>comparing(group -> group.key().buyInRange().from())
                    .thenComparing(group -> group.key().buyInRange().to(),
                            Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (groupBy == GroupBy.WEEKDAY) {
            return Comparator.comparing(group -> group.key().weekday());
        }
        // Most played first; the rest only makes the order stable. Games without a name go last.
        return Comparator.<Group, Boolean>comparing(group -> groupBy == GroupBy.NAME && group.key().name() == null)
                .thenComparing(Comparator.<Group>comparingLong(group -> group.figures().games()).reversed())
                .thenComparing(group -> group.figures().net(), Comparator.reverseOrder())
                .thenComparing(group -> group.key().toString());
    }

    private static List<Group> withCumulativeNet(List<Group> groups) {
        List<Group> result = new ArrayList<>(groups.size());
        BigDecimal cumulativeNet = BigDecimal.ZERO;
        for (Group group : groups) {
            cumulativeNet = cumulativeNet.add(group.figures().net());
            result.add(new Group(group.key(), group.figures(), cumulativeNet, group.byGameType()));
        }
        return result;
    }

    /**
     * One row of sums per currency, game type (some figures leave cash games apart) and the given
     * keys, for the games selected by the filter.
     */
    private List<Tuple> sums(GameFilter filter, boolean finishedOnly, KeyColumns keys) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Game> game = query.from(Game.class);

        Path<BigDecimal> buyIn = game.get("buyIn");
        Path<Integer> entries = game.get("entries");
        Path<BigDecimal> prize = game.get("prize");
        Path<BigDecimal> bounty = game.get("bounty");
        Path<BigDecimal> ticketPrizeValue = game.get("ticketPrizeValue");
        Path<BigDecimal> net = game.get("net");

        Predicate inTheMoney = cb.or(cb.gt(prize, 0), cb.gt(ticketPrizeValue, 0));
        Predicate withPrize = cb.or(inTheMoney, cb.gt(bounty, 0));
        Expression<Integer> entriesPaidInCash = cb.diff(entries,
                cb.<Integer>selectCase().when(cb.isTrue(game.get("paidWithTicket")), 1).otherwise(0));

        List<Expression<?>> groupedBy = new ArrayList<>();
        groupedBy.add(game.get("room").get("currencyCode"));
        groupedBy.add(game.get("gameType"));
        groupedBy.addAll(keys.of(game, cb));

        List<Selection<?>> columns = new ArrayList<>(groupedBy);
        columns.addAll(List.of(
                cb.count(game),
                cb.sum(entries),
                countIf(cb, withPrize),
                countIf(cb, inTheMoney),
                countIf(cb, cb.gt(net, 0)),
                cb.sum(buyIn),
                cb.sum(cb.prod(buyIn, entriesPaidInCash)),
                cb.sum(prize),
                cb.sum(bounty),
                cb.sum(ticketPrizeValue),
                cb.sum(net)));
        query.multiselect(columns);

        List<Predicate> selected = new ArrayList<>();
        Predicate filtered = filter.toSpecification().toPredicate(game, query, cb);
        if (filtered != null) {
            selected.add(filtered);
        }
        if (finishedOnly) {
            selected.add(cb.equal(game.get("status"), GameStatus.FINISHED));
        }
        query.where(selected.toArray(Predicate[]::new));
        query.groupBy(groupedBy);
        return entityManager.createQuery(query).getResultList();
    }

    private static Expression<Integer> countIf(CriteriaBuilder cb, Predicate condition) {
        return cb.sum(cb.<Integer>selectCase().when(condition, 1).otherwise(0));
    }

    /** Adds the sums of a row, which come after its {@code keyCount} keys. */
    private static void add(Tuple row, int keyCount, GameTotals totals) {
        int i = FIRST_KEY + keyCount;
        totals.add(row.get(GAME_TYPE, GameType.class), count(row, i), count(row, i + 1), count(row, i + 2),
                count(row, i + 3), count(row, i + 4), amount(row, i + 5), amount(row, i + 6), amount(row, i + 7),
                amount(row, i + 8), amount(row, i + 9), amount(row, i + 10));
    }

    private static long count(Tuple row, int index) {
        return ((Number) row.get(index)).longValue();
    }

    private static BigDecimal amount(Tuple row, int index) {
        Object value = row.get(index);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static final class CurrencyTotals {
        final GameTotals finished = new GameTotals();
        final Map<GameType, GameTotals> byGameType = new EnumMap<>(GameType.class);
        final GameTotals inPlay = new GameTotals();
    }

    @FunctionalInterface
    private interface KeyColumns {
        List<Expression<?>> of(Root<Game> game, CriteriaBuilder cb);
    }

    /** The columns a grouping needs from the database and how they become the key of a group. */
    private record Grouping(int keyCount, KeyColumns keys, Function<Tuple, GroupKey> toKey) {

        static Grouping of(GroupBy groupBy) {
            return switch (groupBy) {
                // Periods are read per day and added up here: no date arithmetic in the database.
                case DAY -> period(TimePeriod.DAY);
                case WEEK -> period(TimePeriod.WEEK);
                case MONTH -> period(TimePeriod.MONTH);
                case YEAR -> period(TimePeriod.YEAR);
                case GAME_TYPE -> new Grouping(0, (game, cb) -> List.of(),
                        row -> GroupKey.ofGameType(row.get(GAME_TYPE, GameType.class)));
                case VARIANT -> new Grouping(3, (game, cb) -> {
                    Join<Game, Variant> variant = game.join("variant", JoinType.LEFT);
                    return List.of(variant.get("id"), variant.get("code"), variant.get("name"));
                }, row -> {
                    Long id = row.get(FIRST_KEY, Long.class);
                    VariantRef variant = id == null ? null : new VariantRef(
                            id, row.get(FIRST_KEY + 1, String.class), row.get(FIRST_KEY + 2, String.class));
                    return GroupKey.ofVariant(row.get(GAME_TYPE, GameType.class), variant);
                });
                case ROOM -> new Grouping(2,
                        (game, cb) -> List.of(game.get("room").get("id"), game.get("room").get("name")),
                        row -> GroupKey.ofRoom(
                                new RoomRef(row.get(FIRST_KEY, Long.class), row.get(FIRST_KEY + 1, String.class))));
                case MODALITY -> new Grouping(1, (game, cb) -> List.of(game.get("modality")),
                        row -> GroupKey.ofModality(row.get(FIRST_KEY, Modality.class)));
                case BUY_IN -> new Grouping(1, (game, cb) -> List.of(game.get("buyIn")),
                        row -> GroupKey.ofBuyIn(row.get(FIRST_KEY, BigDecimal.class)));
                // Read per buy-in and put in its range here.
                case BUY_IN_RANGE -> new Grouping(1, (game, cb) -> List.of(game.get("buyIn")),
                        row -> GroupKey.ofBuyInRange(rangeOf(row.get(FIRST_KEY, BigDecimal.class))));
                // Read as written and put together here, whatever the capitals.
                case NAME -> new Grouping(1, (game, cb) -> List.of(game.get("name")),
                        row -> GroupKey.ofName(nameKey(row.get(FIRST_KEY, String.class))));
                case WEEKDAY -> new Grouping(1, (game, cb) -> List.of(game.get("playedOn")),
                        row -> GroupKey.ofWeekday(row.get(FIRST_KEY, LocalDate.class).getDayOfWeek()));
            };
        }

        /** Where each range of buy-ins ends and the next one starts; the last one has no end. */
        private static final List<BigDecimal> RANGE_ENDS = Stream.of("1", "2", "5", "10", "20", "50")
                .map(BigDecimal::new).toList();

        private static BuyInRange rangeOf(BigDecimal buyIn) {
            if (buyIn.signum() == 0) {
                return new BuyInRange(BigDecimal.ZERO, BigDecimal.ZERO);
            }
            BigDecimal from = BigDecimal.ZERO;
            for (BigDecimal end : RANGE_ENDS) {
                if (buyIn.compareTo(end) < 0) {
                    return new BuyInRange(from, end);
                }
                from = end;
            }
            return new BuyInRange(from, null);
        }

        private static @Nullable String nameKey(@Nullable String name) {
            return name == null || name.isBlank() ? null : name.strip().toLowerCase(Locale.ROOT);
        }

        private static Grouping period(TimePeriod period) {
            return new Grouping(1, (game, cb) -> List.of(game.get("playedOn")),
                    row -> GroupKey.ofPeriod(period.keyOf(row.get(FIRST_KEY, LocalDate.class))));
        }
    }
}
