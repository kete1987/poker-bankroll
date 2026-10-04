package io.github.kete1987.pokerbankroll.backup;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import io.github.kete1987.pokerbankroll.backup.BackupData.GameData;
import io.github.kete1987.pokerbankroll.backup.BackupData.LogoData;
import io.github.kete1987.pokerbankroll.backup.BackupData.MovementData;
import io.github.kete1987.pokerbankroll.backup.BackupData.RateData;
import io.github.kete1987.pokerbankroll.backup.BackupData.RoomData;
import io.github.kete1987.pokerbankroll.backup.BackupData.TemplateData;
import io.github.kete1987.pokerbankroll.backup.BackupData.VariantData;
import io.github.kete1987.pokerbankroll.backup.BackupRestoreResponse.BackupContents;
import io.github.kete1987.pokerbankroll.backup.BackupRestoreResponse.BackupError;
import io.github.kete1987.pokerbankroll.bankroll.BankrollMovement;
import io.github.kete1987.pokerbankroll.bankroll.BankrollMovementRepository;
import io.github.kete1987.pokerbankroll.bankroll.MovementRequest;
import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.exchange.ExchangeRatesNeeded;
import io.github.kete1987.pokerbankroll.exchange.ManualRateRequest;
import io.github.kete1987.pokerbankroll.game.Game;
import io.github.kete1987.pokerbankroll.game.GameRequest;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomLogo;
import io.github.kete1987.pokerbankroll.room.RoomLogoRepository;
import io.github.kete1987.pokerbankroll.room.RoomLogoService;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import io.github.kete1987.pokerbankroll.room.RoomRequest;
import io.github.kete1987.pokerbankroll.template.GameTemplate;
import io.github.kete1987.pokerbankroll.template.GameTemplateRepository;
import io.github.kete1987.pokerbankroll.template.GameTemplateRequest;
import io.github.kete1987.pokerbankroll.tag.Tag;
import io.github.kete1987.pokerbankroll.tag.TagRef;
import io.github.kete1987.pokerbankroll.variant.Variant;
import io.github.kete1987.pokerbankroll.variant.VariantCreateRequest;
import io.github.kete1987.pokerbankroll.variant.VariantRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backs up everything the installation holds into one file ({@link BackupFormat}) and restores
 * such a file, replacing everything.
 *
 * <p>A restore is all or nothing, in one transaction. The content of the file is first checked as
 * a whole, with the constraints of the requests of the API where they apply; then the installation
 * is emptied and the file written, through the entities (the games, which are many, with plain SQL
 * in batches) and not through the services that record a game by hand: a backup holds states the API does not create afresh (games in an inactive room
 * or with an inactive variant, in play or finished). A dry run does the same and rolls back, so it
 * finds what the restore would find.
 */
@Service
public class BackupService {

    /**
     * Largest file accepted. A game takes about 250 bytes of JSON and a logo, 256 kB at most, up to
     * 350 kB in Base64: 32 MB hold more than 100,000 games with their notes and dozens of logos,
     * and the file is read into memory as a whole, so it cannot be unbounded.
     */
    static final int MAX_BYTES = 32 * 1024 * 1024;
    /** Errors listed in the response; the rest are only counted. */
    static final int MAX_ERRORS_LISTED = 200;

    /** Games read before the persistence context is emptied, and games written in one batch. */
    private static final int BATCH = 500;

    private final RoomRepository rooms;
    private final RoomLogoRepository logos;
    private final RoomLogoService logoService;
    private final VariantRepository variants;
    private final BankrollMovementRepository movements;
    private final GameTemplateRepository templates;
    private final CurrencyRepository currencies;
    private final Validator validator;
    private final MessageSource messages;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate snapshot;
    private final TransactionTemplate transaction;
    private final String appVersion;
    private final ApplicationEventPublisher events;

    BackupService(RoomRepository rooms, RoomLogoRepository logos, RoomLogoService logoService,
            VariantRepository variants, BankrollMovementRepository movements, GameTemplateRepository templates,
            CurrencyRepository currencies, Validator validator, MessageSource messages, EntityManager entityManager, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, ObjectProvider<BuildProperties> build,
            ApplicationEventPublisher events) {
        this.events = events;
        this.rooms = rooms;
        this.logos = logos;
        this.logoService = logoService;
        this.variants = variants;
        this.movements = movements;
        this.templates = templates;
        this.currencies = currencies;
        this.validator = validator;
        this.messages = messages;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        // What is backed up is one moment of the database, whatever is recorded meanwhile.
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setReadOnly(true);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transaction = new TransactionTemplate(transactionManager);
        // The version the API reports (the one of its OpenAPI document).
        this.appVersion = build.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
    }

    // ---- backup ----

    /** Everything the installation holds, as a file to download. */
    public BackupFile backup() {
        BackupData data = Objects.requireNonNull(snapshot.execute(status -> read()));
        return new BackupFile("poker-bankroll-backup-" + LocalDate.now() + ".json", BackupFormat.write(data));
    }

    /** The ids of rooms and variants are the ones of the database: they only have to be unique. */
    private BackupData read() {
        Map<Long, RoomLogo> logoByRoom = logos.findAll().stream()
                .collect(Collectors.toMap(RoomLogo::getRoomId, Function.identity()));
        List<@Nullable RoomData> roomData = new ArrayList<>();
        for (Room room : rooms.findAll(Sort.by("id"))) {
            RoomLogo logo = logoByRoom.get(room.getId());
            roomData.add(new RoomData(room.getId(), room.getName(), room.getCurrencyCode(), room.isActive(),
                    logo == null ? null : new LogoData(logo.getContentType(), logo.getContent())));
        }
        List<@Nullable VariantData> variantData = new ArrayList<>();
        for (Variant variant : variants.findAll(Sort.by("id"))) {
            variantData.add(new VariantData(variant.getId(), variant.getGameType(), variant.getCode(),
                    variant.getName(), variant.isActive()));
        }
        List<@Nullable TemplateData> templateData = new ArrayList<>();
        for (GameTemplate template : templates.findAll(Sort.by("id"))) {
            Variant variant = template.getVariant();
            templateData.add(new TemplateData(template.getLabel(), template.getRoom().getId(),
                    template.getGameType(), template.getModality(), variant == null ? null : variant.getId(),
                    template.getGameName(), template.getBuyIn()));
        }
        entityManager.clear();

        // In the order they were recorded, which is the order they are restored in: games of the
        // same day and time stay in the same order.
        List<@Nullable GameData> gameData = new ArrayList<>();
        long after = 0;
        while (true) {
            List<Game> page = entityManager
                    .createQuery("select g from Game g where g.id > :after order by g.id", Game.class)
                    .setParameter("after", after)
                    .setMaxResults(BATCH)
                    .getResultList();
            for (Game game : page) {
                Variant variant = game.getVariant();
                gameData.add(new GameData(game.getPlayedOn(), game.getPlayedAt(), game.getRoom().getId(),
                        game.getGameType(), game.getModality(), variant == null ? null : variant.getId(),
                        game.getStatus(), game.getName(), game.getBuyIn(), game.getEntries(), game.getPrize(),
                        game.getBounty(), game.getTicketPrizeValue(), game.getTicketDescription(),
                        game.isPaidWithTicket(), game.getNotes(),
                        game.getTags().stream().map(Tag::toRef).sorted(TagRef.BY_NAME).map(TagRef::name).toList()));
            }
            entityManager.clear();
            if (page.size() < BATCH) {
                break;
            }
            after = page.getLast().getId();
        }

        List<@Nullable MovementData> movementData = new ArrayList<>();
        for (BankrollMovement movement : movements.findAll(Sort.by("id"))) {
            Room room = movement.getRoom();
            movementData.add(new MovementData(movement.getOccurredOn(), movement.getType(),
                    room == null ? null : room.getId(),
                    room == null ? movement.getEffectiveCurrencyCode() : null,
                    movement.getAmount(), movement.getNotes()));
        }
        String baseCurrency = jdbc.queryForObject("select base_currency_code from currency_setting", String.class);
        List<@Nullable RateData> rateData = new ArrayList<>(jdbc.query("""
                select currency_code, rate_date, rate from exchange_rate where source = 'MANUAL'
                order by currency_code, rate_date
                """, (row, number) -> new RateData(row.getString("currency_code"),
                        row.getObject("rate_date", LocalDate.class), row.getBigDecimal("rate"))));
        return new BackupData(BackupFormat.CURRENT_VERSION, appVersion, Instant.now().truncatedTo(ChronoUnit.SECONDS),
                roomData, variantData, gameData, movementData, templateData, baseCurrency, rateData);
    }

    // ---- restore ----

    /**
     * Replaces everything the installation holds with the content of a backup file, or only checks
     * it ({@code dryRun}).
     *
     * @param replace the caller knows the installation has data and that it is deleted; without it
     *                only an empty installation is restored into
     * @throws ApiException when the file cannot be read as a whole ({@code BACKUP_FILE_*},
     *                      {@code BACKUP_FORMAT_TOO_NEW}), or {@code BACKUP_REPLACE_NOT_CONFIRMED}
     */
    public BackupRestoreResponse restore(byte[] content, boolean dryRun, boolean replace) {
        if (content.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.BACKUP_FILE_TOO_LARGE, MAX_BYTES / (1024 * 1024));
        }
        BackupData data = BackupFormat.read(content);
        Locale locale = LocaleContextHolder.getLocale();
        return Objects.requireNonNull(transaction.execute(status -> {
            // Nothing is recorded while the installation is replaced: writers wait, readers do not.
            // Rooms first, as recording a game or a movement locks its room before it writes.
            entityManager.createNativeQuery("lock table room, room_logo, variant, game, bankroll_movement, tag, game_tag, "
                    + "game_template, exchange_rate, currency_setting in exclusive mode").executeUpdate();
            BackupContents current = currentContents();
            if (!dryRun && !replace && !current.empty()) {
                throw new ApiException(ErrorCode.BACKUP_REPLACE_NOT_CONFIRMED);
            }
            Restore restore = new Restore(data, locale);
            restore.check();
            if (restore.errorCount == 0) {
                restore.write();
            }
            boolean restored = !dryRun && restore.errorCount == 0;
            if (!restored) {
                status.setRollbackOnly();
            } else {
                // Other currencies, or another base currency: their rates are downloaded once committed.
                events.publishEvent(new ExchangeRatesNeeded());
            }
            return new BackupRestoreResponse(dryRun, restored, data.formatVersion(), data.appVersion(),
                    data.exportedAt(), contentsOf(data), current, restore.errorCount, restore.errors);
        }));
    }

    private BackupContents currentContents() {
        return Objects.requireNonNull(jdbc.queryForObject("""
                select (select count(*) from room) as rooms,
                       (select count(*) from variant where code is null) as variants,
                       (select count(*) from game) as games,
                       (select count(*) from game where status = 'IN_PLAY') as games_in_play,
                       (select count(*) from bankroll_movement) as movements,
                       (select count(*) from game_template) as templates,
                       (select min(played_on) from game) as first_game,
                       (select max(played_on) from game) as last_game
                """, (row, number) -> contents(row.getInt("rooms"), row.getInt("variants"), row.getInt("games"),
                        row.getInt("games_in_play"), row.getInt("movements"), row.getInt("templates"),
                        row.getObject("first_game", LocalDate.class), row.getObject("last_game", LocalDate.class))));
    }

    private static BackupContents contentsOf(BackupData data) {
        List<GameData> games = data.games().stream().filter(Objects::nonNull).toList();
        List<LocalDate> dates = games.stream().map(GameData::playedOn).filter(Objects::nonNull).sorted().toList();
        return contents(data.rooms().size(),
                (int) data.variants().stream().filter(variant -> variant != null && !variant.isBuiltIn()).count(),
                data.games().size(),
                (int) games.stream().filter(game -> game.status() == GameStatus.IN_PLAY).count(),
                data.movements().size(),
                data.templates().size(),
                dates.isEmpty() ? null : dates.getFirst(),
                dates.isEmpty() ? null : dates.getLast());
    }

    /** Templates do not count to say whether it is empty: there are none without a room. */
    private static BackupContents contents(int rooms, int variants, int games, int gamesInPlay, int movements,
            int templates, @Nullable LocalDate from, @Nullable LocalDate to) {
        return new BackupContents(rooms, variants, games, gamesInPlay, movements, templates, from, to,
                rooms + variants + games + movements == 0);
    }

    /** One restore: what it has found wrong in the file, and what it writes. */
    private final class Restore {

        private final BackupData data;
        private final Locale locale;
        private final List<BackupError> errors = new ArrayList<>();
        private int errorCount;

        /** The built-in variants of this installation, by game type and code. */
        private final Set<String> builtIn = new HashSet<>();
        private final Map<String, Boolean> knownCurrencies = new HashMap<>();
        /** The ids of the file that name a room, and the variant each id of the file names. */
        private final Set<Long> roomIds = new HashSet<>();
        private final Map<Long, VariantData> variantsById = new HashMap<>();

        Restore(BackupData data, Locale locale) {
            this.data = data;
            this.locale = locale;
            for (Variant variant : variants.findAll()) {
                if (variant.isBuiltIn()) {
                    builtIn.add(builtInKey(variant.getGameType(), variant.getCode()));
                }
            }
            entityManager.clear();
        }

        // ---- the file as a whole, before anything is touched ----

        void check() {
            checkRooms();
            checkVariants();
            checkGames();
            checkMovements();
            checkTemplates();
            checkCurrencies();
        }

        private void checkRooms() {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < data.rooms().size(); i++) {
                String path = "rooms[" + i + "]";
                RoomData room = data.rooms().get(i);
                if (room == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                checkId(path, room.id(), roomIds);
                violations(path, new RoomRequest(room.name(), room.currencyCode(), room.active()));
                if (room.currencyCode() != null && !exists(room.currencyCode())) {
                    apiError(path + ".currencyCode", ErrorCode.UNKNOWN_CURRENCY, room.currencyCode());
                }
                if (room.name() != null && !names.add(key(room.name()))) {
                    apiError(path + ".name", ErrorCode.ROOM_NAME_TAKEN, room.name().strip());
                }
                if (room.logo() != null && room.logo().content() == null) {
                    problem(path + ".logo.content", BackupProblem.REQUIRED);
                }
            }
        }

        private void checkVariants() {
            Set<Long> ids = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (int i = 0; i < data.variants().size(); i++) {
                String path = "variants[" + i + "]";
                VariantData variant = data.variants().get(i);
                if (variant == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                if (checkId(path, variant.id(), ids)) {
                    variantsById.put(variant.id(), variant);
                }
                if (variant.gameType() == null) {
                    problem(path + ".gameType", BackupProblem.REQUIRED);
                }
                if ((variant.code() == null) == (variant.name() == null)) {
                    problem(path, BackupProblem.VARIANT_CODE_OR_NAME);
                } else if (variant.name() != null && variant.gameType() != null) {
                    violations(path, new VariantCreateRequest(variant.gameType(), variant.name()));
                    if (!names.add(variant.gameType() + "\n" + key(variant.name()))) {
                        apiError(path + ".name", ErrorCode.VARIANT_NAME_TAKEN, variant.name().strip());
                    }
                }
            }
        }

        private void checkGames() {
            for (int i = 0; i < data.games().size(); i++) {
                String path = "games[" + i + "]";
                GameData game = data.games().get(i);
                if (game == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                // The constraints of a game recorded by hand: amounts, cash games, games in play...
                violations(path, new GameRequest(game.playedOn(), game.playedAt(), game.roomId(), game.gameType(),
                        game.modality(), game.variantId(), game.status(), game.name(), game.buyIn(), game.entries(),
                        game.prize(), game.bounty(), game.ticketPrizeValue(), game.ticketDescription(),
                        game.paidWithTicket(), game.notes(), game.tags()));
                if (game.status() == null) {
                    problem(path + ".status", BackupProblem.REQUIRED);
                }
                if (game.roomId() != null && !roomIds.contains(game.roomId())) {
                    apiError(path + ".roomId", ErrorCode.UNKNOWN_ROOM, String.valueOf(game.roomId()));
                }
                if (game.variantId() != null) {
                    checkVariantOf(path + ".variantId", game.variantId(), game.gameType());
                }
            }
        }

        private void checkVariantOf(String path, Long variantId, @Nullable GameType gameType) {
            VariantData variant = variantsById.get(variantId);
            if (variant == null) {
                apiError(path, ErrorCode.UNKNOWN_VARIANT, String.valueOf(variantId));
            } else if (variant.gameType() != null && gameType != null && variant.gameType() != gameType) {
                apiError(path, ErrorCode.VARIANT_GAME_TYPE_MISMATCH);
            } else if (variant.gameType() != null && variant.code() != null && variant.name() == null
                    && !builtIn.contains(builtInKey(variant.gameType(), variant.code()))) {
                problem(path, BackupProblem.UNKNOWN_BUILT_IN_VARIANT, variant.code());
            }
        }

        private void checkMovements() {
            for (int i = 0; i < data.movements().size(); i++) {
                String path = "movements[" + i + "]";
                MovementData movement = data.movements().get(i);
                if (movement == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                violations(path, new MovementRequest(movement.occurredOn(), movement.type(), movement.roomId(),
                        movement.currencyCode(), movement.amount(), movement.notes()));
                if (movement.roomId() != null && !roomIds.contains(movement.roomId())) {
                    apiError(path + ".roomId", ErrorCode.UNKNOWN_ROOM, String.valueOf(movement.roomId()));
                }
                String currency = movement.currencyCode();
                if (movement.roomId() == null && currency != null && !currency.isBlank() && !exists(currency.strip())) {
                    apiError(path + ".currencyCode", ErrorCode.UNKNOWN_CURRENCY, currency.strip());
                }
            }
        }

        private void checkTemplates() {
            for (int i = 0; i < data.templates().size(); i++) {
                String path = "templates[" + i + "]";
                TemplateData template = data.templates().get(i);
                if (template == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                // The constraints of a template created by hand.
                violations(path, new GameTemplateRequest(template.label(), template.roomId(), template.gameType(),
                        template.modality(), template.variantId(), template.name(), template.buyIn()));
                if (template.roomId() != null && !roomIds.contains(template.roomId())) {
                    apiError(path + ".roomId", ErrorCode.UNKNOWN_ROOM, String.valueOf(template.roomId()));
                }
                if (template.variantId() != null) {
                    checkVariantOf(path + ".variantId", template.variantId(), template.gameType());
                }
            }
        }

        /** The base currency, which must exist, and the rates typed by hand: once per currency and day. */
        private void checkCurrencies() {
            String base = data.baseCurrencyCode();
            if (base != null && !exists(base.strip())) {
                apiError("baseCurrencyCode", ErrorCode.UNKNOWN_CURRENCY, base.strip());
            }
            Set<String> days = new HashSet<>();
            for (int i = 0; i < data.exchangeRates().size(); i++) {
                String path = "exchangeRates[" + i + "]";
                RateData rate = data.exchangeRates().get(i);
                if (rate == null) {
                    problem(path, BackupProblem.REQUIRED);
                    continue;
                }
                violations(path, new ManualRateRequest(rate.rate()));
                if (rate.date() == null) {
                    problem(path + ".date", BackupProblem.REQUIRED);
                }
                String currency = rate.currencyCode() == null ? null : rate.currencyCode().strip();
                if (currency == null || currency.isEmpty()) {
                    problem(path + ".currencyCode", BackupProblem.REQUIRED);
                } else if (currency.equals("EUR")) {
                    apiError(path + ".currencyCode", ErrorCode.EXCHANGE_RATE_OF_EUR);
                } else if (!exists(currency)) {
                    apiError(path + ".currencyCode", ErrorCode.UNKNOWN_CURRENCY, currency);
                } else if (rate.date() != null && !days.add(currency + "/" + rate.date())) {
                    problem(path, BackupProblem.DUPLICATE_EXCHANGE_RATE, currency, rate.date().toString());
                }
            }
        }

        /** Whether the id is there and is the first with its value. */
        private boolean checkId(String path, @Nullable Long id, Set<Long> ids) {
            if (id == null) {
                problem(path + ".id", BackupProblem.REQUIRED);
                return false;
            }
            if (!ids.add(id)) {
                problem(path + ".id", BackupProblem.DUPLICATE_ID, String.valueOf(id));
                return false;
            }
            return true;
        }

        private boolean exists(String currency) {
            return knownCurrencies.computeIfAbsent(currency, currencies::existsById);
        }

        // ---- the installation, emptied and written again ----

        void write() {
            entityManager.clear();
            // Logos go with their rooms, and the tags of the games with them.
            for (String table : List.of("game_template", "game", "tag", "bankroll_movement", "room")) {
                entityManager.createNativeQuery("delete from " + table).executeUpdate();
            }
            entityManager.createNativeQuery("delete from variant where code is null").executeUpdate();

            Map<Long, Long> newRoomIds = writeRooms();
            if (errorCount > 0) {
                return;
            }
            Map<Long, Long> newVariantIds = writeVariants();
            writeMovements(newRoomIds);
            writeTemplates(newRoomIds, newVariantIds);
            entityManager.flush();
            entityManager.clear();
            writeGames(newRoomIds, newVariantIds);
            writeCurrencies();
        }

        /** The base currency and the rates typed by hand; the downloaded rates stay. */
        private void writeCurrencies() {
            jdbc.update("update currency_setting set base_currency_code = ?, updated_at = now()",
                    data.baseCurrencyCode() == null ? null : data.baseCurrencyCode().strip());
            jdbc.update("delete from exchange_rate where source = 'MANUAL'");
            jdbc.batchUpdate("insert into exchange_rate (currency_code, rate_date, source, rate) values (?, ?, 'MANUAL', ?)",
                    data.exchangeRates(), BATCH, (statement, rate) -> {
                        statement.setString(1, rate.currencyCode().strip());
                        statement.setObject(2, rate.date());
                        statement.setBigDecimal(3, rate.rate());
                    });
        }

        /** The rooms and their logos; returns the id each id of the file has now. */
        private Map<Long, Long> writeRooms() {
            Map<Long, Room> written = new HashMap<>();
            for (RoomData source : data.rooms()) {
                Room room = new Room(source.name().strip(), source.currencyCode());
                room.setActive(!Boolean.FALSE.equals(source.active()));
                written.put(source.id(), rooms.save(room));
            }
            entityManager.flush();
            Map<Long, Long> ids = new HashMap<>();
            written.forEach((id, room) -> ids.put(id, room.getId()));

            for (int i = 0; i < data.rooms().size(); i++) {
                RoomData room = data.rooms().get(i);
                if (room.logo() != null) {
                    try {
                        // The rules of a logo uploaded by hand: its size and its format, read from the content.
                        logoService.replace(ids.get(room.id()), room.logo().content());
                    } catch (ApiException ex) {
                        apiError("rooms[" + i + "].logo.content", ex.getCode(), ex.getArgs());
                    }
                }
            }
            return ids;
        }

        /**
         * The variants of the user, and whether each built-in one is active: as the file says, and
         * active when the file does not name it. Returns the id each id of the file has now.
         */
        private Map<Long, Long> writeVariants() {
            Map<String, Variant> builtIns = new HashMap<>();
            for (Variant variant : variants.findAll()) {
                variant.setActive(true);
                builtIns.put(builtInKey(variant.getGameType(), variant.getCode()), variant);
            }
            Map<Long, Variant> written = new HashMap<>();
            for (VariantData source : data.variants()) {
                boolean active = !Boolean.FALSE.equals(source.active());
                if (source.isBuiltIn()) {
                    // One this installation does not have, which no game uses: there is nothing to restore.
                    Variant variant = builtIns.get(builtInKey(source.gameType(), source.code()));
                    if (variant != null) {
                        variant.setActive(active);
                        written.put(source.id(), variant);
                    }
                } else {
                    Variant variant = new Variant(source.gameType(), source.name().strip());
                    variant.setActive(active);
                    written.put(source.id(), variants.save(variant));
                }
            }
            entityManager.flush();
            Map<Long, Long> ids = new HashMap<>();
            written.forEach((id, variant) -> ids.put(id, variant.getId()));
            entityManager.clear();
            return ids;
        }

        /**
         * The games, in the order of the file, and their tags. They are the bulk of a backup, so they
         * are inserted in batches with plain SQL: one by one through the entity, tens of thousands take
         * minutes. The database computes the net and checks every rule, as for any other game.
         */
        private void writeGames(Map<Long, Long> newRoomIds, Map<Long, Long> newVariantIds) {
            List<GameData> games = data.games();
            Map<String, Long> tagIds = new HashMap<>();
            List<long[]> gameTags = new ArrayList<>();
            for (int start = 0; start < games.size(); start += BATCH) {
                List<GameData> batch = games.subList(start, Math.min(start + BATCH, games.size()));
                List<Long> ids = insertGames(batch, newRoomIds, newVariantIds);
                for (int i = 0; i < batch.size(); i++) {
                    for (String tag : namesOf(batch.get(i).tags())) {
                        gameTags.add(new long[] {ids.get(i), tagIds.computeIfAbsent(key(tag), key -> insertTag(tag))});
                    }
                }
            }
            jdbc.batchUpdate("insert into game_tag (game_id, tag_id) values (?, ?) on conflict do nothing",
                    gameTags, BATCH, (statement, pair) -> {
                        statement.setLong(1, pair[0]);
                        statement.setLong(2, pair[1]);
                    });
        }

        /** Inserts the games in one batch; returns their ids, in the same order. */
        private List<Long> insertGames(List<GameData> batch, Map<Long, Long> newRoomIds,
                Map<Long, Long> newVariantIds) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.batchUpdate(connection -> connection.prepareStatement("""
                    insert into game (played_on, played_at, room_id, game_type_code, modality_code, variant_id,
                                      status, name, buy_in, entries, prize, bounty, ticket_prize_value,
                                      ticket_description, paid_with_ticket, notes)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[] {"id"}), new BatchPreparedStatementSetter() {

                        @Override
                        public void setValues(PreparedStatement statement, int index) throws SQLException {
                            GameData source = batch.get(index);
                            statement.setObject(1, source.playedOn());
                            // A local time, as it is: not through java.sql.Time, which has a time zone.
                            setNullable(statement, 2, source.playedAt(), Types.TIME);
                            statement.setLong(3, newRoomIds.get(source.roomId()));
                            statement.setString(4, source.gameType().name());
                            statement.setString(5,
                                    (source.modality() == null ? Modality.NLHE : source.modality()).name());
                            setNullable(statement, 6, source.variantId() == null ? null
                                    : newVariantIds.get(source.variantId()), Types.BIGINT);
                            statement.setString(7, source.status().name());
                            setNullable(statement, 8, blankToNull(source.name()), Types.VARCHAR);
                            statement.setBigDecimal(9, source.buyIn());
                            statement.setInt(10, source.entries() == null ? 1 : source.entries());
                            statement.setBigDecimal(11, orZero(source.prize()));
                            statement.setBigDecimal(12, orZero(source.bounty()));
                            statement.setBigDecimal(13, orZero(source.ticketPrizeValue()));
                            setNullable(statement, 14, blankToNull(source.ticketDescription()), Types.VARCHAR);
                            statement.setBoolean(15, Boolean.TRUE.equals(source.paidWithTicket()));
                            setNullable(statement, 16, blankToNull(source.notes()), Types.VARCHAR);
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    }, keys);
            return keys.getKeyList().stream().map(key -> ((Number) key.get("id")).longValue()).toList();
        }

        /**
         * Creates a tag of the file. The restore deleted every tag, so it is new unless another name
         * of the file is the same one for the database, ignoring case.
         */
        private long insertTag(String name) {
            jdbc.update("insert into tag (name) values (?) on conflict ((lower(name))) do nothing", name);
            return Objects.requireNonNull(
                    jdbc.queryForObject("select id from tag where lower(name) = lower(?)", Long.class, name));
        }

        private void writeMovements(Map<Long, Long> newRoomIds) {
            for (MovementData source : data.movements()) {
                BankrollMovement movement = new BankrollMovement();
                movement.setOccurredOn(source.occurredOn());
                movement.setType(source.type());
                if (source.roomId() == null) {
                    movement.setOwner(null, source.currencyCode().strip());
                } else {
                    movement.setOwner(entityManager.getReference(Room.class, newRoomIds.get(source.roomId())), null);
                }
                movement.setAmount(source.amount());
                movement.setNotes(blankToNull(source.notes()));
                entityManager.persist(movement);
            }
        }

        /** As they are, also in an inactive room or with an inactive variant. */
        private void writeTemplates(Map<Long, Long> newRoomIds, Map<Long, Long> newVariantIds) {
            for (TemplateData source : data.templates()) {
                GameTemplate template = new GameTemplate();
                template.setLabel(blankToNull(source.label()));
                template.setRoom(entityManager.getReference(Room.class, newRoomIds.get(source.roomId())));
                template.setGameType(source.gameType());
                template.setModality(source.modality() == null ? Modality.NLHE : source.modality());
                Long variantId = source.variantId() == null ? null : newVariantIds.get(source.variantId());
                template.setVariant(variantId == null ? null : entityManager.getReference(Variant.class, variantId));
                template.setGameName(blankToNull(source.name()));
                template.setBuyIn(source.buyIn());
                entityManager.persist(template);
            }
        }

        // ---- errors ----

        private void violations(String path, Object request) {
            for (ConstraintViolation<Object> violation : validator.validate(request)) {
                // A value of a list is named by its position: tags[2], not tags[2].<list element>.
                String field = violation.getPropertyPath().toString().replace(".<list element>", "");
                String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
                // The constraints of the application have their message under their own name.
                String message = messages.getMessage(constraint, null, violation.getMessage(), locale);
                error(field.isEmpty() ? path : path + "." + field, constraint, message);
            }
        }

        private void problem(String path, BackupProblem problem, Object... args) {
            error(path, problem.name(), messages.getMessage(problem.messageKey(), args, problem.name(), locale));
        }

        private void apiError(String path, ErrorCode code, Object... args) {
            error(path, code.name(), messages.getMessage(code.messageKey(), args, code.name(), locale));
        }

        private void error(String path, String code, @Nullable String message) {
            errorCount++;
            if (errors.size() < MAX_ERRORS_LISTED) {
                errors.add(new BackupError(path, code, message == null ? code : message));
            }
        }
    }

    private static void setNullable(PreparedStatement statement, int index, @Nullable Object value, int type)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, type);
        } else {
            statement.setObject(index, value);
        }
    }

    /** The names of the tags of a game, stripped, once each ignoring case. */
    private static List<String> namesOf(@Nullable List<@Nullable String> tags) {
        if (tags == null) {
            return List.of();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                byKey.putIfAbsent(key(tag), tag.strip());
            }
        }
        return List.copyOf(byKey.values());
    }

    private static String builtInKey(@Nullable GameType gameType, @Nullable String code) {
        return gameType + "/" + code;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal orZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
