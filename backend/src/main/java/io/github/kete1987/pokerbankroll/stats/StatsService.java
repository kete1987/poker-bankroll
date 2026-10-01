package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

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
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.CurrencyGroups;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.Group;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.GroupKey;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.CurrencySummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.InPlay;
import io.github.kete1987.pokerbankroll.variant.Variant;
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
    public StatsGroupsResponse groups(GameFilter filter, GroupBy groupBy) {
        Grouping grouping = Grouping.of(groupBy);
        Map<String, Map<GroupKey, GameTotals>> currencies = new TreeMap<>();
        for (Tuple row : sums(filter, true, grouping.keys)) {
            if (groupBy == GroupBy.BUY_IN && row.get(GAME_TYPE, GameType.class) == GameType.CASH) {
                continue;
            }
            add(row, grouping.keyCount, currencies
                    .computeIfAbsent(row.get(CURRENCY, String.class), code -> new HashMap<>())
                    .computeIfAbsent(grouping.toKey.apply(row), key -> new GameTotals()));
        }
        List<CurrencyGroups> result = new ArrayList<>();
        currencies.forEach((currencyCode, totalsByKey) -> {
            List<Group> groups = totalsByKey.entrySet().stream()
                    .map(entry -> new Group(entry.getKey(), entry.getValue().toFigures(), null))
                    .sorted(order(groupBy))
                    .toList();
            result.add(new CurrencyGroups(currencyCode, groupBy.isPeriod() ? withCumulativeNet(groups) : groups));
        });
        return new StatsGroupsResponse(groupBy, result);
    }

    private static Comparator<Group> order(GroupBy groupBy) {
        if (groupBy.isPeriod()) {
            // ISO dates, months and years sort as text.
            return Comparator.comparing(group -> group.key().period());
        }
        if (groupBy == GroupBy.BUY_IN) {
            return Comparator.comparing(group -> group.key().buyIn());
        }
        // Most played first; the rest only makes the order stable.
        return Comparator.<Group>comparingLong(group -> group.figures().games()).reversed()
                .thenComparing(group -> group.figures().net(), Comparator.reverseOrder())
                .thenComparing(group -> group.key().toString());
    }

    private static List<Group> withCumulativeNet(List<Group> groups) {
        List<Group> result = new ArrayList<>(groups.size());
        BigDecimal cumulativeNet = BigDecimal.ZERO;
        for (Group group : groups) {
            cumulativeNet = cumulativeNet.add(group.figures().net());
            result.add(new Group(group.key(), group.figures(), cumulativeNet));
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
                case DAY -> period(LocalDate::toString);
                case WEEK -> period(day -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
                case MONTH -> period(day -> YearMonth.from(day).toString());
                case YEAR -> period(day -> String.valueOf(day.getYear()));
                case GAME_TYPE -> new Grouping(0, (game, cb) -> List.of(),
                        row -> new GroupKey(null, row.get(GAME_TYPE, GameType.class), null, null, null, null));
                case VARIANT -> new Grouping(3, (game, cb) -> {
                    Join<Game, Variant> variant = game.join("variant", JoinType.LEFT);
                    return List.of(variant.get("id"), variant.get("code"), variant.get("name"));
                }, row -> {
                    Long id = row.get(FIRST_KEY, Long.class);
                    VariantRef variant = id == null ? null : new VariantRef(
                            id, row.get(FIRST_KEY + 1, String.class), row.get(FIRST_KEY + 2, String.class));
                    return new GroupKey(null, row.get(GAME_TYPE, GameType.class), variant, null, null, null);
                });
                case ROOM -> new Grouping(2,
                        (game, cb) -> List.of(game.get("room").get("id"), game.get("room").get("name")),
                        row -> new GroupKey(null, null, null,
                                new RoomRef(row.get(FIRST_KEY, Long.class), row.get(FIRST_KEY + 1, String.class)),
                                null, null));
                case MODALITY -> new Grouping(1, (game, cb) -> List.of(game.get("modality")),
                        row -> new GroupKey(null, null, null, null, row.get(FIRST_KEY, Modality.class), null));
                case BUY_IN -> new Grouping(1, (game, cb) -> List.of(game.get("buyIn")),
                        row -> new GroupKey(null, null, null, null, null, row.get(FIRST_KEY, BigDecimal.class)));
            };
        }

        private static Grouping period(Function<LocalDate, String> periodOf) {
            return new Grouping(1, (game, cb) -> List.of(game.get("playedOn")),
                    row -> new GroupKey(periodOf.apply(row.get(FIRST_KEY, LocalDate.class)), null, null, null, null, null));
        }
    }
}
