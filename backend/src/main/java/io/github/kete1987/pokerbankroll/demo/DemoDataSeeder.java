package io.github.kete1987.pokerbankroll.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.github.kete1987.pokerbankroll.bankroll.BankrollService;
import io.github.kete1987.pokerbankroll.bankroll.MovementRequest;
import io.github.kete1987.pokerbankroll.bankroll.MovementType;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameRequest;
import io.github.kete1987.pokerbankroll.game.GameService;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.room.RoomLogoService;
import io.github.kete1987.pokerbankroll.room.RoomRequest;
import io.github.kete1987.pokerbankroll.room.RoomService;
import io.github.kete1987.pokerbankroll.template.GameTemplateRequest;
import io.github.kete1987.pokerbankroll.template.GameTemplateService;
import io.github.kete1987.pokerbankroll.variant.VariantCreateRequest;
import io.github.kete1987.pokerbankroll.variant.VariantResponse;
import io.github.kete1987.pokerbankroll.variant.VariantService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile {@code demo}: fills an empty database with a year of made-up results, to look at the
 * application without recording anything. It does nothing when there is already a room, a game, a
 * bankroll movement or a user-defined variant. Everything goes through the services, so it follows the rules of the API,
 * but the exchange rates of the dollar, made up too, which only the download records.
 * The same day always gives the same data.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final int DAYS = 365;
    private static final long SEED = 20260119L;

    private final RoomService rooms;
    private final RoomLogoService logos;
    private final VariantService variants;
    private final GameService games;
    private final BankrollService bankroll;
    private final GameTemplateService templates;
    private final JdbcClient jdbc;

    private final Random random = new Random(SEED);
    private final Map<String, Long> variantIds = new HashMap<>();
    private long winamax;
    private long tripleEight;
    private long pokerStars;
    private long unibet;
    private int gameCount;
    /** A month of games tagged as a challenge. */
    private LocalDate challengeFrom;
    private LocalDate challengeTo;

    DemoDataSeeder(RoomService rooms, RoomLogoService logos, VariantService variants, GameService games,
            BankrollService bankroll, GameTemplateService templates, JdbcClient jdbc) {
        this.rooms = rooms;
        this.logos = logos;
        this.variants = variants;
        this.games = games;
        this.bankroll = bankroll;
        this.templates = templates;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(@Nullable ApplicationArguments args) {
        long rows = jdbc.sql("""
                select (select count(*) from room) + (select count(*) from game)
                     + (select count(*) from bankroll_movement)
                     + (select count(*) from variant where name is not null)
                """).query(Long.class).single();
        if (rows > 0) {
            log.info("Demo data not loaded: the database already has data");
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(DAYS);
        challengeFrom = start.plusDays(200);
        challengeTo = start.plusDays(230);

        createRoomsAndVariants();
        createTemplates();
        for (LocalDate day = start; day.isBefore(today); day = day.plusDays(1)) {
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            int sessions = pick(weekend ? new int[] {15, 25, 30, 20, 10} : new int[] {45, 30, 15, 10, 0});
            for (int i = 0; i < sessions; i++) {
                // Unibet was only played during the first months, then left.
                boolean unibetOpen = day.isBefore(start.plusDays(120));
                recordGame(day, unibetOpen);
            }
        }
        recordGamesInPlay(today);
        recordMovements(start, today);
        recordExchangeRates(start, today);
        rooms.update(unibet, new RoomRequest("Unibet", "EUR", false));

        log.info("Demo data loaded: 4 rooms, {} games from {} to {}, and dollar exchange rates", gameCount, start,
                today);
    }

    private void createRoomsAndVariants() {
        winamax = rooms.create(new RoomRequest("Winamax", "EUR", true)).id();
        tripleEight = rooms.create(new RoomRequest("888poker", "EUR", true)).id();
        pokerStars = rooms.create(new RoomRequest("PokerStars", "USD", true)).id();
        unibet = rooms.create(new RoomRequest("Unibet", "EUR", true)).id();
        // PokerStars stays without a logo, to see both cases.
        logos.replace(winamax, DemoLogo.png(DemoLogo.Shape.CIRCLE, 0x0B7285, 0xFFFFFF));
        logos.replace(tripleEight, DemoLogo.png(DemoLogo.Shape.DIAMOND, 0x5F3DC4, 0xFFD43B));
        logos.replace(unibet, DemoLogo.png(DemoLogo.Shape.RING, 0x495057, 0xFFA94D));

        for (VariantResponse variant : variants.list(null, null)) {
            if (variant.code() != null) {
                variantIds.put(variant.gameType() + "/" + variant.code(), variant.id());
            }
        }
        long hyperTurbo = variants.create(new VariantCreateRequest(GameType.SIT_AND_GO, "Hyper Turbo 6-max")).id();
        variantIds.put("SIT_AND_GO/HYPER_TURBO", hyperTurbo);
    }

    /** The games played most, to start them in a click; the one of Unibet stays when it is closed. */
    private void createTemplates() {
        templates.create(new GameTemplateRequest(null, winamax, GameType.SIT_AND_GO, Modality.NLHE,
                variantId(GameType.SIT_AND_GO, "EXPRESSO"), null, money("2")));
        templates.create(new GameTemplateRequest(null, winamax, GameType.TOURNAMENT, Modality.NLHE,
                variantId(GameType.TOURNAMENT, "KO"), "Kill The Fish", money("5")));
        templates.create(new GameTemplateRequest("NL10 Stars", pokerStars, GameType.CASH, Modality.NLHE, null,
                null, money("10")));
        templates.create(new GameTemplateRequest(null, unibet, GameType.TOURNAMENT, Modality.NLHE,
                variantId(GameType.TOURNAMENT, "REGULAR"), "Night Owl", money("2")));
    }

    private void recordGame(LocalDate day, boolean unibetOpen) {
        int kind = pick(new int[] {58, 34, 8});
        if (kind == 0) {
            recordTournament(day, unibetOpen);
        } else if (kind == 1) {
            recordSitAndGo(day);
        } else {
            recordCashGame(day);
        }
    }

    private void recordTournament(LocalDate day, boolean unibetOpen) {
        long room = switch (pick(new int[] {55, 20, 17, unibetOpen ? 8 : 0})) {
            case 0 -> winamax;
            case 1 -> tripleEight;
            case 2 -> pokerStars;
            default -> unibet;
        };
        if (room == winamax && chance(6)) {
            recordSatellite(day);
            return;
        }
        String variant = oneOf("REGULAR", "REGULAR", "REGULAR", "KO", "KO", "KO", "SPACE_KO", "MYSTERY_KO", null);
        BigDecimal buyIn = money(oneOf("1", "2", "2", "2", "5", "5", "10", "20"));
        int entries = 1 + pick(new int[] {80, 15, 5});
        String name = switch (variant == null ? "" : variant) {
            case "KO" -> oneOf("Kill The Fish", "Bounty Hunter", "Wanted", "Big KO");
            case "SPACE_KO" -> oneOf("Space KO Turbo", "Supernova", "Space KO Deepstack");
            case "MYSTERY_KO" -> oneOf("Mystery KO", "Mystery Sunday");
            default -> oneOf("Monster Stack", "Main Event", "High Five", "Deepstack", "Sunday Surprise", "Night Owl");
        };

        BigDecimal prize = BigDecimal.ZERO;
        if (chance(19)) {
            double multiplier = chance(6) ? 25 + random.nextDouble() * 60 : 1.6 + random.nextDouble() * 9;
            prize = times(buyIn, multiplier);
        }
        BigDecimal bounty = BigDecimal.ZERO;
        if (variant != null && variant.endsWith("KO") && chance(45)) {
            bounty = times(buyIn, 0.25 + random.nextDouble() * 2.2);
        }
        // The tournaments of PokerStars on Fridays are the ones played with friends.
        boolean withFriends = room == pokerStars && day.getDayOfWeek() == DayOfWeek.FRIDAY;
        List<String> tags = tags(day, withFriends ? "Friends" : null);
        create(new GameRequest(day, timeOrNull(), room, GameType.TOURNAMENT,
                chance(8) ? Modality.PLO : Modality.NLHE, variantId(GameType.TOURNAMENT, variant),
                GameStatus.FINISHED, name, buyIn, entries, prize, bounty, null, null, null, null, tags));
    }

    /** A satellite; when it is won, the ticket is played right away in the target tournament. */
    private void recordSatellite(LocalDate day) {
        boolean won = chance(30);
        create(new GameRequest(day, LocalTime.of(19, 0), winamax, GameType.TOURNAMENT, Modality.NLHE,
                variantId(GameType.TOURNAMENT, "REGULAR"), GameStatus.FINISHED, "Satellite Winamax Series",
                money("2"), 1, null, null, won ? money("20") : null, won ? "Winamax Series 20 €" : null,
                null, null, tags(day, "Satellite", "Winamax Series")));
        if (won) {
            BigDecimal prize = chance(25) ? times(money("20"), 2 + random.nextDouble() * 6) : BigDecimal.ZERO;
            create(new GameRequest(day, LocalTime.of(21, 0), winamax, GameType.TOURNAMENT, Modality.NLHE,
                    variantId(GameType.TOURNAMENT, "REGULAR"), GameStatus.FINISHED, "Winamax Series",
                    money("20"), 1, prize, null, null, null, true, "Played with the ticket won in the satellite",
                    tags(day, "Winamax Series")));
        }
    }

    private void recordSitAndGo(LocalDate day) {
        String variant = oneOf("EXPRESSO", "EXPRESSO", "EXPRESSO", "EXPRESSO", "EXPRESSO_NITRO", "EXPRESSO_NITRO",
                "DOUBLE_OR_NOTHING", "HEADS_UP", "HYPER_TURBO");
        long room = variant.startsWith("EXPRESSO") ? winamax : (chance(60) ? tripleEight : pokerStars);
        BigDecimal buyIn = money(oneOf("0.50", "1", "1", "2", "2", "5"));
        BigDecimal prize = BigDecimal.ZERO;
        String notes = null;
        switch (variant) {
            case "EXPRESSO", "EXPRESSO_NITRO" -> {
                int multiplier = new int[] {2, 2, 2, 2, 2, 2, 3, 3, 5, 10}[random.nextInt(10)];
                notes = "x" + multiplier;
                if (chance(34)) {
                    prize = times(buyIn, multiplier);
                }
            }
            case "DOUBLE_OR_NOTHING" -> prize = chance(54) ? times(buyIn, 1.86) : BigDecimal.ZERO;
            case "HEADS_UP" -> prize = chance(52) ? times(buyIn, 1.9) : BigDecimal.ZERO;
            default -> {
                int place = pick(new int[] {20, 18, 62});
                prize = place == 0 ? times(buyIn, 3.9) : place == 1 ? times(buyIn, 1.7) : BigDecimal.ZERO;
            }
        }
        create(new GameRequest(day, timeOrNull(), room, GameType.SIT_AND_GO, Modality.NLHE,
                variantId(GameType.SIT_AND_GO, variant), GameStatus.FINISHED, null, buyIn, 1, prize, null, null,
                null, null, notes, tags(day)));
    }

    private void recordCashGame(LocalDate day) {
        boolean plo = chance(25);
        BigDecimal buyIn = money(oneOf("2", "2", "5", "10"));
        double result = Math.max(0, 1.02 + random.nextGaussian() * 0.75);
        create(new GameRequest(day, timeOrNull(), chance(70) ? winamax : pokerStars, GameType.CASH,
                plo ? Modality.PLO : Modality.NLHE, null, GameStatus.FINISHED, null, buyIn, 1,
                times(buyIn, result), null, null, null, null,
                (plo ? "PLO" : "NL") + buyIn.intValue() + " 6-max", null));
    }

    private void recordGamesInPlay(LocalDate today) {
        create(new GameRequest(today, LocalTime.of(20, 30), winamax, GameType.TOURNAMENT, Modality.NLHE,
                variantId(GameType.TOURNAMENT, "KO"), null, "Kill The Fish", money("5"), 2, null, null, null, null,
                null, null, List.of("Friends")));
        create(new GameRequest(today, LocalTime.of(21, 15), tripleEight, GameType.TOURNAMENT, Modality.NLHE,
                variantId(GameType.TOURNAMENT, "REGULAR"), null, "Deepstack", money("2"), 1, null, null, null,
                null, null, null, null));
        create(new GameRequest(today, null, winamax, GameType.CASH, Modality.NLHE, null, null, null, money("5"), 1,
                null, null, null, null, null, "NL5 6-max", null));
    }

    private void recordMovements(LocalDate start, LocalDate today) {
        move(start, MovementType.DEPOSIT, null, "EUR", "200", "Initial bankroll");
        move(start, MovementType.DEPOSIT, pokerStars, null, "50", "Initial bankroll");
        move(start.plusDays(3), MovementType.DEPOSIT, unibet, null, "20", null);
        move(start.plusDays(125), MovementType.WITHDRAWAL, unibet, null, "150", "Closing the account");
        for (LocalDate day = start.plusMonths(1); day.isBefore(today); day = day.plusMonths(1)) {
            move(day, MovementType.BONUS, winamax, null,
                    money(String.valueOf(2 + random.nextInt(7))).add(money("0.50")).toPlainString(), "Rakeback");
        }
        move(start.plusDays(200), MovementType.WITHDRAWAL, tripleEight, null, "40", "Profits");
        move(start.plusDays(240), MovementType.BONUS, tripleEight, null, "8", "Welcome bonus");
        move(start.plusDays(300), MovementType.ADJUSTMENT, tripleEight, null, "-3.20", "Games not recorded");
    }

    /**
     * Made-up daily rates of the dollar, around 1.05 to 1.15 per euro, on working days as the ECB
     * publishes them, so the demo shows everything converted without internet. They are written
     * as downloaded rates: no service records those. Their own random numbers, so the rest of the
     * demo data stays the same.
     */
    private void recordExchangeRates(LocalDate start, LocalDate today) {
        Random rates = new Random(SEED + 1);
        double rate = 1.10;
        for (LocalDate day = start.minusDays(10); !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            rate = Math.clamp(rate + (rates.nextDouble() - 0.5) * 0.01, 1.05, 1.15);
            jdbc.sql("insert into exchange_rate (currency_code, rate_date, source, rate) values ('USD', :day, 'ECB', :rate)")
                    .param("day", day)
                    .param("rate", BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP))
                    .update();
        }
    }

    private void move(LocalDate day, MovementType type, @Nullable Long room, @Nullable String currency, String amount,
            @Nullable String notes) {
        bankroll.create(new MovementRequest(day, type, room, currency, new BigDecimal(amount), notes));
    }

    private void create(GameRequest game) {
        games.create(game);
        gameCount++;
    }

    /** The given tags, plus the one of the challenge for the games played during it. */
    private List<String> tags(LocalDate day, @Nullable String... tags) {
        List<String> all = new ArrayList<>();
        for (String tag : tags) {
            if (tag != null) {
                all.add(tag);
            }
        }
        if (!day.isBefore(challengeFrom) && !day.isAfter(challengeTo)) {
            all.add("Challenge");
        }
        return all;
    }

    private @Nullable Long variantId(GameType gameType, @Nullable String code) {
        return code == null ? null : variantIds.get(gameType + "/" + code);
    }

    /** Most games have a start time, in the evening. */
    private @Nullable LocalTime timeOrNull() {
        return chance(65) ? LocalTime.of(17 + random.nextInt(7), 15 * random.nextInt(4)) : null;
    }

    private boolean chance(int percent) {
        return random.nextInt(100) < percent;
    }

    /** Index chosen with the given weights. */
    private int pick(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    @SafeVarargs
    private final <T> T oneOf(T... options) {
        return options[random.nextInt(options.length)];
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal times(BigDecimal amount, double factor) {
        return amount.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }
}
