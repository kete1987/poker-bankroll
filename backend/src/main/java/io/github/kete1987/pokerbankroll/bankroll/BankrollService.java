package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import io.github.kete1987.pokerbankroll.bankroll.BankrollEvolutionResponse.CurrencyEvolution;
import io.github.kete1987.pokerbankroll.bankroll.BankrollEvolutionResponse.EvolutionPeriod;
import io.github.kete1987.pokerbankroll.bankroll.BankrollEvolutionResponse.EvolutionSeries;
import io.github.kete1987.pokerbankroll.bankroll.BankrollEvolutionResponse.RoomEvolution;
import io.github.kete1987.pokerbankroll.bankroll.BankrollEvolutionResponse.ConvertedEvolution;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.ConvertedBankroll;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.CurrencyBankroll;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.BankrollFigures;
import io.github.kete1987.pokerbankroll.bankroll.BankrollSummaryResponse.RoomBankroll;
import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.exchange.Converter;
import io.github.kete1987.pokerbankroll.exchange.ExchangeRates;
import io.github.kete1987.pokerbankroll.game.GameResponse.RoomRef;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import io.github.kete1987.pokerbankroll.stats.TimePeriod;
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
    private final ExchangeRates exchangeRates;

    BankrollService(BankrollMovementRepository movements, RoomRepository rooms, CurrencyRepository currencies,
            JdbcClient jdbc, EntityManager entityManager, ExchangeRates exchangeRates) {
        this.movements = movements;
        this.rooms = rooms;
        this.currencies = currencies;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.exchangeRates = exchangeRates;
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
     * per room; and all of it converted to the base currency. Rooms that are inactive and have
     * neither games nor movements are left out.
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

        // Movements per day: added up here as they are, and converted with the rates of their day.
        List<MovementDay> movementDays = jdbc.sql("""
                select m.room_id, coalesce(r.currency_code, m.currency_code) as currency_code, m.type,
                       m.occurred_on, sum(m.amount) as amount
                from bankroll_movement m
                left join room r on r.id = m.room_id
                where (cast(:from as date) is null or m.occurred_on >= cast(:from as date))
                  and (cast(:to as date) is null or m.occurred_on <= cast(:to as date))
                group by m.room_id, coalesce(r.currency_code, m.currency_code), m.type, m.occurred_on
                """).param("from", from).param("to", to).query((row, n) -> new MovementDay(
                        (Long) row.getObject("room_id"), row.getString("currency_code"),
                        MovementType.valueOf(row.getString("type")), row.getObject("occurred_on", LocalDate.class),
                        row.getBigDecimal("amount")))
                .list();
        Map<Long, Totals> byRoom = new HashMap<>();
        Map<String, Totals> withoutRoom = new TreeMap<>();
        for (MovementDay movement : movementDays) {
            if (movement.roomId() == null && !everyRoom) {
                // Movements without a room are of no room in particular.
                continue;
            }
            Totals totals = movement.roomId() == null
                    ? withoutRoom.computeIfAbsent(movement.currencyCode(), code -> new Totals())
                    : byRoom.computeIfAbsent(movement.roomId(), id -> new Totals());
            totals.addMovements(movement.type(), movement.amount());
        }

        List<ListedRoom> listed = new ArrayList<>();
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
                    RoomRef room = new RoomRef(roomId, row.getString("name"));
                    listed.add(new ListedRoom(room, active, currencyCode, totals));
                    roomsByCurrency.computeIfAbsent(currencyCode, code -> new ArrayList<>()).add(
                            new RoomBankroll(room, active, totals.toFigures()));
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
        return new BankrollSummaryResponse(result,
                converted(from, to, listed, withoutRoom, movementDays, totalByCurrency.keySet()));
    }

    /**
     * The summary converted to the base currency: what happened with the rates of its day and,
     * without {@code from}, the bankroll as a balance with the rates of {@code to} or today.
     */
    private ConvertedBankroll converted(@Nullable LocalDate from, @Nullable LocalDate to, List<ListedRoom> listed,
            Map<String, Totals> withoutRoom, List<MovementDay> movementDays, Set<String> currencies) {
        String base = exchangeRates.baseCurrency();
        LocalDate today = LocalDate.now();
        LocalDate balanceDay = from != null ? null : to != null && to.isBefore(today) ? to : today;

        Map<Long, Totals> convertedByRoom = new HashMap<>();
        Map<String, Totals> convertedWithoutRoom = new TreeMap<>();
        Converter converter;
        if (currencies.stream().allMatch(base::equals)) {
            // Nothing to convert: the figures are those of the base currency.
            converter = exchangeRates.converter(base, currencies, null, null);
            listed.forEach(room -> convertedByRoom.put(room.room().id(), room.totals()));
            convertedWithoutRoom.putAll(withoutRoom);
        } else {
            Set<Long> listedIds = listed.stream().map(room -> room.room().id()).collect(Collectors.toSet());
            List<GameDay> gameDays = jdbc.sql("""
                    select g.room_id, r.currency_code, g.played_on,
                           sum(g.net) as net,
                           sum(g.ticket_prize_value) as tickets_won,
                           count(*) filter (where g.status = 'IN_PLAY') as games_in_play,
                           coalesce(sum(g.buy_in * (g.entries - case when g.paid_with_ticket then 1 else 0 end))
                                    filter (where g.status = 'IN_PLAY'), 0) as invested_in_play
                    from game g
                    join room r on r.id = g.room_id
                    where (cast(:from as date) is null or g.played_on >= cast(:from as date))
                      and (cast(:to as date) is null or g.played_on <= cast(:to as date))
                    group by g.room_id, r.currency_code, g.played_on
                    """).param("from", from).param("to", to).query((row, n) -> new GameDay(row.getLong("room_id"),
                            row.getString("currency_code"), row.getObject("played_on", LocalDate.class),
                            row.getBigDecimal("net"), row.getBigDecimal("tickets_won"), row.getLong("games_in_play"),
                            row.getBigDecimal("invested_in_play")))
                    .list().stream().filter(game -> listedIds.contains(game.roomId())).toList();
            List<MovementDay> movements = movementDays.stream()
                    .filter(movement -> movement.roomId() == null
                            ? withoutRoom.containsKey(movement.currencyCode())
                            : listedIds.contains(movement.roomId()))
                    .toList();
            converter = converterFor(base, currencies, balanceDay,
                    Stream.concat(movements.stream().map(MovementDay::day), gameDays.stream().map(GameDay::day)));

            for (MovementDay movement : movements) {
                Totals totals = movement.roomId() == null
                        ? convertedWithoutRoom.computeIfAbsent(movement.currencyCode(), code -> new Totals())
                        : convertedByRoom.computeIfAbsent(movement.roomId(), id -> new Totals());
                totals.addMovements(movement.type(),
                        converter.convertOrZero(movement.amount(), movement.currencyCode(), movement.day()));
            }
            for (GameDay game : gameDays) {
                BigDecimal factor = converter.factor(game.currencyCode(), game.day());
                BigDecimal rate = factor == null ? BigDecimal.ZERO : factor;
                convertedByRoom.computeIfAbsent(game.roomId(), id -> new Totals()).addGames(
                        game.net().multiply(rate), game.ticketsWon().multiply(rate), game.gamesInPlay(),
                        game.investedInPlay().multiply(rate));
            }
            if (balanceDay != null) {
                // The bankroll is a balance: what there is in each currency, at the rates of that day.
                for (ListedRoom room : listed) {
                    convertedByRoom.computeIfAbsent(room.room().id(), id -> new Totals()).revalue(
                            converter.convertOrZero(room.totals().bankroll(), room.currencyCode(), balanceDay));
                }
                withoutRoom.forEach((currencyCode, totals) ->
                        convertedWithoutRoom.computeIfAbsent(currencyCode, code -> new Totals()).revalue(
                                converter.convertOrZero(totals.bankroll(), currencyCode, balanceDay)));
            }
        }

        Totals total = new Totals();
        Totals withoutAnyRoom = new Totals();
        convertedWithoutRoom.values().forEach(withoutAnyRoom::add);
        total.add(withoutAnyRoom);
        List<RoomBankroll> rooms = new ArrayList<>();
        for (ListedRoom room : listed) {
            Totals totals = convertedByRoom.getOrDefault(room.room().id(), new Totals());
            total.add(totals);
            rooms.add(new RoomBankroll(room.room(), room.active(), totals.toFigures()));
        }
        return new ConvertedBankroll(base, total.toFigures(), withoutAnyRoom.toFigures(), rooms, balanceDay,
                converter.missing());
    }

    /** A converter with the rates of the days given and of {@code alsoOn}. */
    private Converter converterFor(String base, Set<String> currencies, @Nullable LocalDate alsoOn,
            Stream<@Nullable LocalDate> days) {
        LocalDate[] span = {alsoOn, alsoOn};
        days.filter(Objects::nonNull).forEach(day -> {
            span[0] = span[0] == null || day.isBefore(span[0]) ? day : span[0];
            span[1] = span[1] == null || day.isAfter(span[1]) ? day : span[1];
        });
        return exchangeRates.converter(base, currencies, span[0], span[1]);
    }

    /**
     * How the bankroll of each currency, and of each of its rooms, changed period by period: the
     * figures of {@link #summary} with dates, one period after another, starting from the bankroll
     * before {@code from}; and all of it converted to the base currency. Only periods with
     * movements or games are listed. With rooms, only those count, without the movements that
     * belong to no room.
     */
    @Transactional(readOnly = true)
    public BankrollEvolutionResponse evolution(TimePeriod groupBy, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable List<Long> roomIds) {
        // A blank parameter arrives as a list holding a null: it is no value.
        Set<Long> onlyRooms = roomIds == null
                ? Set.of()
                : roomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        boolean everyRoom = onlyRooms.isEmpty();

        // Sums per day; whatever happened before the range is one sum without a day.
        List<Flow> flows = new ArrayList<>();
        jdbc.sql("""
                select m.room_id, coalesce(r.currency_code, m.currency_code) as currency_code, m.type,
                       case when m.occurred_on < cast(:from as date) then null else m.occurred_on end as day,
                       sum(m.amount) as amount
                from bankroll_movement m
                left join room r on r.id = m.room_id
                where cast(:to as date) is null or m.occurred_on <= cast(:to as date)
                group by 1, 2, 3, 4
                """).param("from", from).param("to", to).query((row, n) -> {
                    Long roomId = (Long) row.getObject("room_id");
                    if (roomId == null && !everyRoom) {
                        // Movements without a room are of no room in particular.
                        return null;
                    }
                    return flows.add(new Flow(roomId, row.getString("currency_code"),
                            MovementType.valueOf(row.getString("type")), row.getObject("day", LocalDate.class),
                            row.getBigDecimal("amount")));
                }).list();
        jdbc.sql("""
                select g.room_id, r.currency_code,
                       case when g.played_on < cast(:from as date) then null else g.played_on end as day,
                       sum(g.net) as net
                from game g
                join room r on r.id = g.room_id
                where cast(:to as date) is null or g.played_on <= cast(:to as date)
                group by 1, 2, 3
                """).param("from", from).param("to", to).query((row, n) -> flows.add(new Flow(
                        row.getLong("room_id"), row.getString("currency_code"), null,
                        row.getObject("day", LocalDate.class), row.getBigDecimal("net"))))
                .list();

        Map<Long, Evolution> byRoom = new HashMap<>();
        Map<String, Evolution> withoutRoom = new TreeMap<>();
        for (Flow flow : flows) {
            flow.addTo(evolutionOf(flow, byRoom, withoutRoom).of(flow.day(), groupBy));
        }

        List<IncludedRoom> included = new ArrayList<>();
        Map<String, List<RoomEvolution>> roomsByCurrency = new TreeMap<>();
        Map<String, Evolution> totalByCurrency = new TreeMap<>();
        jdbc.sql("select id, name, currency_code, active from room order by lower(name), id").query((row, n) -> {
            long roomId = row.getLong("id");
            Evolution evolution = byRoom.get(roomId);
            if (evolution == null || evolution.isEmpty() || !(everyRoom || onlyRooms.contains(roomId))) {
                return roomId;
            }
            String currencyCode = row.getString("currency_code");
            RoomRef room = new RoomRef(roomId, row.getString("name"));
            included.add(new IncludedRoom(room, row.getBoolean("active"), currencyCode, evolution));
            roomsByCurrency.computeIfAbsent(currencyCode, code -> new ArrayList<>()).add(new RoomEvolution(
                    room, row.getBoolean("active"), evolution.toSeries()));
            totalByCurrency.computeIfAbsent(currencyCode, code -> new Evolution()).add(evolution);
            return roomId;
        }).list();
        withoutRoom.forEach((currencyCode, evolution) ->
                totalByCurrency.computeIfAbsent(currencyCode, code -> new Evolution()).add(evolution));

        List<CurrencyEvolution> result = new ArrayList<>();
        totalByCurrency.forEach((currencyCode, total) -> {
            if (!total.isEmpty()) {
                result.add(new CurrencyEvolution(currencyCode, total.toSeries(),
                        roomsByCurrency.getOrDefault(currencyCode, List.of())));
            }
        });
        return new BankrollEvolutionResponse(groupBy, result,
                convertedEvolution(groupBy, from, to, flows, included, withoutRoom));
    }

    private static Evolution evolutionOf(Flow flow, Map<Long, Evolution> byRoom, Map<String, Evolution> withoutRoom) {
        return flow.roomId() == null
                ? withoutRoom.computeIfAbsent(flow.currencyCode(), code -> new Evolution())
                : byRoom.computeIfAbsent(flow.roomId(), id -> new Evolution());
    }

    /**
     * The evolution converted to the base currency. Every room has every period of the total: a
     * balance in another currency is worth something else at the end of each period. What changed
     * is converted with the rates of its day; the bankroll at the end of a period, with those of its
     * last day (or of {@code to}); the starting bankroll, with those of the day before {@code from}.
     */
    private ConvertedEvolution convertedEvolution(TimePeriod groupBy, @Nullable LocalDate from,
            @Nullable LocalDate to, List<Flow> flows, List<IncludedRoom> included, Map<String, Evolution> withoutRoom) {
        String base = exchangeRates.baseCurrency();
        // The periods of the total: those with anything of a room or of the movements without one.
        Map<LocalDate, PeriodTotals> periods = new TreeMap<>();
        Stream.concat(included.stream().map(IncludedRoom::evolution), withoutRoom.values().stream())
                .filter(evolution -> !evolution.isEmpty())
                .forEach(evolution -> evolution.periods.forEach((first, period) ->
                        periods.putIfAbsent(first, new PeriodTotals(period.key, period.startsOn, period.endsOn))));

        Set<String> currencies = new HashSet<>();
        List<LocalDate> days = new ArrayList<>();
        for (Flow flow : flows) {
            currencies.add(flow.currencyCode());
            if (flow.day() != null) {
                days.add(flow.day());
            }
        }
        LocalDate dayBefore = from == null ? null : from.minusDays(1);
        for (PeriodTotals period : periods.values()) {
            days.add(rateDayOf(period, to));
        }
        Converter converter = converterFor(base, currencies, dayBefore, days.stream());

        // What changed in each period, converted day by day, of each room and of the movements without one.
        Set<Long> includedIds = included.stream().map(room -> room.room().id()).collect(Collectors.toSet());
        Map<Long, Evolution> convertedByRoom = new HashMap<>();
        Map<String, Evolution> convertedWithoutRoom = new HashMap<>();
        for (Flow flow : flows) {
            boolean counts = flow.roomId() == null
                    ? withoutRoom.containsKey(flow.currencyCode()) && !withoutRoom.get(flow.currencyCode()).isEmpty()
                    : includedIds.contains(flow.roomId());
            if (counts && flow.day() != null) {
                flow.converted(converter).addTo(
                        evolutionOf(flow, convertedByRoom, convertedWithoutRoom).of(flow.day(), groupBy));
            }
        }

        List<RoomEvolution> rooms = new ArrayList<>();
        List<EvolutionSeries> parts = new ArrayList<>();
        for (IncludedRoom room : included) {
            EvolutionSeries series = convertedSeries(room.evolution(), convertedByRoom.get(room.room().id()),
                    room.currencyCode(), periods, converter, dayBefore, to);
            rooms.add(new RoomEvolution(room.room(), room.active(), series));
            parts.add(series);
        }
        withoutRoom.forEach((currencyCode, evolution) -> {
            if (!evolution.isEmpty()) {
                parts.add(convertedSeries(evolution, convertedWithoutRoom.get(currencyCode), currencyCode, periods,
                        converter, dayBefore, to));
            }
        });
        return new ConvertedEvolution(base, sum(parts, periods), rooms, converter.missing());
    }

    /**
     * The series of a room (or of the movements without a room of a currency) in the base currency,
     * over every period of the total.
     */
    private static EvolutionSeries convertedSeries(Evolution original, @Nullable Evolution converted,
            String currencyCode, Map<LocalDate, PeriodTotals> periods, Converter converter,
            @Nullable LocalDate dayBefore, @Nullable LocalDate to) {
        BigDecimal balance = original.before.bankroll();
        BigDecimal startingBankroll = dayBefore == null
                ? BigDecimal.ZERO
                : converter.convertOrZero(balance, currencyCode, dayBefore);
        List<EvolutionPeriod> result = new ArrayList<>(periods.size());
        for (Map.Entry<LocalDate, PeriodTotals> entry : periods.entrySet()) {
            PeriodTotals period = entry.getValue();
            PeriodTotals own = original.periods.get(entry.getKey());
            if (own != null) {
                balance = balance.add(own.totals.bankroll());
            }
            PeriodTotals changes = converted == null ? null : converted.periods.get(entry.getKey());
            BankrollFigures figures = (changes == null ? new Totals() : changes.totals).toFigures();
            BigDecimal bankroll = converter.convertOrZero(balance, currencyCode, rateDayOf(period, to));
            result.add(new EvolutionPeriod(period.key, period.startsOn, period.endsOn, figures.deposited(),
                    figures.withdrawn(), figures.bonuses(), figures.adjustments(), figures.gamesNet(),
                    Totals.money(bankroll)));
        }
        return new EvolutionSeries(Totals.money(startingBankroll), result);
    }

    /** The series of the parts added up, period by period. */
    private static EvolutionSeries sum(List<EvolutionSeries> parts, Map<LocalDate, PeriodTotals> periods) {
        BigDecimal startingBankroll = BigDecimal.ZERO;
        for (EvolutionSeries part : parts) {
            startingBankroll = startingBankroll.add(part.startingBankroll());
        }
        List<EvolutionPeriod> result = new ArrayList<>(periods.size());
        int index = 0;
        for (PeriodTotals period : periods.values()) {
            BigDecimal[] sums = new BigDecimal[6];
            java.util.Arrays.fill(sums, BigDecimal.ZERO);
            for (EvolutionSeries part : parts) {
                EvolutionPeriod of = part.periods().get(index);
                sums[0] = sums[0].add(of.deposited());
                sums[1] = sums[1].add(of.withdrawn());
                sums[2] = sums[2].add(of.bonuses());
                sums[3] = sums[3].add(of.adjustments());
                sums[4] = sums[4].add(of.gamesNet());
                sums[5] = sums[5].add(of.bankroll());
            }
            result.add(new EvolutionPeriod(period.key, period.startsOn, period.endsOn, sums[0], sums[1], sums[2],
                    sums[3], sums[4], sums[5]));
            index++;
        }
        return new EvolutionSeries(startingBankroll, result);
    }

    /**
     * The day whose rates value the bankroll at the end of a period: its last day, or {@code to} if
     * earlier, and never after today. A balance is worth what today's rates say, as in the summary,
     * also when the period ends later (the current month) or holds games dated after today.
     */
    private static LocalDate rateDayOf(PeriodTotals period, @Nullable LocalDate to) {
        LocalDate day = to != null && to.isBefore(period.endsOn) ? to : period.endsOn;
        LocalDate today = LocalDate.now();
        return day.isAfter(today) ? today : day;
    }

    /** The bankroll before the range and what changed it in each period, keyed by the first day of the period. */
    private static final class Evolution {
        private final Totals before = new Totals();
        private final Map<LocalDate, PeriodTotals> periods = new TreeMap<>();

        /** The sums of the period holding a day; of the time before the range without a day. */
        Totals of(@Nullable LocalDate day, TimePeriod groupBy) {
            if (day == null) {
                return before;
            }
            return periods.computeIfAbsent(groupBy.firstDayOf(day), first -> new PeriodTotals(
                    groupBy.keyOf(day), first, groupBy.lastDayOf(day))).totals;
        }

        void add(Evolution other) {
            before.add(other.before);
            other.periods.forEach((first, period) -> periods.computeIfAbsent(first,
                    key -> new PeriodTotals(period.key, period.startsOn, period.endsOn)).totals.add(period.totals));
        }

        /** Nothing in the range and no bankroll when it starts: nothing to show. */
        boolean isEmpty() {
            return periods.isEmpty() && before.bankroll().signum() == 0;
        }

        EvolutionSeries toSeries() {
            BigDecimal startingBankroll = before.toFigures().bankroll();
            BigDecimal bankroll = startingBankroll;
            List<EvolutionPeriod> result = new ArrayList<>(periods.size());
            for (PeriodTotals period : periods.values()) {
                BankrollFigures figures = period.totals.toFigures();
                bankroll = bankroll.add(figures.bankroll());
                result.add(new EvolutionPeriod(period.key, period.startsOn, period.endsOn, figures.deposited(),
                        figures.withdrawn(), figures.bonuses(), figures.adjustments(), figures.gamesNet(), bankroll));
            }
            return new EvolutionSeries(startingBankroll, result);
        }
    }

    private record PeriodTotals(String key, LocalDate startsOn, LocalDate endsOn, Totals totals) {
        PeriodTotals(String key, LocalDate startsOn, LocalDate endsOn) {
            this(key, startsOn, endsOn, new Totals());
        }
    }

    /** The movements of a type, or the net of the games ({@code type} null), of a room or currency on a day. */
    private record Flow(@Nullable Long roomId, String currencyCode, @Nullable MovementType type,
            @Nullable LocalDate day, BigDecimal amount) {

        void addTo(Totals totals) {
            if (type == null) {
                totals.addGames(amount, BigDecimal.ZERO, 0, BigDecimal.ZERO);
            } else {
                totals.addMovements(type, amount);
            }
        }

        /** The same, with its amount in the base currency (zero when it has no rate). */
        Flow converted(Converter converter) {
            return new Flow(roomId, currencyCode, type, day, converter.convertOrZero(amount, currencyCode, day));
        }
    }

    /** The movements of a type of a room, or of a currency without a room, on a day. */
    private record MovementDay(@Nullable Long roomId, String currencyCode, MovementType type, LocalDate day,
            BigDecimal amount) {
    }

    /** The games of a room on a day. */
    private record GameDay(long roomId, String currencyCode, LocalDate day, BigDecimal net, BigDecimal ticketsWon,
            long gamesInPlay, BigDecimal investedInPlay) {
    }

    /** A room of the summary, with its sums in its currency. */
    private record ListedRoom(RoomRef room, boolean active, String currencyCode, Totals totals) {
    }

    /** A room of the evolution, with its sums in its currency. */
    private record IncludedRoom(RoomRef room, boolean active, String currencyCode, Evolution evolution) {
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
        /**
         * Converted, what the balance is worth at the rates of one day minus what its parts were
         * worth at the rates of their days; zero otherwise.
         */
        private BigDecimal exchangeDifference = BigDecimal.ZERO;

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
            exchangeDifference = exchangeDifference.add(other.exchangeDifference);
            addGames(other.gamesNet, other.ticketsWon, other.gamesInPlay, other.investedInPlay);
        }

        /** The bankroll is this balance, whatever its parts add up to. */
        void revalue(BigDecimal balance) {
            exchangeDifference = balance.subtract(bankroll().subtract(exchangeDifference));
        }

        /** deposited - withdrawn + adjustments + result, unrounded. */
        BigDecimal bankroll() {
            return deposited.subtract(withdrawn).add(adjustments).add(gamesNet).add(bonuses).add(exchangeDifference);
        }

        BankrollFigures toFigures() {
            return new BankrollFigures(money(deposited), money(withdrawn), money(bonuses), money(adjustments),
                    money(gamesNet), money(gamesNet.add(bonuses)), money(bankroll()), money(ticketsWon), gamesInPlay,
                    money(investedInPlay));
        }

        static BigDecimal money(@Nullable BigDecimal amount) {
            return (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
