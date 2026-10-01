package io.github.kete1987.pokerbankroll.stats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.game.Game;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.CurrencySummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.GameTypeSummary;
import io.github.kete1987.pokerbankroll.stats.StatsSummaryResponse.InPlay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatsService {

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
        for (Tuple row : sumsByCurrencyTypeAndStatus(filter)) {
            CurrencyTotals totals = currencies.computeIfAbsent(row.get(0, String.class), code -> new CurrencyTotals());
            GameType gameType = row.get(1, GameType.class);
            boolean finished = row.get(2, GameStatus.class) == GameStatus.FINISHED;
            GameTotals[] targets = finished
                    ? new GameTotals[] {totals.finished, totals.byGameType.computeIfAbsent(gameType, t -> new GameTotals())}
                    : new GameTotals[] {totals.inPlay};
            for (GameTotals target : targets) {
                target.add(gameType, count(row, 3), count(row, 4), count(row, 5), count(row, 6), count(row, 7),
                        amount(row, 8), amount(row, 9), amount(row, 10), amount(row, 11), amount(row, 12),
                        amount(row, 13));
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

    /** One row of sums per currency, game type and status; the columns are read by position above. */
    private List<Tuple> sumsByCurrencyTypeAndStatus(GameFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Game> game = query.from(Game.class);

        Path<String> currencyCode = game.get("room").get("currencyCode");
        Path<GameType> gameType = game.get("gameType");
        Path<GameStatus> status = game.get("status");
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

        query.multiselect(
                currencyCode,
                gameType,
                status,
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
                cb.sum(net));
        Predicate selected = filter.toSpecification().toPredicate(game, query, cb);
        if (selected != null) {
            query.where(selected);
        }
        query.groupBy(currencyCode, gameType, status);
        return entityManager.createQuery(query).getResultList();
    }

    private static Expression<Integer> countIf(CriteriaBuilder cb, Predicate condition) {
        return cb.sum(cb.<Integer>selectCase().when(condition, 1).otherwise(0));
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
}
