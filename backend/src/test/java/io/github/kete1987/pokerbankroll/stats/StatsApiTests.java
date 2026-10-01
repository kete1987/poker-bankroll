package io.github.kete1987.pokerbankroll.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.jayway.jsonpath.JsonPath;
import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StatsApiTests extends ApiIntegrationTest {

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    @Test
    void thereIsNothingToSummariseWithoutGames() {
        assertThat(summary("")).isEqualTo("{\"currencies\":[]}");
    }

    @Test
    void summarisesFinishedGamesOverallAndPerGameType() {
        recordSampleGames();

        String json = summary("?currency=EUR");

        assertThat(JsonPath.<Integer>read(json, "$.currencies.length()")).isEqualTo(1);
        assertThat(JsonPath.<String>read(json, "$.currencies[0].currencyCode")).isEqualTo("EUR");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].byGameType[*].gameType"))
                .hasToString("[\"TOURNAMENT\",\"SIT_AND_GO\",\"CASH\"]");

        String tournaments = "$.currencies[0].byGameType[0].figures";
        assertNumber(json, tournaments + ".games", "4");
        assertNumber(json, tournaments + ".entries", "5");
        assertNumber(json, tournaments + ".gamesWithPrize", "3");
        assertNumber(json, tournaments + ".withPrizeRate", "0.75");
        assertNumber(json, tournaments + ".gamesInTheMoney", "2");
        assertNumber(json, tournaments + ".inTheMoneyRate", "0.5");
        assertNumber(json, tournaments + ".winningGames", "1");
        assertNumber(json, tournaments + ".winningRate", "0.25");
        assertNumber(json, tournaments + ".averageBuyIn", "4.5");
        assertNumber(json, tournaments + ".invested", "13");
        assertNumber(json, tournaments + ".won", "13");
        assertNumber(json, tournaments + ".bounties", "3");
        assertNumber(json, tournaments + ".ticketsWon", "20");
        assertNumber(json, tournaments + ".net", "0");
        assertNumber(json, tournaments + ".roi", "0");

        String sitAndGo = "$.currencies[0].byGameType[1].figures";
        assertNumber(json, sitAndGo + ".games", "1");
        assertNumber(json, sitAndGo + ".net", "1");
        assertNumber(json, sitAndGo + ".roi", "1");

        String total = "$.currencies[0].total";
        assertNumber(json, total + ".games", "6");
        assertNumber(json, total + ".entries", "7");
        assertNumber(json, total + ".gamesWithPrize", "4");
        assertNumber(json, total + ".withPrizeRate", "0.8");
        assertNumber(json, total + ".gamesInTheMoney", "3");
        assertNumber(json, total + ".inTheMoneyRate", "0.6");
        assertNumber(json, total + ".winningGames", "3");
        assertNumber(json, total + ".winningRate", "0.5");
        // (2 + 5 + 10 + 1 + 1) / 5: the mean over the games, not over the game types; cash games apart.
        assertNumber(json, total + ".averageBuyIn", "3.8");
        assertNumber(json, total + ".invested", "16");
        assertNumber(json, total + ".won", "18.5");
        assertNumber(json, total + ".bounties", "3");
        assertNumber(json, total + ".ticketsWon", "20");
        assertNumber(json, total + ".net", "2.5");
        assertNumber(json, total + ".roi", "0.1563");
    }

    @Test
    void cashGamesHaveNoPrizeFiguresOrAverageBuyIn() {
        recordSampleGames();

        String json = summary("?gameType=CASH");

        String cash = "$.currencies[0].total";
        assertNumber(json, cash + ".games", "1");
        assertThat(JsonPath.<Object>read(json, cash + ".gamesWithPrize")).isNull();
        assertThat(JsonPath.<Object>read(json, cash + ".withPrizeRate")).isNull();
        assertThat(JsonPath.<Object>read(json, cash + ".gamesInTheMoney")).isNull();
        assertThat(JsonPath.<Object>read(json, cash + ".inTheMoneyRate")).isNull();
        assertThat(JsonPath.<Object>read(json, cash + ".averageBuyIn")).isNull();
        assertNumber(json, cash + ".winningGames", "1");
        assertNumber(json, cash + ".winningRate", "1");
        assertNumber(json, cash + ".invested", "2");
        assertNumber(json, cash + ".won", "3.5");
        assertNumber(json, cash + ".net", "1.5");
        assertNumber(json, cash + ".roi", "0.75");
    }

    @Test
    void gamesInPlayAreReportedApart() {
        recordSampleGames();

        String json = summary("?currency=EUR");

        assertNumber(json, "$.currencies[0].inPlay.games", "1");
        assertNumber(json, "$.currencies[0].inPlay.invested", "6");
        // Not in the figures: see the totals of the previous test.
        assertNumber(json, "$.currencies[0].total.games", "6");
        assertNumber(json, "$.currencies[0].total.invested", "16");
    }

    @Test
    void aCurrencyWithOnlyGamesInPlayHasEmptyFigures() {
        game(pokerStars, "TOURNAMENT", "2026-01-19").buyIn("5").inPlay().insert();

        String json = summary("");

        assertThat(JsonPath.<String>read(json, "$.currencies[0].currencyCode")).isEqualTo("USD");
        assertThat(JsonPath.<Integer>read(json, "$.currencies[0].byGameType.length()")).isZero();
        assertNumber(json, "$.currencies[0].total.games", "0");
        assertNumber(json, "$.currencies[0].total.invested", "0");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].total.roi")).isNull();
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].total.winningRate")).isNull();
        assertNumber(json, "$.currencies[0].inPlay.games", "1");
        assertNumber(json, "$.currencies[0].inPlay.invested", "5");
    }

    @Test
    void currenciesAreNeverAddedUp() {
        recordSampleGames();

        String json = summary("");

        assertThat(JsonPath.<Object>read(json, "$.currencies[*].currencyCode")).hasToString("[\"EUR\",\"USD\"]");
        assertNumber(json, "$.currencies[0].total.games", "6");
        assertNumber(json, "$.currencies[1].total.games", "1");
        assertNumber(json, "$.currencies[1].total.net", "40");
    }

    @Test
    void roiIsUnknownWhenNothingWasInvested() {
        game(winamax, "TOURNAMENT", "2026-01-19").buyIn("10").paidWithTicket().prize("25").insert();

        String json = summary("");

        assertNumber(json, "$.currencies[0].total.invested", "0");
        assertNumber(json, "$.currencies[0].total.net", "25");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].total.roi")).isNull();
    }

    @Test
    void takesTheFiltersOfTheGamesList() {
        recordSampleGames();
        long ko = builtInVariantId("TOURNAMENT", "KO");

        assertNumber(summary("?from=2026-01-20&to=2026-01-21"), "$.currencies[0].total.games", "3");
        assertNumber(summary("?gameType=SIT_AND_GO"), "$.currencies[0].total.games", "1");
        assertNumber(summary("?modality=PLO"), "$.currencies[0].total.games", "1");
        assertNumber(summary("?roomId=" + pokerStars), "$.currencies[0].total.games", "1");
        assertNumber(summary("?variantId=" + ko), "$.currencies[0].total.games", "1");
        assertNumber(summary("?q=satellite"), "$.currencies[0].total.games", "1");
        // Several values of a filter: the games of any of them.
        assertNumber(summary("?currency=EUR&gameType=SIT_AND_GO,CASH"), "$.currencies[0].total.games", "2");
        assertNumber(summary("?gameType=TOURNAMENT&roomId=" + winamax + "&roomId=" + pokerStars),
                "$.currencies[1].total.games", "1");
        assertThat(JsonPath.<Integer>read(groups("?groupBy=GAME_TYPE&currency=EUR&gameType=SIT_AND_GO,CASH"),
                "$.currencies[0].groups.length()")).isEqualTo(2);
        assertThat(summary("?from=2027-01-01")).isEqualTo("{\"currencies\":[]}");
    }

    @Test
    void rejectsAnInvalidFilter() {
        assertThat(mvc.get().uri("/stats/summary?gameType=BINGO")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- groups ----

    @Test
    void groupsByDayWithTheCumulativeNet() {
        recordSampleGames();

        String json = groups("?groupBy=DAY&currency=EUR");

        assertThat(JsonPath.<String>read(json, "$.groupBy")).isEqualTo("DAY");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].groups[*].key.period"))
                .hasToString("[\"2026-01-19\",\"2026-01-20\",\"2026-01-21\",\"2026-01-22\"]");
        String monday = "$.currencies[0].groups[0]";
        assertNumber(json, monday + ".figures.games", "2");
        assertNumber(json, monday + ".figures.entries", "3");
        assertNumber(json, monday + ".figures.invested", "12");
        assertNumber(json, monday + ".figures.won", "13");
        assertNumber(json, monday + ".figures.net", "1");
        assertNumber(json, monday + ".figures.roi", "0.0833");
        assertNumber(json, monday + ".cumulativeNet", "1");
        assertNumber(json, "$.currencies[0].groups[1].figures.net", "-1");
        assertNumber(json, "$.currencies[0].groups[1].cumulativeNet", "0");
        assertNumber(json, "$.currencies[0].groups[2].cumulativeNet", "1");
        // The game in play of the 22nd is not counted.
        assertNumber(json, "$.currencies[0].groups[3].figures.games", "1");
        assertNumber(json, "$.currencies[0].groups[3].cumulativeNet", "2.5");
    }

    @Test
    void theCumulativeNetStartsFromZeroAtTheBeginningOfTheFilteredRange() {
        recordSampleGames();

        String json = groups("?groupBy=DAY&currency=EUR&from=2026-01-21");

        assertThat(JsonPath.<Object>read(json, "$.currencies[0].groups[*].key.period"))
                .hasToString("[\"2026-01-21\",\"2026-01-22\"]");
        assertNumber(json, "$.currencies[0].groups[0].cumulativeNet", "1");
        assertNumber(json, "$.currencies[0].groups[1].cumulativeNet", "2.5");
    }

    @Test
    void groupsByWeekMonthAndYear() {
        recordSampleGames();
        game(winamax, "TOURNAMENT", "2025-12-31").buyIn("4").insert();
        game(winamax, "TOURNAMENT", "2026-01-18").buyIn("1").prize("3").insert();

        String weeks = groups("?groupBy=WEEK&currency=EUR");
        // Weeks start on Monday: Sunday the 18th belongs to the week of the 12th.
        assertThat(JsonPath.<Object>read(weeks, "$.currencies[0].groups[*].key.period"))
                .hasToString("[\"2025-12-29\",\"2026-01-12\",\"2026-01-19\"]");
        assertNumber(weeks, "$.currencies[0].groups[2].figures.games", "6");
        assertNumber(weeks, "$.currencies[0].groups[2].cumulativeNet", "0.5");

        String months = groups("?groupBy=MONTH&currency=EUR");
        assertThat(JsonPath.<Object>read(months, "$.currencies[0].groups[*].key.period"))
                .hasToString("[\"2025-12\",\"2026-01\"]");
        assertNumber(months, "$.currencies[0].groups[1].figures.games", "7");
        assertNumber(months, "$.currencies[0].groups[1].figures.net", "4.5");
        // Figures are computed on the whole group, not added up from its days.
        assertNumber(months, "$.currencies[0].groups[1].figures.averageBuyIn", "3.33");
        assertNumber(months, "$.currencies[0].groups[1].cumulativeNet", "0.5");

        String years = groups("?groupBy=YEAR&currency=EUR");
        assertThat(JsonPath.<Object>read(years, "$.currencies[0].groups[*].key.period"))
                .hasToString("[\"2025\",\"2026\"]");
        assertNumber(years, "$.currencies[0].groups[0].figures.net", "-4");
        assertNumber(years, "$.currencies[0].groups[1].cumulativeNet", "0.5");
    }

    @Test
    void groupsByGameTypeLikeTheSummary() {
        recordSampleGames();

        String json = groups("?groupBy=GAME_TYPE&currency=EUR");

        // Most played first; with the same games, the best net first.
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].groups[*].key.gameType"))
                .hasToString("[\"TOURNAMENT\",\"CASH\",\"SIT_AND_GO\"]");
        assertNumber(json, "$.currencies[0].groups[0].figures.games", "4");
        assertNumber(json, "$.currencies[0].groups[0].figures.withPrizeRate", "0.75");
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].groups[0].cumulativeNet")).isNull();
        assertThat(JsonPath.<Object>read(json, "$.currencies[0].groups[1].figures.averageBuyIn")).isNull();
    }

    @Test
    void groupsByVariantWithinItsGameType() {
        recordSampleGames();
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long turbo = insertCustomVariant("SIT_AND_GO", "Turbo");
        game(winamax, "SIT_AND_GO", "2026-01-23").variant(turbo).buyIn("1").insert();
        game(winamax, "SIT_AND_GO", "2026-01-23").variant(turbo).buyIn("1").prize("2").insert();

        String json = groups("?groupBy=VARIANT&currency=EUR");

        String groups = "$.currencies[0].groups";
        assertThat(JsonPath.<Integer>read(json, groups + ".length()")).isEqualTo(5);
        // Tournaments without a variant are one group, Sit&Go without a variant another.
        assertThat(JsonPath.<String>read(json, groups + "[0].key.gameType")).isEqualTo("TOURNAMENT");
        assertThat(JsonPath.<Object>read(json, groups + "[0].key.variant")).isNull();
        assertNumber(json, groups + "[0].figures.games", "3");
        assertThat(JsonPath.<String>read(json, groups + "[1].key.gameType")).isEqualTo("SIT_AND_GO");
        assertThat(JsonPath.<Integer>read(json, groups + "[1].key.variant.id")).isEqualTo((int) turbo);
        assertThat(JsonPath.<String>read(json, groups + "[1].key.variant.name")).isEqualTo("Turbo");
        assertThat(JsonPath.<Object>read(json, groups + "[1].key.variant.code")).isNull();
        assertNumber(json, groups + "[1].figures.games", "2");
        assertThat(JsonPath.<Object>read(json, groups + "[?(@.key.variant.code == 'KO')].key.variant.id"))
                .hasToString("[" + ko + "]");
        assertThat(JsonPath.<Object>read(json, groups + "[?(@.key.variant.code == 'KO')].key.gameType"))
                .hasToString("[\"TOURNAMENT\"]");
        assertThat(JsonPath.<Object>read(json,
                groups + "[?(@.key.gameType == 'SIT_AND_GO' && @.key.variant == null)].figures.games"))
                .hasToString("[1]");
    }

    @Test
    void groupsByRoomAndByModality() {
        recordSampleGames();
        long unibet = insertRoom("Unibet", "EUR");
        game(unibet, "TOURNAMENT", "2026-01-23").buyIn("1").insert();

        String rooms = groups("?groupBy=ROOM");
        assertThat(JsonPath.<Object>read(rooms, "$.currencies[0].groups[*].key.room.name"))
                .hasToString("[\"Winamax\",\"Unibet\"]");
        assertThat(JsonPath.<Integer>read(rooms, "$.currencies[0].groups[1].key.room.id")).isEqualTo((int) unibet);
        assertNumber(rooms, "$.currencies[0].groups[0].figures.games", "6");
        assertThat(JsonPath.<Object>read(rooms, "$.currencies[1].groups[*].key.room.name"))
                .hasToString("[\"PokerStars\"]");

        String modalities = groups("?groupBy=MODALITY&currency=EUR");
        assertThat(JsonPath.<Object>read(modalities, "$.currencies[0].groups[*].key.modality"))
                .hasToString("[\"NLHE\",\"PLO\"]");
        assertNumber(modalities, "$.currencies[0].groups[1].figures.games", "1");
    }

    @Test
    void groupsByBuyInFromLowestToHighestWithoutCashGames() {
        recordSampleGames();

        String json = groups("?groupBy=BUY_IN&currency=EUR");

        assertThat(JsonPath.<Integer>read(json, "$.currencies[0].groups.length()")).isEqualTo(4);
        assertNumber(json, "$.currencies[0].groups[0].key.buyIn", "1");
        // The satellite and the Sit&Go; the cash game brought 2 to the table and is not a buy-in of 2.
        assertNumber(json, "$.currencies[0].groups[0].figures.games", "2");
        assertNumber(json, "$.currencies[0].groups[1].key.buyIn", "2");
        assertNumber(json, "$.currencies[0].groups[1].figures.games", "1");
        assertNumber(json, "$.currencies[0].groups[2].key.buyIn", "5");
        assertNumber(json, "$.currencies[0].groups[3].key.buyIn", "10");
        assertThat(groups("?groupBy=BUY_IN&gameType=CASH")).isEqualTo("{\"groupBy\":\"BUY_IN\",\"currencies\":[]}");
    }

    @Test
    void groupsTakeTheFiltersOfTheGamesList() {
        recordSampleGames();

        String json = groups("?groupBy=MONTH&gameType=TOURNAMENT&roomId=" + winamax);

        assertThat(JsonPath.<Integer>read(json, "$.currencies.length()")).isEqualTo(1);
        assertNumber(json, "$.currencies[0].groups[0].figures.games", "4");
        assertThat(groups("?groupBy=DAY&from=2027-01-01")).isEqualTo("{\"groupBy\":\"DAY\",\"currencies\":[]}");
    }

    @Test
    void groupByIsRequiredAndMustBeKnown() {
        assertThat(mvc.get().uri("/stats/groups")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/stats/groups?groupBy=DECADE")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    /**
     * EUR: four tournaments, a Sit&Go and a cash game finished, plus a tournament in play.
     * USD: one tournament finished.
     */
    private void recordSampleGames() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        game(winamax, "TOURNAMENT", "2026-01-19").buyIn("2").prize("10").insert();
        game(winamax, "TOURNAMENT", "2026-01-19").variant(ko).buyIn("5").entries(2).bounty("3").insert();
        game(winamax, "TOURNAMENT", "2026-01-20").buyIn("10").paidWithTicket().insert();
        game(winamax, "TOURNAMENT", "2026-01-20").name("Satellite").buyIn("1").ticket("20").insert();
        game(winamax, "SIT_AND_GO", "2026-01-21").modality("PLO").buyIn("1").prize("2").insert();
        game(winamax, "CASH", "2026-01-22").buyIn("2").prize("3.50").insert();
        game(winamax, "TOURNAMENT", "2026-01-22").buyIn("3").entries(2).inPlay().insert();
        game(pokerStars, "TOURNAMENT", "2026-01-22").buyIn("10").prize("50").insert();
    }

    private String summary(String query) {
        return get("/stats/summary" + query);
    }

    private String groups(String query) {
        return get("/stats/groups" + query);
    }

    private String get(String uri) {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compares by value, whatever the scale the number was written with. */
    private static void assertNumber(String json, String path, String expected) {
        Object value = JsonPath.read(json, path);
        assertThat(value).as(path).isNotNull();
        assertThat(new BigDecimal(value.toString())).as(path).isEqualByComparingTo(expected);
    }

    private GameRow game(long roomId, String gameType, String playedOn) {
        return new GameRow(roomId, gameType, playedOn);
    }

    /** A finished game with nothing won, unless said otherwise. */
    private final class GameRow {
        private final long roomId;
        private final String gameType;
        private final String playedOn;
        private Long variantId;
        private String modality = "NLHE";
        private String status = "FINISHED";
        private String name;
        private String buyIn = "1";
        private int entries = 1;
        private String prize = "0";
        private String bounty = "0";
        private String ticket = "0";
        private boolean paidWithTicket;

        GameRow(long roomId, String gameType, String playedOn) {
            this.roomId = roomId;
            this.gameType = gameType;
            this.playedOn = playedOn;
        }

        GameRow variant(long id) {
            this.variantId = id;
            return this;
        }

        GameRow modality(String code) {
            this.modality = code;
            return this;
        }

        GameRow inPlay() {
            this.status = "IN_PLAY";
            return this;
        }

        GameRow name(String value) {
            this.name = value;
            return this;
        }

        GameRow buyIn(String amount) {
            this.buyIn = amount;
            return this;
        }

        GameRow entries(int count) {
            this.entries = count;
            return this;
        }

        GameRow prize(String amount) {
            this.prize = amount;
            return this;
        }

        GameRow bounty(String amount) {
            this.bounty = amount;
            return this;
        }

        GameRow ticket(String value) {
            this.ticket = value;
            return this;
        }

        GameRow paidWithTicket() {
            this.paidWithTicket = true;
            return this;
        }

        void insert() {
            jdbc.update("""
                    insert into game (played_on, room_id, game_type_code, variant_id, modality_code, status, name,
                                      buy_in, entries, prize, bounty, ticket_prize_value, paid_with_ticket)
                    values (?::date, ?, ?, ?, ?, ?, ?, ?::numeric, ?, ?::numeric, ?::numeric, ?::numeric, ?)
                    """, playedOn, roomId, gameType, variantId, modality, status, name, buyIn, entries, prize,
                    bounty, ticket, paidWithTicket);
        }
    }
}
