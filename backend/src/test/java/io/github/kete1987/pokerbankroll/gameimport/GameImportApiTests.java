package io.github.kete1987.pokerbankroll.gameimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

class GameImportApiTests extends ApiIntegrationTest {

    static final String HEADER = "playedOn,room,gameType,buyIn";

    long winamax;

    @BeforeEach
    void createRoom() {
        winamax = insertRoom("Winamax", "EUR");
    }

    // ---- importing ----

    @Test
    void importsAGameWithOnlyTheRequiredColumns() {
        var json = assertThat(importCsv(HEADER, "2026-01-19,Winamax,TOURNAMENT,2.50")).hasStatusOk().bodyJson();

        json.extractingPath("$.dryRun").isEqualTo(false);
        json.extractingPath("$.imported").isEqualTo(true);
        json.extractingPath("$.rows").isEqualTo(1);
        json.extractingPath("$.games").isEqualTo(1);
        json.extractingPath("$.errorCount").isEqualTo(0);
        json.extractingPath("$.errors").asArray().isEmpty();
        Map<String, Object> game = jdbc.queryForMap("select * from game");
        assertThat(game).containsEntry("room_id", winamax)
                .containsEntry("game_type_code", "TOURNAMENT")
                .containsEntry("modality_code", "NLHE")
                // An imported game is a result, also when nothing was won.
                .containsEntry("status", "FINISHED")
                .containsEntry("entries", 1)
                .containsEntry("paid_with_ticket", false)
                .containsEntry("variant_id", null)
                .containsEntry("name", null);
        assertThat(game.get("played_on")).hasToString("2026-01-19");
        assertThat(game.get("buy_in")).hasToString("2.50");
        assertThat(game.get("net")).hasToString("-2.50");
    }

    @Test
    void importsEveryColumnInAnyOrder() {
        long ko = builtInVariantId("TOURNAMENT", "KO");

        assertThat(importCsv(
                "notes,paidWithTicket,ticketDescription,ticketPrizeValue,bounty,prize,entries,buyIn,name,modality,"
                        + "variant,gameType,currency,room,playedAt,playedOn",
                "\"Final table, at last\",true,Sunday Million,109,12.5,80,3,10,Kill The Fish,PLO,KO,TOURNAMENT,"
                        + "EUR,Winamax,21:30,2026-01-19"))
                .hasStatusOk().bodyJson().extractingPath("$.imported").isEqualTo(true);

        Map<String, Object> game = jdbc.queryForMap("select * from game");
        assertThat(game).containsEntry("variant_id", ko)
                .containsEntry("modality_code", "PLO")
                .containsEntry("name", "Kill The Fish")
                .containsEntry("entries", 3)
                .containsEntry("paid_with_ticket", true)
                .containsEntry("ticket_description", "Sunday Million")
                .containsEntry("notes", "Final table, at last");
        assertThat(mvc.get().uri("/games")).bodyJson().extractingPath("$.items[0].playedAt").isEqualTo("21:30:00");
        assertThat(game.get("prize")).hasToString("80.00");
        assertThat(game.get("bounty")).hasToString("12.50");
        assertThat(game.get("ticket_prize_value")).hasToString("109.00");
        // Two of the three entries were paid in cash.
        assertThat(game.get("net")).hasToString("72.50");
    }

    @Test
    void summarisesWhatWasImported() {
        insertRoom("PokerStars", "USD");

        var json = assertThat(importCsv(HEADER + ",prize",
                "2026-02-10,Winamax,TOURNAMENT,10,25",
                "2026-01-05,winamax,SIT_AND_GO,5,",
                "2026-03-01,Winamax,CASH,20,31.50",
                "2026-01-20,PokerStars,TOURNAMENT,11,0")).hasStatusOk().bodyJson();

        json.extractingPath("$.rows").isEqualTo(4);
        json.extractingPath("$.games").isEqualTo(4);
        json.extractingPath("$.from").isEqualTo("2026-01-05");
        json.extractingPath("$.to").isEqualTo("2026-03-01");
        json.extractingPath("$.gamesByType[*].gameType").asArray()
                .containsExactly("TOURNAMENT", "SIT_AND_GO", "CASH");
        json.extractingPath("$.gamesByType[*].games").asArray().containsExactly(2, 1, 1);
        json.extractingPath("$.totals[*].currencyCode").asArray().containsExactly("EUR", "USD");
        json.extractingPath("$.totals[*].games").asArray().containsExactly(3, 1);
        // 15 - 5 + 11.50 in euros; the dollars are never added to them.
        json.extractingPath("$.totals[*].net").asArray().containsExactly(21.5, -11.0);
        json.extractingPath("$.newRooms").asArray().isEmpty();
        json.extractingPath("$.newVariants").asArray().isEmpty();
        assertThat(jdbc.queryForObject("select sum(net) from game where room_id = ?", String.class, winamax))
                .isEqualTo("21.50");
    }

    @Test
    void readsQuotedValuesWithCommasQuotesAndLineBreaks() {
        assertThat(importCsv(HEADER + ",name,notes",
                "2026-01-19,Winamax,TOURNAMENT,5,\"Fish, chips & \"\"more\"\"\",\"Table 12\nSeat 3\"",
                "2026-01-20,Winamax,TOURNAMENT,5,Next,"))
                .hasStatusOk().bodyJson().extractingPath("$.games").isEqualTo(2);

        assertThat(jdbc.queryForObject("select name from game where played_on = '2026-01-19'", String.class))
                .isEqualTo("Fish, chips & \"more\"");
        assertThat(jdbc.queryForObject("select notes from game where played_on = '2026-01-19'", String.class))
                .isEqualTo("Table 12\nSeat 3");
    }

    @Test
    void acceptsAByteOrderMarkWindowsLineEndsAndEmptyRows() {
        byte[] file = ("﻿" + HEADER + "\r\n2026-01-19,Winamax,TOURNAMENT,5\r\n\r\n,,,\r\n"
                + "2026-01-20, Winamax ,tournament, 5 \r\n").getBytes(StandardCharsets.UTF_8);

        var json = assertThat(importFile(file, false)).hasStatusOk().bodyJson();

        json.extractingPath("$.rows").isEqualTo(2);
        json.extractingPath("$.games").isEqualTo(2);
    }

    @Test
    void theExampleOfTheDocumentationIsAValidFile() throws IOException {
        byte[] example = Files.readAllBytes(Path.of("..", "docs", "import-example.csv"));

        var json = assertThat(importFile(example, true)).hasStatusOk().bodyJson();

        json.extractingPath("$.errors").asArray().isEmpty();
        json.extractingPath("$.games").isEqualTo(6);
        json.extractingPath("$.newRooms[*].name").asArray().containsExactly("PokerStars");
        json.extractingPath("$.newVariants").asArray().isEmpty();
        // Tickets are not money: neither the one won nor the entry it paid.
        json.extractingPath("$.totals[?(@.currencyCode == 'EUR')].net").asArray().containsExactly(12.65);
    }

    // ---- rooms and variants ----

    @Test
    void createsTheRoomsThatDoNotExistWithTheCurrencyOfAnyOfTheirRows() {
        var json = assertThat(importCsv(HEADER + ",currency",
                "2026-01-19,PokerStars,TOURNAMENT,5,",
                "2026-01-20,pokerstars,TOURNAMENT,5,usd",
                "2026-01-21,Winamax,TOURNAMENT,5,EUR")).hasStatusOk().bodyJson();

        json.extractingPath("$.imported").isEqualTo(true);
        json.extractingPath("$.newRooms.length()").isEqualTo(1);
        json.extractingPath("$.newRooms[0].name").isEqualTo("PokerStars");
        json.extractingPath("$.newRooms[0].currencyCode").isEqualTo("USD");
        assertThat(jdbc.queryForObject("""
                select count(*) from game g join room r on r.id = g.room_id
                where r.name = 'PokerStars' and r.currency_code = 'USD' and r.active
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void aRoomThatDoesNotExistNeedsACurrency() {
        var json = assertThat(importCsv(HEADER, "2026-01-19,PokerStars,TOURNAMENT,5")).hasStatusOk().bodyJson();

        json.extractingPath("$.imported").isEqualTo(false);
        json.extractingPath("$.errors[0].row").isEqualTo(2);
        json.extractingPath("$.errors[0].field").isEqualTo("room");
        json.extractingPath("$.errors[0].code").isEqualTo("ROOM_NEEDS_CURRENCY");
        json.extractingPath("$.errors[0].message").asString().contains("PokerStars");
    }

    @Test
    void rejectsACurrencyThatIsNotTheOneOfTheRoomOrDoesNotExist() {
        var json = assertThat(importCsv(HEADER + ",currency",
                "2026-01-19,Winamax,TOURNAMENT,5,USD",
                "2026-01-19,Moon Poker,TOURNAMENT,5,XXX")).hasStatusOk().bodyJson();

        json.extractingPath("$.errors[*].row").asArray().containsExactly(2, 3);
        json.extractingPath("$.errors[*].field").asArray().containsExactly("currency", "currency");
        json.extractingPath("$.errors[*].code").asArray().containsExactly("ROOM_CURRENCY_MISMATCH", "UNKNOWN_CURRENCY");
        assertThat(count("room")).isEqualTo(1);
    }

    @Test
    void findsBuiltInVariantsByCodeAndTheOnesOfTheUserByName() {
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");
        long hyper = insertCustomVariant("SIT_AND_GO", "Hyper Turbo");

        var json = assertThat(importCsv(HEADER + ",variant",
                "2026-01-19,Winamax,SIT_AND_GO,5,expresso",
                "2026-01-20,Winamax,SIT_AND_GO,5,hyper turbo")).hasStatusOk().bodyJson();

        json.extractingPath("$.newVariants").asArray().isEmpty();
        assertThat(jdbc.queryForList("select variant_id from game order by played_on", Long.class))
                .containsExactly(expresso, hyper);
    }

    @Test
    void createsTheVariantsThatDoNotExistForTheTypeOfTheGame() {
        var json = assertThat(importCsv(HEADER + ",variant",
                "2026-01-19,Winamax,TOURNAMENT,5,Deep Stack",
                "2026-01-20,Winamax,TOURNAMENT,5,deep stack",
                // A code of another type is not that variant: it is a new one of this type.
                "2026-01-21,Winamax,CASH,5,KO")).hasStatusOk().bodyJson();

        json.extractingPath("$.imported").isEqualTo(true);
        json.extractingPath("$.newVariants[*].name").asArray().containsExactly("Deep Stack", "KO");
        json.extractingPath("$.newVariants[*].gameType").asArray().containsExactly("TOURNAMENT", "CASH");
        assertThat(jdbc.queryForObject(
                "select count(distinct variant_id) from game where game_type_code = 'TOURNAMENT'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from variant where code is null", Integer.class)).isEqualTo(2);
    }

    @Test
    void inactiveRoomsAndVariantsTakeNoImportedGamesEither() {
        jdbc.update("update room set active = false where id = ?", winamax);
        insertRoom("PokerStars", "USD");
        jdbc.update("update variant set active = false where code = 'KO'");

        var json = assertThat(importCsv(HEADER + ",variant",
                "2026-01-19,Winamax,TOURNAMENT,5,",
                "2026-01-19,PokerStars,TOURNAMENT,5,KO")).hasStatusOk().bodyJson();

        json.extractingPath("$.errors[*].code").asArray().containsExactly("ROOM_INACTIVE", "VARIANT_INACTIVE");
        json.extractingPath("$.errors[*].field").asArray().containsExactly("room", "variant");
    }

    // ---- all or nothing ----

    @Test
    void aDryRunReportsTheSameAndStoresNothing() {
        var json = assertThat(importFile(csv(HEADER + ",currency,variant",
                "2026-01-19,Winamax,TOURNAMENT,5,,Deep Stack",
                "2026-01-20,PokerStars,TOURNAMENT,5,USD,"), true)).hasStatusOk().bodyJson();

        json.extractingPath("$.dryRun").isEqualTo(true);
        json.extractingPath("$.imported").isEqualTo(false);
        json.extractingPath("$.games").isEqualTo(2);
        json.extractingPath("$.newRooms[0].name").isEqualTo("PokerStars");
        json.extractingPath("$.newVariants[0].name").isEqualTo("Deep Stack");
        json.extractingPath("$.totals[*].net").asArray().containsExactly(-5.0, -5.0);
        assertThat(count("game")).isZero();
        assertThat(count("room")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from variant where code is null", Integer.class)).isZero();
    }

    @Test
    void oneRowInErrorImportsNothing() {
        var json = assertThat(importCsv(HEADER + ",currency",
                "2026-01-19,Winamax,TOURNAMENT,5,",
                "2026-01-20,PokerStars,TOURNAMENT,5,USD",
                "2026-01-21,Winamax,TOURNAMENT,five,")).hasStatusOk().bodyJson();

        json.extractingPath("$.imported").isEqualTo(false);
        json.extractingPath("$.rows").isEqualTo(3);
        // The figures are those of the rows that are right.
        json.extractingPath("$.games").isEqualTo(2);
        json.extractingPath("$.errorCount").isEqualTo(1);
        json.extractingPath("$.errors[0].row").isEqualTo(4);
        assertThat(count("game")).isZero();
        assertThat(count("room")).isEqualTo(1);
    }

    @Test
    void importingAFileTwiceRecordsItsGamesTwice() {
        byte[] file = csv(HEADER, "2026-01-19,Winamax,TOURNAMENT,5");

        assertThat(importFile(file, false)).hasStatusOk();
        assertThat(importFile(file, false)).hasStatusOk();

        assertThat(count("game")).isEqualTo(2);
    }

    // ---- errors in rows ----

    @Test
    void reportsEveryValueThatCannotBeRead() {
        var json = assertThat(importCsv(
                HEADER + ",playedAt,modality,entries,prize,paidWithTicket",
                "19/01/2026,Winamax,SPIN,\"2,50\",9pm,HOLDEM,two,1e3,yes",
                ",,,,,,,,x",
                "2026-01-19,Winamax,TOURNAMENT,5,,,,,")).hasStatusOk().bodyJson();

        json.extractingPath("$.games").isEqualTo(1);
        json.extractingPath("$.errorCount").isEqualTo(13);
        json.extractingPath("$.errors[?(@.row == 2)].field").asArray().containsExactlyInAnyOrder(
                "playedOn", "playedAt", "gameType", "modality", "buyIn", "entries", "prize", "paidWithTicket");
        json.extractingPath("$.errors[?(@.row == 2)].code").asArray().containsExactlyInAnyOrder(
                "INVALID_DATE", "INVALID_TIME", "INVALID_GAME_TYPE", "INVALID_MODALITY", "INVALID_NUMBER",
                "INVALID_INTEGER", "INVALID_NUMBER", "INVALID_BOOLEAN");
        json.extractingPath("$.errors[?(@.row == 3)].field").asArray()
                .containsExactlyInAnyOrder("playedOn", "room", "gameType", "buyIn", "paidWithTicket");
        json.extractingPath("$.errors[?(@.row == 3 && @.field == 'room')].code").asArray().containsExactly("REQUIRED");
    }

    @Test
    void rowsFollowTheRulesOfAGameRecordedByHand() {
        var json = assertThat(importCsv(HEADER + ",entries,bounty,prize,ticketDescription",
                "2026-01-19,Winamax,CASH,20,2,5,,",
                "2026-01-19,Winamax,TOURNAMENT,-5,0,,1.005,",
                "2026-01-19,Winamax,TOURNAMENT,5,,,,Sunday Million")).hasStatusOk().bodyJson();

        json.extractingPath("$.games").isEqualTo(0);
        json.extractingPath("$.errors[?(@.row == 2)].field").asArray().containsExactlyInAnyOrder("entries", "bounty");
        json.extractingPath("$.errors[?(@.row == 2)].code").asArray()
                .containsExactly("CashGameFields", "CashGameFields");
        json.extractingPath("$.errors[?(@.row == 3)].field").asArray()
                .containsExactlyInAnyOrder("buyIn", "entries", "prize");
        json.extractingPath("$.errors[?(@.row == 3)].code").asArray()
                .containsExactlyInAnyOrder("DecimalMin", "Min", "Digits");
        json.extractingPath("$.errors[?(@.row == 4)].code").asArray().containsExactly("TicketDescriptionNeedsValue");
    }

    @Test
    void aRowWithMoreOrFewerValuesThanColumnsIsAnError() {
        var json = assertThat(importCsv(HEADER,
                "2026-01-19,Winamax,TOURNAMENT",
                "2026-01-19,Winamax,TOURNAMENT,5,extra")).hasStatusOk().bodyJson();

        json.extractingPath("$.errors[*].row").asArray().containsExactly(2, 3);
        json.extractingPath("$.errors[*].code").asArray().containsExactly("COLUMN_COUNT", "COLUMN_COUNT");
        json.extractingPath("$.errors[0].field").isNull();
    }

    @Test
    void rowsAreNumberedAsASpreadsheetDoes() {
        var json = assertThat(importCsv(HEADER + ",notes",
                "2026-01-19,Winamax,TOURNAMENT,5,\"two\nlines\"",
                "",
                "2026-01-19,Winamax,TOURNAMENT,x,")).hasStatusOk().bodyJson();

        // The header is row 1, a value with a line break is still one row and the empty row counts.
        json.extractingPath("$.errors[0].row").isEqualTo(4);
    }

    @Test
    void errorsAreExplainedInTheLanguageAsked() {
        assertThat(importFile(csv(HEADER, "19/01/2026,Winamax,TOURNAMENT,5"), false).header("Accept-Language", "es"))
                .hasStatusOk().bodyJson().extractingPath("$.errors[0].message")
                .isEqualTo("\"19/01/2026\" no es una fecha: escríbela como 2026-01-31.");
    }

    @Test
    void listsOnlyTheFirstErrorsAndCountsThemAll() {
        String[] rows = new String[GameImportService.MAX_ERRORS_LISTED + 50];
        java.util.Arrays.fill(rows, "2026-01-19,Winamax,TOURNAMENT,x");

        var json = assertThat(importCsv(HEADER, rows)).hasStatusOk().bodyJson();

        json.extractingPath("$.errorCount").isEqualTo(rows.length);
        json.extractingPath("$.errors.length()").isEqualTo(GameImportService.MAX_ERRORS_LISTED);
    }

    // ---- errors in the file ----

    @Test
    void rejectsAFileWithoutGames() {
        for (String file : List.of("", "  \n", HEADER, HEADER + "\n,,,\n\n")) {
            assertThat(importFile(file.getBytes(StandardCharsets.UTF_8), false))
                    .as(file).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_FILE_EMPTY");
        }
    }

    @Test
    void rejectsAHeaderThatIsNotTheOneOfTheFormat() {
        assertFileError("playedOn,room,gameType,buyIn,bounties", "IMPORT_UNKNOWN_COLUMN", "bounties");
        assertFileError("playedOn,room,gameType,buyIn,room", "IMPORT_DUPLICATE_COLUMN", "room");
        assertFileError("playedOn,room,gameType", "IMPORT_MISSING_COLUMN", "buyIn");
        // Another separator: the whole header is one column that nobody knows.
        assertFileError("playedOn;room;gameType;buyIn", "IMPORT_UNKNOWN_COLUMN", "playedOn;room;gameType;buyIn");
    }

    @Test
    void rejectsAFileThatIsNotUtf8OrNotCsv() {
        byte[] latin1 = (HEADER + ",name\n2026-01-19,Winamax,TOURNAMENT,5,Año nuevo\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(importFile(latin1, false)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_FILE_NOT_UTF8");

        assertThat(importCsv(HEADER + ",name", "2026-01-19,Winamax,TOURNAMENT,5,\"never closed"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_FILE_MALFORMED");
    }

    @Test
    void rejectsAFileThatIsTooLargeOrTooLong() {
        byte[] large = new byte[GameImportService.MAX_BYTES + 1];
        java.util.Arrays.fill(large, (byte) 'a');
        var tooLarge = assertThat(importFile(large, false)).hasStatus(HttpStatus.CONTENT_TOO_LARGE).bodyJson();
        tooLarge.extractingPath("$.code").isEqualTo("IMPORT_FILE_TOO_LARGE");
        tooLarge.extractingPath("$.detail").isEqualTo("The file is too large: the maximum is 5 MB.");

        String row = "2026-01-19,Winamax,CASH,1\n";
        String tooLong = HEADER + "\n" + row.repeat(GameImportService.MAX_ROWS + 1);
        assertThat(importFile(tooLong.getBytes(StandardCharsets.UTF_8), true)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_TOO_MANY_ROWS");
    }

    @Test
    void onlyTakesCsv() {
        assertThat(postJson("/imports/games", "{}")).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ---- size ----

    @Test
    void importsSomeThousandsOfGamesInAFewSeconds() {
        StringBuilder file = new StringBuilder(HEADER + ",prize,variant\n");
        for (int i = 0; i < 5_000; i++) {
            file.append("2026-01-%02d,Winamax,SIT_AND_GO,5,%d,EXPRESSO\n".formatted(1 + i % 28, i % 3 == 0 ? 10 : 0));
        }
        long start = System.nanoTime();

        assertThat(importFile(file.toString().getBytes(StandardCharsets.UTF_8), false))
                .hasStatusOk().bodyJson().extractingPath("$.games").isEqualTo(5_000);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(60));
        assertThat(count("game")).isEqualTo(5_000);
    }

    private void assertFileError(String header, String code, String column) {
        var json = assertThat(importCsv(header, "2026-01-19,Winamax,TOURNAMENT,5"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson();
        json.extractingPath("$.code").isEqualTo(code);
        json.extractingPath("$.detail").asString().contains("\"" + column + "\"");
    }

    private MockMvcRequestBuilder importCsv(String header, String... rows) {
        return importFile(csv(header, rows), false);
    }

    private MockMvcRequestBuilder importFile(byte[] file, boolean dryRun) {
        return mvc.post().uri("/imports/games").param("dryRun", String.valueOf(dryRun))
                .contentType("text/csv").content(file);
    }

    private static byte[] csv(String header, String... rows) {
        return (header + "\n" + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
