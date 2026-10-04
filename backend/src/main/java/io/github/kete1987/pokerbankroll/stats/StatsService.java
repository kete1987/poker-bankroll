package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import io.github.kete1987.pokerbankroll.exchange.Converter;
import io.github.kete1987.pokerbankroll.exchange.ExchangeRates;
import io.github.kete1987.pokerbankroll.game.Game;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.BuyInRange;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.ConvertedGroups;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.CurrencyGroups;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.Group;
import io.github.kete1987.pokerbankroll.stats.StatsGroupsResponse.GroupKey;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.ConvertedSummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.CurrencySummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.InPlay;
import io.github.kete1987.pokerbankroll.tag.Tag;
import io.github.kete1987.pokerbankroll.tag.TagRef;
import io.github.kete1987.pokerbankroll.variant.Variant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistics of the games, per currency and converted to the base currency.
 *
 * <p>Everything is added up in the database by currency, game type and the keys of the request.
 * When the games are in a currency other than the base one, the sums also come per day, so that
 * each day is converted with its own rates; nothing is read game by game.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /** Columns of every row of sums: currency, game type, then the keys of the query, the day, then the sums. */
    private static final int CURRENCY = 0;
    private static final int GAME_TYPE = 1;
    private static final int FIRST_KEY = 2;

    private final EntityManager entityManager;
    private final ExchangeRates exchangeRates;

    StatsService(EntityManager entityManager, ExchangeRates exchangeRates) {
        this.entityManager = entityManager;
        this.exchangeRates = exchangeRates;
    }

    /**
     * Results of the games selected by the filter, per currency and converted to the base currency:
     * finished games in the figures (overall and per game type), games in play apart.
     */
    public StatsSummaryResponse summary(GameFilter filter) {
        String base = exchangeRates.baseCurrency();
        Rows rows = sums(filter, false, 1, (game, cb) -> List.of(game.<GameStatus>get("status")), base);
        Converter converter = rows.converter(base);
        Map<String, CurrencyTotals> currencies = new TreeMap<>();
        CurrencyTotals converted = new CurrencyTotals();
        for (Tuple row : rows.list) {
            String currency = row.get(CURRENCY, String.class);
            boolean finished = row.get(FIRST_KEY, GameStatus.class) == GameStatus.FINISHED;
            currencies.computeIfAbsent(currency, code -> new CurrencyTotals()).add(row, rows.firstSum, finished,
                    BigDecimal.ONE);
            BigDecimal factor = rows.factor(converter, row, currency);
            if (factor != null) {
                converted.add(row, rows.firstSum, finished, factor);
            }
        }
        List<CurrencySummary> summaries = new ArrayList<>();
        currencies.forEach((currencyCode, totals) -> summaries.add(new CurrencySummary(
                currencyCode, totals.finished.toFigures(), totals.byGameType(), totals.inPlay())));
        return new StatsSummaryResponse(summaries, new ConvertedSummary(converter.currencyCode(),
                converted.finished.toFigures(), converted.byGameType(), converted.inPlay(), converter.missing()));
    }

    /** Results of the finished games selected by the filter, per group: per currency and converted. */
    public StatsGroupsResponse groups(GameFilter filter, GroupBy groupBy, boolean byGameType) {
        String base = exchangeRates.baseCurrency();
        Grouping grouping = Grouping.of(groupBy);
        Rows rows = sums(filter, true, grouping.keyCount, grouping.keys, base);
        Converter converter = rows.converter(base);
        Map<String, GroupTotals> currencies = new TreeMap<>();
        GroupTotals converted = new GroupTotals();
        for (Tuple row : rows.list) {
            if ((groupBy == GroupBy.BUY_IN || groupBy == GroupBy.BUY_IN_RANGE)
                    && row.get(GAME_TYPE, GameType.class) == GameType.CASH) {
                continue;
            }
            String currency = row.get(CURRENCY, String.class);
            GroupKey groupKey = grouping.toKey.apply(row);
            // Names are grouped ignoring case: how each is written, with the games written that way.
            String written = groupBy == GroupBy.NAME && groupKey.name() != null
                    ? row.get(FIRST_KEY, String.class).strip()
                    : null;
            currencies.computeIfAbsent(currency, code -> new GroupTotals())
                    .add(row, rows.firstSum, groupKey, written, BigDecimal.ONE, byGameType);
            BigDecimal factor = rows.factor(converter, row, currency);
            if (factor != null) {
                converted.add(row, rows.firstSum, convertedKey(groupBy, groupKey, row, currency, factor), written,
                        factor, byGameType);
            }
        }
        List<CurrencyGroups> result = new ArrayList<>();
        currencies.forEach((currencyCode, totals) ->
                result.add(new CurrencyGroups(currencyCode, totals.toGroups(groupBy, byGameType))));
        return new StatsGroupsResponse(groupBy, result, new ConvertedGroups(converter.currencyCode(),
                converted.toGroups(groupBy, byGameType), converter.missing()));
    }

    /**
     * The key of a group once its amounts are converted: a buy-in is still the one of its currency
     * (5 USD and 5 EUR apart), and a range of buy-ins is the one of the buy-in converted.
     */
    private static GroupKey convertedKey(GroupBy groupBy, GroupKey key, Tuple row, String currency,
            BigDecimal factor) {
        return switch (groupBy) {
            case BUY_IN -> GroupKey.ofBuyIn(key.buyIn(), currency);
            case BUY_IN_RANGE -> GroupKey.ofBuyInRange(Grouping.rangeOf(
                    row.get(FIRST_KEY, BigDecimal.class).multiply(factor).setScale(2, RoundingMode.HALF_UP)));
            default -> key;
        };
    }

    /** A name as most of its games write it. */
    private static GroupKey written(GroupKey key, Map<String, Map<String, Long>> spellings) {
        Map<String, Long> ways = key.name() == null ? null : spellings.get(key.name());
        if (ways == null) {
            return key;
        }
        String name = ways.entrySet().stream()
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
            // Converted, the same buy-in in several currencies: by currency.
            return Comparator.<Group, BigDecimal>comparing(group -> group.key().buyIn())
                    .thenComparing(group -> group.key().currencyCode(), Comparator.nullsFirst(Comparator.naturalOrder()));
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
        // Most played first; the rest only makes the order stable. Games without a name or a tag go last.
        return Comparator.<Group, Boolean>comparing(group -> groupBy == GroupBy.NAME && group.key().name() == null
                        || groupBy == GroupBy.TAG && group.key().tag() == null)
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
     * keys, for the games selected by the filter; also per day when a currency other than the base
     * one is among them, to convert each day with its rates.
     */
    private Rows sums(GameFilter filter, boolean finishedOnly, int keyCount, KeyColumns keys, String base) {
        boolean byDay = currenciesOf(filter, finishedOnly).stream().anyMatch(currency -> !currency.equals(base));
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
        if (byDay) {
            groupedBy.add(game.get("playedOn"));
        }

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
        query.where(selected(filter, finishedOnly, game, query, cb));
        query.groupBy(groupedBy);
        return new Rows(entityManager.createQuery(query).getResultList(), FIRST_KEY + keyCount, byDay);
    }

    /** The currencies of the games selected by the filter. */
    private List<String> currenciesOf(GameFilter filter, boolean finishedOnly) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<Game> game = query.from(Game.class);
        query.select(game.get("room").get("currencyCode")).distinct(true)
                .where(selected(filter, finishedOnly, game, query, cb));
        return entityManager.createQuery(query).getResultList();
    }

    private static Predicate[] selected(GameFilter filter, boolean finishedOnly, Root<Game> game,
            CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> selected = new ArrayList<>();
        Predicate filtered = filter.toSpecification().toPredicate(game, query, cb);
        if (filtered != null) {
            selected.add(filtered);
        }
        if (finishedOnly) {
            selected.add(cb.equal(game.get("status"), GameStatus.FINISHED));
        }
        return selected.toArray(Predicate[]::new);
    }

    private static Expression<Integer> countIf(CriteriaBuilder cb, Predicate condition) {
        return cb.sum(cb.<Integer>selectCase().when(condition, 1).otherwise(0));
    }

    /** Adds the sums of a row, which start at {@code firstSum}, with its amounts multiplied by {@code factor}. */
    private static void add(Tuple row, int firstSum, GameTotals totals, BigDecimal factor) {
        int i = firstSum;
        totals.add(row.get(GAME_TYPE, GameType.class), count(row, i), count(row, i + 1), count(row, i + 2),
                count(row, i + 3), count(row, i + 4), amount(row, i + 5, factor), amount(row, i + 6, factor),
                amount(row, i + 7, factor), amount(row, i + 8, factor), amount(row, i + 9, factor),
                amount(row, i + 10, factor));
    }

    private static long count(Tuple row, int index) {
        return ((Number) row.get(index)).longValue();
    }

    private static BigDecimal amount(Tuple row, int index, BigDecimal factor) {
        Object value = row.get(index);
        BigDecimal amount = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        return factor == BigDecimal.ONE ? amount : amount.multiply(factor);
    }

    /** The rows of sums, where their sums start and whether they come per day (the column before the sums). */
    private final class Rows {
        final List<Tuple> list;
        final int firstSum;
        final boolean byDay;

        Rows(List<Tuple> list, int keysEnd, boolean byDay) {
            this.list = list;
            this.byDay = byDay;
            this.firstSum = byDay ? keysEnd + 1 : keysEnd;
        }

        /** The day of a row; {@code null} when they do not come per day (then all are in the base currency). */
        @Nullable LocalDate dayOf(Tuple row) {
            return byDay ? row.get(firstSum - 1, LocalDate.class) : null;
        }

        /**
         * What converts the amounts of a row, {@code null} when a rate is missing. A row without
         * money (freerolls that won nothing) needs no rate: its games still count.
         */
        @Nullable BigDecimal factor(Converter converter, Tuple row, String currency) {
            for (int index = firstSum; index < row.getElements().size(); index++) {
                if (row.get(index) instanceof BigDecimal amount && amount.signum() != 0) {
                    return converter.factor(currency, dayOf(row));
                }
            }
            return BigDecimal.ZERO;
        }

        /** A converter with the rates of the currencies and days of the rows. */
        Converter converter(String base) {
            Set<String> currencies = new HashSet<>();
            LocalDate first = null;
            LocalDate last = null;
            for (Tuple row : list) {
                currencies.add(row.get(CURRENCY, String.class));
                LocalDate day = dayOf(row);
                if (day != null) {
                    first = first == null || day.isBefore(first) ? day : first;
                    last = last == null || day.isAfter(last) ? day : last;
                }
            }
            return exchangeRates.converter(base, currencies, first, last);
        }
    }

    /** The finished games, overall and per game type, and the games in play, of a currency or converted. */
    private static final class CurrencyTotals {
        final GameTotals finished = new GameTotals();
        final Map<GameType, GameTotals> perGameType = new EnumMap<>(GameType.class);
        final GameTotals inPlay = new GameTotals();

        void add(Tuple row, int firstSum, boolean isFinished, BigDecimal factor) {
            if (isFinished) {
                StatsService.add(row, firstSum, finished, factor);
                StatsService.add(row, firstSum,
                        perGameType.computeIfAbsent(row.get(GAME_TYPE, GameType.class), type -> new GameTotals()),
                        factor);
            } else {
                StatsService.add(row, firstSum, inPlay, factor);
            }
        }

        List<GameTypeSummary> byGameType() {
            return gameTypesOf(perGameType);
        }

        InPlay inPlay() {
            return new InPlay(inPlay.games(), inPlay.invested());
        }
    }

    /** The groups of a currency, or converted: their sums, also per game type, and how names are written. */
    private static final class GroupTotals {
        final Map<GroupKey, GameTotals> totals = new HashMap<>();
        final Map<GroupKey, Map<GameType, GameTotals>> perGameType = new HashMap<>();
        final Map<String, Map<String, Long>> spellings = new HashMap<>();

        void add(Tuple row, int firstSum, GroupKey key, @Nullable String written, BigDecimal factor,
                boolean byGameType) {
            if (written != null) {
                spellings.computeIfAbsent(key.name(), name -> new HashMap<>())
                        .merge(written, count(row, firstSum), Long::sum);
            }
            StatsService.add(row, firstSum, totals.computeIfAbsent(key, any -> new GameTotals()), factor);
            if (byGameType) {
                StatsService.add(row, firstSum, perGameType
                        .computeIfAbsent(key, any -> new EnumMap<>(GameType.class))
                        .computeIfAbsent(row.get(GAME_TYPE, GameType.class), type -> new GameTotals()), factor);
            }
        }

        List<Group> toGroups(GroupBy groupBy, boolean byGameType) {
            List<Group> groups = totals.entrySet().stream()
                    .map(entry -> new Group(written(entry.getKey(), spellings), entry.getValue().toFigures(), null,
                            byGameType ? gameTypesOf(perGameType.get(entry.getKey())) : null))
                    .sorted(order(groupBy))
                    .toList();
            return groupBy.isPeriod() ? withCumulativeNet(groups) : groups;
        }
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
                // One row per tag of a game: a game with several tags counts in each of their groups.
                case TAG -> new Grouping(2, (game, cb) -> {
                    Join<Game, Tag> tag = game.join("tags", JoinType.LEFT);
                    return List.of(tag.get("id"), tag.get("name"));
                }, row -> {
                    Long id = row.get(FIRST_KEY, Long.class);
                    return GroupKey.ofTag(id == null ? null : new TagRef(id, row.get(FIRST_KEY + 1, String.class)));
                });
            };
        }

        /** Where each range of buy-ins ends and the next one starts; the last one has no end. */
        private static final List<BigDecimal> RANGE_ENDS = Stream.of("1", "2", "5", "10", "20", "50")
                .map(BigDecimal::new).toList();

        static BuyInRange rangeOf(BigDecimal buyIn) {
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
