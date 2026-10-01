package io.github.kete1987.pokerbankroll.gameimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.game.GameRequest;
import io.github.kete1987.pokerbankroll.game.GameService;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.gameimport.GameCsv.Row;
import io.github.kete1987.pokerbankroll.gameimport.GameImportResponse.ImportCurrencyTotal;
import io.github.kete1987.pokerbankroll.gameimport.GameImportResponse.ImportGameTypeCount;
import io.github.kete1987.pokerbankroll.gameimport.GameImportResponse.ImportRowError;
import io.github.kete1987.pokerbankroll.gameimport.GameImportResponse.ImportedRoom;
import io.github.kete1987.pokerbankroll.gameimport.GameImportResponse.ImportedVariant;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import io.github.kete1987.pokerbankroll.room.RoomRequest;
import io.github.kete1987.pokerbankroll.room.RoomResponse;
import io.github.kete1987.pokerbankroll.room.RoomService;
import io.github.kete1987.pokerbankroll.variant.Variant;
import io.github.kete1987.pokerbankroll.variant.VariantCreateRequest;
import io.github.kete1987.pokerbankroll.variant.VariantRepository;
import io.github.kete1987.pokerbankroll.variant.VariantService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports the games of a CSV file ({@link GameCsv}), all of them or none.
 *
 * <p>A file is always imported for real, row by row, through the same services that record a game,
 * a room or a variant by hand, so the rules are the same; the transaction is then rolled back when
 * a row had an error or it was only a dry run. A dry run therefore finds exactly what the import
 * would find.
 */
@Service
public class GameImportService {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_ROWS = 50_000;
    /** Errors listed in the response; the rest are only counted. */
    static final int MAX_ERRORS_LISTED = 200;

    /** Games kept in the persistence context before it is emptied: it is checked on every flush. */
    private static final int FLUSH_EVERY = 50;
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,9}");
    /** The room of a row while it is being checked, before the room is known. */
    private static final long NO_ROOM_YET = 0L;

    private final GameService gameService;
    private final RoomService roomService;
    private final VariantService variantService;
    private final RoomRepository rooms;
    private final VariantRepository variants;
    private final CurrencyRepository currencies;
    private final Validator validator;
    private final MessageSource messages;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;

    GameImportService(GameService gameService, RoomService roomService, VariantService variantService,
            RoomRepository rooms, VariantRepository variants, CurrencyRepository currencies, Validator validator,
            MessageSource messages, EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.variantService = variantService;
        this.rooms = rooms;
        this.variants = variants;
        this.currencies = currencies;
        this.validator = validator;
        this.messages = messages;
        this.entityManager = entityManager;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public GameImportResponse importGames(byte[] content, boolean dryRun) {
        if (content.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.IMPORT_FILE_TOO_LARGE, MAX_BYTES / (1024 * 1024));
        }
        List<Row> rows = GameCsv.read(content, MAX_ROWS);
        Locale locale = LocaleContextHolder.getLocale();
        return transaction.execute(status -> {
            Run run = new Run(rows, locale);
            run.importRows();
            boolean imported = !dryRun && run.errorCount == 0;
            if (!imported) {
                status.setRollbackOnly();
            }
            return run.response(dryRun, imported);
        });
    }

    /** A room as the import needs it: where to record and in which currency. */
    private record KnownRoom(long id, String currencyCode) {
    }

    /** One import: what it knows about rooms and variants, and what it has found so far. */
    private final class Run {

        private final List<Row> rows;
        private final Locale locale;
        private final Map<String, KnownRoom> roomsByName = new HashMap<>();
        private final Map<String, Long> variantsByKey = new HashMap<>();
        /** For rooms that do not exist: the first currency that exists among those their rows give. */
        private final Map<String, String> currencyOfNewRooms = new HashMap<>();
        private final Map<String, Boolean> knownCurrencies = new HashMap<>();

        private final List<ImportedRoom> newRooms = new ArrayList<>();
        private final List<ImportedVariant> newVariants = new ArrayList<>();
        private final List<ImportRowError> errors = new ArrayList<>();
        private int errorCount;
        private int games;
        private final Map<GameType, Integer> gamesByType = new EnumMap<>(GameType.class);
        private final Map<String, ImportCurrencyTotal> totals = new TreeMap<>();
        private @Nullable LocalDate from;
        private @Nullable LocalDate to;

        Run(List<Row> rows, Locale locale) {
            this.rows = rows;
            this.locale = locale;
            for (Room room : rooms.findAll()) {
                roomsByName.put(key(room.getName()), new KnownRoom(room.getId(), room.getCurrencyCode()));
            }
            for (Variant variant : variants.findAll()) {
                String label = variant.isBuiltIn() ? variant.getCode() : variant.getName();
                if (label != null) {
                    // A code and a name of the same type could be equal: the built-in variant wins.
                    variantsByKey.merge(variantKey(variant.getGameType(), label), variant.getId(),
                            (first, second) -> variant.isBuiltIn() ? second : first);
                }
            }
            for (Row row : rows) {
                String room = row.get(GameCsv.ROOM);
                String currency = row.get(GameCsv.CURRENCY);
                if (room != null && currency != null && !roomsByName.containsKey(key(room)) && exists(currency)) {
                    currencyOfNewRooms.putIfAbsent(key(room), currency.toUpperCase(Locale.ROOT));
                }
            }
        }

        void importRows() {
            int pending = 0;
            for (Row row : rows) {
                int before = errorCount;
                importRow(row);
                if (errorCount == before && ++pending == FLUSH_EVERY) {
                    entityManager.flush();
                    entityManager.clear();
                    pending = 0;
                }
            }
        }

        private void importRow(Row row) {
            if (!row.wellFormed()) {
                problem(row, null, RowProblem.COLUMN_COUNT);
                return;
            }
            int before = errorCount;
            Values values = new Values(row);
            LocalDate playedOn = values.date(GameCsv.PLAYED_ON, true);
            LocalTime playedAt = values.time(GameCsv.PLAYED_AT);
            String roomName = values.text(GameCsv.ROOM, true);
            GameType gameType = values.constant(GameCsv.GAME_TYPE, GameType.class, RowProblem.INVALID_GAME_TYPE, true);
            Modality modality = values.constant(GameCsv.MODALITY, Modality.class, RowProblem.INVALID_MODALITY, false);
            BigDecimal buyIn = values.decimal(GameCsv.BUY_IN, true);
            Integer entries = values.integer(GameCsv.ENTRIES);
            BigDecimal prize = values.decimal(GameCsv.PRIZE, false);
            BigDecimal bounty = values.decimal(GameCsv.BOUNTY, false);
            BigDecimal ticketPrizeValue = values.decimal(GameCsv.TICKET_PRIZE_VALUE, false);
            Boolean paidWithTicket = values.bool(GameCsv.PAID_WITH_TICKET);
            if (errorCount > before || playedOn == null || roomName == null || gameType == null || buyIn == null) {
                return;
            }

            GameRequest unplaced = new GameRequest(playedOn, playedAt, NO_ROOM_YET, gameType, modality, null,
                    GameStatus.FINISHED, row.get(GameCsv.NAME), buyIn, entries, prize, bounty, ticketPrizeValue,
                    row.get(GameCsv.TICKET_DESCRIPTION), paidWithTicket, row.get(GameCsv.NOTES));
            Set<ConstraintViolation<GameRequest>> violations = validator.validate(unplaced);
            if (!violations.isEmpty()) {
                violations.forEach(violation -> violation(row, violation));
                return;
            }

            KnownRoom room = room(row, roomName);
            Long variantId = room == null ? null : variant(row, gameType);
            if (errorCount > before || room == null) {
                return;
            }
            GameRequest request = new GameRequest(playedOn, playedAt, room.id(), gameType, modality, variantId,
                    GameStatus.FINISHED, unplaced.name(), buyIn, entries, prize, bounty, ticketPrizeValue,
                    unplaced.ticketDescription(), paidWithTicket, unplaced.notes());
            String currencyCode;
            try {
                // The currency the game was recorded in: the one the room has now, read under its lock.
                currencyCode = gameService.create(request).currencyCode();
            } catch (ApiException ex) {
                apiError(row, fieldOf(ex.getCode()), ex);
                return;
            }
            String declared = row.get(GameCsv.CURRENCY);
            if (declared != null && !declared.equalsIgnoreCase(currencyCode)) {
                // The room changed its currency while the file was being imported.
                problem(row, GameCsv.CURRENCY, RowProblem.ROOM_CURRENCY_MISMATCH, roomName, currencyCode, declared);
                return;
            }
            count(request, currencyCode);
        }

        /** The room of the row, created if it does not exist; nothing when the row cannot have one. */
        private @Nullable KnownRoom room(Row row, String name) {
            String currency = row.get(GameCsv.CURRENCY);
            if (currency != null && !exists(currency)) {
                apiError(row, GameCsv.CURRENCY, new ApiException(ErrorCode.UNKNOWN_CURRENCY, currency));
                return null;
            }
            KnownRoom room = roomsByName.get(key(name));
            if (room == null) {
                String currencyCode = currencyOfNewRooms.get(key(name));
                if (currencyCode == null) {
                    problem(row, GameCsv.ROOM, RowProblem.ROOM_NEEDS_CURRENCY, name);
                    return null;
                }
                room = createRoom(row, name, currencyCode);
                if (room == null) {
                    return null;
                }
            }
            if (currency != null && !currency.equalsIgnoreCase(room.currencyCode())) {
                problem(row, GameCsv.CURRENCY, RowProblem.ROOM_CURRENCY_MISMATCH, name, room.currencyCode(), currency);
                return null;
            }
            return room;
        }

        private boolean exists(String currency) {
            return knownCurrencies.computeIfAbsent(currency.toUpperCase(Locale.ROOT), currencies::existsById);
        }

        private @Nullable KnownRoom createRoom(Row row, String name, String currencyCode) {
            RoomRequest request = new RoomRequest(name, currencyCode, true);
            Set<ConstraintViolation<RoomRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                violations.forEach(violation -> violation(row, GameCsv.ROOM, violation));
                return null;
            }
            RoomResponse created;
            try {
                created = roomService.create(request);
            } catch (ApiException ex) {
                apiError(row, GameCsv.ROOM, ex);
                return null;
            }
            KnownRoom room = new KnownRoom(created.id(), created.currencyCode());
            roomsByName.put(key(name), room);
            newRooms.add(new ImportedRoom(created.name(), created.currencyCode()));
            return room;
        }

        /**
         * The variant of the row within its game type: a built-in one by its code or one of the
         * user by its name, created when there is none. Nothing when the row has no variant.
         */
        private @Nullable Long variant(Row row, GameType gameType) {
            String label = row.get(GameCsv.VARIANT);
            if (label == null) {
                return null;
            }
            Long id = variantsByKey.get(variantKey(gameType, label));
            if (id != null) {
                return id;
            }
            VariantCreateRequest request = new VariantCreateRequest(gameType, label);
            Set<ConstraintViolation<VariantCreateRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                violations.forEach(violation -> violation(row, GameCsv.VARIANT, violation));
                return null;
            }
            try {
                id = variantService.create(request).id();
            } catch (ApiException ex) {
                apiError(row, GameCsv.VARIANT, ex);
                return null;
            }
            variantsByKey.put(variantKey(gameType, label), id);
            newVariants.add(new ImportedVariant(gameType, label));
            return id;
        }

        private void count(GameRequest game, String currencyCode) {
            games++;
            gamesByType.merge(game.gameType(), 1, Integer::sum);
            LocalDate playedOn = game.playedOn();
            if (from == null || playedOn.isBefore(from)) {
                from = playedOn;
            }
            if (to == null || playedOn.isAfter(to)) {
                to = playedOn;
            }
            BigDecimal net = net(game);
            totals.merge(currencyCode, new ImportCurrencyTotal(currencyCode, 1, net),
                    (total, one) -> new ImportCurrencyTotal(currencyCode, total.games() + 1, total.net().add(net)));
        }

        GameImportResponse response(boolean dryRun, boolean imported) {
            List<ImportGameTypeCount> byType = gamesByType.entrySet().stream()
                    .map(entry -> new ImportGameTypeCount(entry.getKey(), entry.getValue()))
                    .toList();
            return new GameImportResponse(dryRun, imported, rows.size(), games, byType, from, to,
                    totals.values().stream().map(Run::inCents).toList(), newRooms, newVariants, errorCount, errors);
        }

        private static ImportCurrencyTotal inCents(ImportCurrencyTotal total) {
            return new ImportCurrencyTotal(total.currencyCode(), total.games(), total.net().setScale(2));
        }

        private void problem(Row row, @Nullable String field, RowProblem problem, Object... args) {
            error(row, field, problem.name(), messages.getMessage(problem.messageKey(), args, problem.name(), locale));
        }

        private void apiError(Row row, @Nullable String field, ApiException ex) {
            ErrorCode code = ex.getCode();
            error(row, field, code.name(), messages.getMessage(code.messageKey(), ex.getArgs(), code.name(), locale));
        }

        private void violation(Row row, ConstraintViolation<?> violation) {
            violation(row, violation.getPropertyPath().toString(), violation);
        }

        private void violation(Row row, String field, ConstraintViolation<?> violation) {
            String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            // The constraints of the application have their message under their own name.
            String message = messages.getMessage(constraint, null, violation.getMessage(), locale);
            error(row, field.isEmpty() ? null : field, constraint, message == null ? constraint : message);
        }

        private void error(Row row, @Nullable String field, String code, @Nullable String message) {
            errorCount++;
            if (errors.size() < MAX_ERRORS_LISTED) {
                errors.add(new ImportRowError(row.number(), field, code, message == null ? code : message));
            }
        }

        /** Reads the values of a row, reporting the ones that cannot be read. */
        private final class Values {

            private final Row row;

            Values(Row row) {
                this.row = row;
            }

            @Nullable String text(String column, boolean required) {
                String value = row.get(column);
                if (value == null && required) {
                    problem(row, column, RowProblem.REQUIRED);
                }
                return value;
            }

            @Nullable LocalDate date(String column, boolean required) {
                String value = text(column, required);
                try {
                    return value == null ? null : LocalDate.parse(value);
                } catch (DateTimeParseException ex) {
                    problem(row, column, RowProblem.INVALID_DATE, value);
                    return null;
                }
            }

            @Nullable LocalTime time(String column) {
                String value = row.get(column);
                try {
                    return value == null ? null : LocalTime.parse(value);
                } catch (DateTimeParseException ex) {
                    problem(row, column, RowProblem.INVALID_TIME, value);
                    return null;
                }
            }

            @Nullable BigDecimal decimal(String column, boolean required) {
                String value = text(column, required);
                if (value == null) {
                    return null;
                }
                if (!DECIMAL.matcher(value).matches()) {
                    problem(row, column, RowProblem.INVALID_NUMBER, value);
                    return null;
                }
                return new BigDecimal(value);
            }

            @Nullable Integer integer(String column) {
                String value = row.get(column);
                if (value == null) {
                    return null;
                }
                if (!INTEGER.matcher(value).matches()) {
                    problem(row, column, RowProblem.INVALID_INTEGER, value);
                    return null;
                }
                return Integer.valueOf(value);
            }

            @Nullable Boolean bool(String column) {
                String value = row.get(column);
                if (value == null) {
                    return null;
                }
                if (value.equalsIgnoreCase("true")) {
                    return true;
                }
                if (value.equalsIgnoreCase("false")) {
                    return false;
                }
                problem(row, column, RowProblem.INVALID_BOOLEAN, value);
                return null;
            }

            <E extends Enum<E>> @Nullable E constant(String column, Class<E> type, RowProblem invalid, boolean required) {
                String value = text(column, required);
                if (value == null) {
                    return null;
                }
                for (E constant : type.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(value)) {
                        return constant;
                    }
                }
                problem(row, column, invalid, value);
                return null;
            }
        }
    }

    /** Money the game won or lost: tickets count neither when won nor when used. */
    private static BigDecimal net(GameRequest game) {
        int entries = game.entries() == null ? 1 : game.entries();
        int paid = entries - (Boolean.TRUE.equals(game.paidWithTicket()) ? 1 : 0);
        return orZero(game.prize()).add(orZero(game.bounty())).subtract(game.buyIn().multiply(BigDecimal.valueOf(paid)));
    }

    private static BigDecimal orZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** The column an error of the API is about. */
    private static @Nullable String fieldOf(ErrorCode code) {
        return switch (code) {
            case UNKNOWN_ROOM, ROOM_INACTIVE -> GameCsv.ROOM;
            case UNKNOWN_VARIANT, VARIANT_INACTIVE, VARIANT_GAME_TYPE_MISMATCH -> GameCsv.VARIANT;
            default -> null;
        };
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static String variantKey(GameType gameType, String label) {
        return gameType.name() + "\n" + key(label);
    }
}
