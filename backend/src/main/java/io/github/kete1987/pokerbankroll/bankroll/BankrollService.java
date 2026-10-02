package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.CurrencyBankroll;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.BankrollFigures;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.RoomBankroll;
import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import org.hibernate.jpa.HibernateHints;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankrollService {

    /** Newest first; the id makes the order total, so pages are stable. */
    private static final Sort NEWEST_FIRST = Sort.by(Order.desc("occurredOn"), Order.desc("id"));
    private static final Sort OLDEST_FIRST = Sort.by(Order.asc("occurredOn"), Order.asc("id"));
    /** Rows read at a time when every movement of a filter is gone through. */
    private static final int STREAM_FETCH_SIZE = 500;

    private final BankrollMovementRepository movements;
    private final RoomRepository rooms;
    private final CurrencyRepository currencies;
    private final JdbcClient jdbc;
    private final EntityManager entityManager;

    BankrollService(BankrollMovementRepository movements, RoomRepository rooms, CurrencyRepository currencies,
            JdbcClient jdbc, EntityManager entityManager) {
        this.movements = movements;
        this.rooms = rooms;
        this.currencies = currencies;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementResponse> list(MovementFilter filter, int page, int size) {
        return PageResponse.of(
                movements.findAll(filter.toSpecification(), PageRequest.of(page, size, NEWEST_FIRST)),
                MovementResponse::of);
    }

    /**
     * Hands over every movement the filter selects, oldest first, for an export: they are read
     * from the database a few at a time and none is kept once it has been handed over.
     */
    @Transactional(readOnly = true)
    public void forEach(MovementFilter filter, Consumer<MovementResponse> action) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BankrollMovement> query = cb.createQuery(BankrollMovement.class);
        Root<BankrollMovement> movement = query.from(BankrollMovement.class);
        movement.fetch("room", JoinType.LEFT);
        query.select(movement)
                .where(filter.toSpecification().toPredicate(movement, query, cb))
                .orderBy(QueryUtils.toOrders(OLDEST_FIRST, movement, cb));
        // With a fetch size, inside a transaction, PostgreSQL sends the rows through a cursor.
        try (Stream<BankrollMovement> found = entityManager.createQuery(query)
                .setHint(HibernateHints.HINT_FETCH_SIZE, STREAM_FETCH_SIZE)
                .getResultStream()) {
            found.forEach(one -> {
                action.accept(MovementResponse.of(one));
                // Rooms stay: they are few and shared by the movements.
                entityManager.detach(one);
            });
        }
    }

    @Transactional(readOnly = true)
    public MovementResponse get(long id) {
        return MovementResponse.of(find(id));
    }

    public MovementResponse create(MovementRequest request) {
        BankrollMovement movement = new BankrollMovement();
        apply(request, movement);
        return MovementResponse.of(movements.saveAndFlush(movement));
    }

    public MovementResponse update(long id, MovementRequest request) {
        BankrollMovement movement = find(id);
        apply(request, movement);
        return MovementResponse.of(movements.saveAndFlush(movement));
    }

    public void delete(long id) {
        movements.delete(find(id));
    }

    private BankrollMovement find(long id) {
        return movements.findWithRoomById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * Copies the request into the movement; rules within the request itself are already validated.
     * Unlike games, movements are accepted in inactive rooms: money is usually withdrawn from a
     * room after it stops being played.
     */
    private void apply(MovementRequest request, BankrollMovement movement) {
        if (request.roomId() != null) {
            Room room = rooms.findToRecordInById(request.roomId())
                    .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_ROOM, String.valueOf(request.roomId())));
            movement.setOwner(room, null);
        } else {
            String currencyCode = request.currencyCode().strip();
            if (!currencies.existsById(currencyCode)) {
                throw new ApiException(ErrorCode.UNKNOWN_CURRENCY, currencyCode);
            }
            movement.setOwner(null, currencyCode);
        }
        movement.setOccurredOn(request.occurredOn());
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().strip());
    }

    /**
     * The poker bankroll per currency: its total, the movements that belong to no room and one row
     * per room. Rooms that are inactive and have neither games nor movements are left out.
     *
     * <p>With dates, only the movements and games of that period count, so the bankroll is what it
     * changed in it and the result what was won or lost. With rooms, only those are listed and
     * added up, without the movements that belong to no room.
     */
    @Transactional(readOnly = true)
    public BankrollSummaryResponse summary(@Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable List<Long> roomIds) {
        // A blank parameter arrives as a list holding a null: it is no value.
        Set<Long> onlyRooms = roomIds == null
                ? Set.of()
                : roomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        boolean everyRoom = onlyRooms.isEmpty();

        Map<Long, Totals> byRoom = new HashMap<>();
        Map<String, Totals> withoutRoom = new TreeMap<>();
        jdbc.sql("""
                select m.room_id, coalesce(r.currency_code, m.currency_code) as currency_code, m.type,
                       sum(m.amount) as amount
                from bankroll_movement m
                left join room r on r.id = m.room_id
                where (cast(:from as date) is null or m.occurred_on >= cast(:from as date))
                  and (cast(:to as date) is null or m.occurred_on <= cast(:to as date))
                group by m.room_id, coalesce(r.currency_code, m.currency_code), m.type
                """).param("from", from).param("to", to).query((row, n) -> {
                    long roomId = row.getLong("room_id");
                    if (row.wasNull() && !everyRoom) {
                        // Movements without a room are of no room in particular.
                        return null;
                    }
                    Totals totals = row.wasNull()
                            ? withoutRoom.computeIfAbsent(row.getString("currency_code"), code -> new Totals())
                            : byRoom.computeIfAbsent(roomId, id -> new Totals());
                    totals.addMovements(MovementType.valueOf(row.getString("type")), row.getBigDecimal("amount"));
                    return totals;
                }).list();

        Map<String, List<RoomBankroll>> roomsByCurrency = new TreeMap<>();
        Map<String, Totals> totalByCurrency = new TreeMap<>();
        jdbc.sql("""
                select r.id, r.name, r.currency_code, r.active,
                       exists (select 1 from game where room_id = r.id)
                           or exists (select 1 from bankroll_movement where room_id = r.id) as has_history,
                       coalesce(sum(g.net), 0) as games_net,
                       coalesce(sum(g.ticket_prize_value), 0) as tickets_won,
                       count(g.id) filter (where g.status = 'IN_PLAY') as games_in_play,
                       coalesce(sum(g.buy_in * (g.entries - case when g.paid_with_ticket then 1 else 0 end))
                                filter (where g.status = 'IN_PLAY'), 0) as invested_in_play
                from room r
                left join game g on g.room_id = r.id
                    and (cast(:from as date) is null or g.played_on >= cast(:from as date))
                    and (cast(:to as date) is null or g.played_on <= cast(:to as date))
                group by r.id
                order by lower(r.name), r.id
                """).param("from", from).param("to", to).query((row, n) -> {
                    long roomId = row.getLong("id");
                    Totals movementsOfRoom = byRoom.get(roomId);
                    boolean active = row.getBoolean("active");
                    if (!everyRoom && !onlyRooms.contains(roomId)) {
                        return roomId;
                    }
                    // Whatever the dates asked for, so the same rooms are listed for every period.
                    if (!active && !row.getBoolean("has_history")) {
                        return roomId;
                    }
                    Totals totals = movementsOfRoom == null ? new Totals() : movementsOfRoom;
                    totals.addGames(row.getBigDecimal("games_net"), row.getBigDecimal("tickets_won"),
                            row.getLong("games_in_play"), row.getBigDecimal("invested_in_play"));
                    String currencyCode = row.getString("currency_code");
                    roomsByCurrency.computeIfAbsent(currencyCode, code -> new ArrayList<>()).add(
                            new RoomBankroll(new RoomRef(roomId, row.getString("name")), active, totals.toFigures()));
                    totalByCurrency.computeIfAbsent(currencyCode, code -> new Totals()).add(totals);
                    return roomId;
                }).list();
        withoutRoom.forEach((currencyCode, totals) ->
                totalByCurrency.computeIfAbsent(currencyCode, code -> new Totals()).add(totals));

        List<CurrencyBankroll> result = new ArrayList<>();
        totalByCurrency.forEach((currencyCode, total) -> result.add(new CurrencyBankroll(
                currencyCode,
                total.toFigures(),
                withoutRoom.getOrDefault(currencyCode, new Totals()).toFigures(),
                roomsByCurrency.getOrDefault(currencyCode, List.of()))));
        return new BankrollSummaryResponse(result);
    }

    /** Sums from which the figures of a room, of the movements without a room or of a currency derive. */
    private static final class Totals {
        private BigDecimal deposited = BigDecimal.ZERO;
        private BigDecimal withdrawn = BigDecimal.ZERO;
        private BigDecimal bonuses = BigDecimal.ZERO;
        private BigDecimal adjustments = BigDecimal.ZERO;
        private BigDecimal gamesNet = BigDecimal.ZERO;
        private BigDecimal ticketsWon = BigDecimal.ZERO;
        private long gamesInPlay;
        private BigDecimal investedInPlay = BigDecimal.ZERO;

        void addMovements(MovementType type, BigDecimal amount) {
            switch (type) {
                case DEPOSIT -> deposited = deposited.add(amount);
                case WITHDRAWAL -> withdrawn = withdrawn.add(amount);
                case BONUS -> bonuses = bonuses.add(amount);
                case ADJUSTMENT -> adjustments = adjustments.add(amount);
            }
        }

        void addGames(BigDecimal net, BigDecimal tickets, long inPlay, BigDecimal invested) {
            gamesNet = gamesNet.add(net);
            ticketsWon = ticketsWon.add(tickets);
            gamesInPlay += inPlay;
            investedInPlay = investedInPlay.add(invested);
        }

        void add(Totals other) {
            deposited = deposited.add(other.deposited);
            withdrawn = withdrawn.add(other.withdrawn);
            bonuses = bonuses.add(other.bonuses);
            adjustments = adjustments.add(other.adjustments);
            addGames(other.gamesNet, other.ticketsWon, other.gamesInPlay, other.investedInPlay);
        }

        BankrollFigures toFigures() {
            BigDecimal result = gamesNet.add(bonuses);
            return new BankrollFigures(money(deposited), money(withdrawn), money(bonuses), money(adjustments),
                    money(gamesNet), money(result),
                    money(deposited.subtract(withdrawn).add(adjustments).add(result)),
                    money(ticketsWon), gamesInPlay, money(investedInPlay));
        }

        private static BigDecimal money(@Nullable BigDecimal amount) {
            return (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
