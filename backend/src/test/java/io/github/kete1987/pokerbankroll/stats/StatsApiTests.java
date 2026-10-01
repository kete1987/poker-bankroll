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
        assertThat(summary("?from=2027-01-01")).isEqualTo("{\"currencies\":[]}");
    }

    @Test
    void rejectsAnInvalidFilter() {
        assertThat(mvc.get().uri("/stats/summary?gameType=BINGO")).hasStatus(HttpStatus.BAD_REQUEST);
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
        var result = mvc.get().uri("/stats/summary" + query).exchange();
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
