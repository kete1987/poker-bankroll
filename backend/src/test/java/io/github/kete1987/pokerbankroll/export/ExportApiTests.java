package io.github.kete1987.pokerbankroll.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import io.github.kete1987.pokerbankroll.bankroll.MovementType;
import io.github.kete1987.pokerbankroll.gameimport.GameCsv;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ExportApiTests extends ApiIntegrationTest {

    static final String CSV_HEADER = String.join(",", GameCsv.COLUMNS);
    static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MessageSource messages;

    long winamax;
    long pokerStars;

    @BeforeEach
    void createRooms() {
        winamax = insertRoom("Winamax", "EUR");
        pokerStars = insertRoom("PokerStars", "USD");
    }

    // ---- games as CSV ----

    @Test
    void theCsvOfGamesHasTheColumnsOfTheImportAndOneRowPerGame() {
        game("""
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "variantId": %d, "modality": "PLO", "name": "Kill The Fish", "buyIn": 10, "entries": 3,
                 "prize": 80, "bounty": 12.5, "ticketPrizeValue": 109, "ticketDescription": "Sunday Million",
                 "paidWithTicket": true, "notes": "Final table", "tags": ["series", "Challenge"]}"""
                .formatted(winamax, builtInVariantId("TOURNAMENT", "KO")));

        MvcTestResult result = export("/exports/games?format=CSV");

        assertThat(result).hasStatusOk();
        assertThat(lines(result)).containsExactly(
                CSV_HEADER,
                "2026-01-19,21:30,Winamax,EUR,TOURNAMENT,KO,PLO,Kill The Fish,10.00,3,80.00,12.50,109.00,"
                        + "Sunday Million,true,Final table,Challenge;series");
    }

    @Test
    void theFileIsAnAttachmentNamedAfterItsContentAndTheDay() {
        MvcTestResult csv = export("/exports/games?format=CSV");
        MvcTestResult xlsx = export("/exports/movements?format=XLSX");

        String today = LocalDate.now().toString();
        assertThat(csv).hasStatusOk().headers()
                .hasValue("Content-Type", "text/csv;charset=UTF-8")
                .hasValue("Content-Disposition", "attachment; filename=\"poker-bankroll-games-" + today + ".csv\"")
                .hasValue("Cache-Control", "no-store")
                .hasValue("X-Content-Type-Options", "nosniff");
        assertThat(xlsx).hasStatusOk().headers()
                .hasValue("Content-Type", XLSX_TYPE)
                .hasValue("Content-Disposition",
                        "attachment; filename=\"poker-bankroll-movements-" + today + ".xlsx\"")
                .hasValue("Cache-Control", "no-store");
    }

    @Test
    void withoutGamesTheCsvIsOnlyItsHeader() {
        assertThat(lines(export("/exports/games?format=CSV"))).containsExactly(CSV_HEADER);
    }

    @Test
    void gamesInPlayAreNeverExported() throws IOException {
        game("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 5, "name": "Playing"}"""
                .formatted(winamax));
        game("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 5, "name": "Done",
                 "status": "FINISHED"}""".formatted(winamax));
        assertThat(jdbc.queryForObject("select count(*) from game where status = 'IN_PLAY'", Integer.class)).isOne();

        assertThat(column(lines(export("/exports/games?format=CSV")), GameCsv.NAME)).containsExactly("Done");
        // Not even when they are asked for: the status is not a filter of the export.
        assertThat(column(lines(export("/exports/games?format=CSV&status=IN_PLAY")), GameCsv.NAME))
                .containsExactly("Done");
        assertThat(texts(sheet(export("/exports/games?format=XLSX")), 7)).containsExactly("Name", "Done");
    }

    @Test
    void theFiltersOfTheListSelectTheGames() {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        finished("A", "2026-01-10", winamax, "TOURNAMENT", ko, "NLHE", null);
        finished("B", "2026-02-10", winamax, "SIT_AND_GO", null, "NLHE", "table 7");
        finished("C", "2026-03-10", pokerStars, "TOURNAMENT", null, "PLO", null);
        finished("D", "2026-04-10", pokerStars, "CASH", null, "NLHE", null);

        assertThat(namesOf("")).containsExactly("A", "B", "C", "D");
        assertThat(namesOf("&from=2026-02-10&to=2026-03-10")).containsExactly("B", "C");
        assertThat(namesOf("&gameType=SIT_AND_GO,CASH")).containsExactly("B", "D");
        assertThat(namesOf("&gameType=SIT_AND_GO&gameType=CASH")).containsExactly("B", "D");
        assertThat(namesOf("&roomId=" + pokerStars)).containsExactly("C", "D");
        assertThat(namesOf("&roomId=" + winamax + "&roomId=" + pokerStars + "&gameType=TOURNAMENT"))
                .containsExactly("A", "C");
        assertThat(namesOf("&variantId=" + ko)).containsExactly("A");
        assertThat(namesOf("&modality=PLO")).containsExactly("C");
        assertThat(namesOf("&currency=EUR")).containsExactly("A", "B");
        assertThat(namesOf("&q=TABLE")).containsExactly("B");
        assertThat(namesOf("&roomId=" + winamax + "&currency=USD")).isEmpty();
    }

    @Test
    void everyGameIsExportedOldestFirstHoweverManyTheyAre() {
        // More than the rows read from the database at a time, on 300 different days.
        jdbc.update("""
                insert into game (played_on, room_id, game_type_code, buy_in, name)
                select date '2025-01-01' + (n % 300), ?, 'TOURNAMENT', 1, 'Game ' || n
                from generate_series(1, 1234) n
                """, winamax);

        List<String> lines = lines(export("/exports/games?format=CSV"));

        assertThat(lines).hasSize(1 + 1234);
        List<String> dates = column(lines, GameCsv.PLAYED_ON);
        assertThat(dates).isSorted().startsWith("2025-01-01").endsWith("2025-10-27");
        assertThat(column(lines, GameCsv.NAME)).doesNotHaveDuplicates();
    }

    @Test
    void importingTheExportedCsvIntoAnEmptyDatabaseGivesTheSameGames() throws IOException {
        long ko = builtInVariantId("TOURNAMENT", "KO");
        long expresso = builtInVariantId("SIT_AND_GO", "EXPRESSO");
        long turbo = insertCustomVariant("TOURNAMENT", "Turbo, \"deep\"");
        // Every field, with what a CSV has to quote.
        game("""
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "variantId": %d, "modality": "PLO", "name": "Fish, chips & \\"more\\"", "buyIn": 10, "entries": 3,
                 "prize": 80, "bounty": 12.5, "ticketPrizeValue": 109, "ticketDescription": "Sunday Million",
                 "paidWithTicket": true, "notes": "Table 12\\nSeat 3; ñandú €",
                 "tags": ["Satélite", "with friends, \\"quoted\\""]}""".formatted(winamax, ko));
        // Only what is required, nothing won.
        game("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 2.5,
                 "status": "FINISHED"}""".formatted(winamax));
        // The same day: with a time before and after, and another one without it.
        game("""
                {"playedOn": "2026-01-19", "playedAt": "09:05:30", "roomId": %d, "gameType": "SIT_AND_GO",
                 "variantId": %d, "buyIn": 5, "prize": 10}""".formatted(winamax, expresso));
        game("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 20,
                 "entries": 2, "prize": 0, "status": "FINISHED", "name": "  Sunday  "}""".formatted(pokerStars, turbo));
        game("""
                {"playedOn": "2025-12-31", "playedAt": "23:59", "roomId": %d, "gameType": "CASH", "modality": "PLO",
                 "name": "NL10", "buyIn": 10, "prize": 31.5, "notes": "=1+1"}""".formatted(pokerStars));
        game("""
                {"playedOn": "2026-02-01", "roomId": %d, "gameType": "SIT_AND_GO", "buyIn": 1,
                 "ticketPrizeValue": 5, "paidWithTicket": true, "tags": ["satélite"]}""".formatted(winamax));
        // In play: it is not exported, so it is not there afterwards.
        game("""
                {"playedOn": "2026-02-02", "roomId": %d, "gameType": "TOURNAMENT", "buyIn": 50}""".formatted(winamax));
        List<Map<String, Object>> before = listedGames();
        assertThat(before).hasSize(6);

        byte[] file = export("/exports/games?format=CSV").getResponse().getContentAsByteArray();
        jdbc.update("delete from game");
        jdbc.update("delete from tag");
        jdbc.update("delete from room");
        jdbc.update("delete from variant where code is null");
        var imported = assertThat(mvc.post().uri("/imports/games").contentType("text/csv").content(file))
                .hasStatusOk().bodyJson();

        imported.extractingPath("$.errors").asArray().isEmpty();
        imported.extractingPath("$.imported").isEqualTo(true);
        imported.extractingPath("$.games").isEqualTo(6);
        imported.extractingPath("$.newRooms[*].name").asArray().containsExactlyInAnyOrder("Winamax", "PokerStars");
        imported.extractingPath("$.newVariants[*].name").asArray().containsExactly("Turbo, \"deep\"");
        // The same games, listed in the same order.
        assertThat(listedGames()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from game", Integer.class)).isEqualTo(6);
        // And exporting them again gives the same file.
        assertThat(export("/exports/games?format=CSV").getResponse().getContentAsByteArray()).isEqualTo(file);
    }

    // ---- games as Excel ----

    @Test
    void theExcelOfGamesHasTypedCellsAndWhatWasInvestedAndTheNet() throws IOException {
        game("""
                {"playedOn": "2026-01-19", "playedAt": "21:30", "roomId": %d, "gameType": "TOURNAMENT",
                 "variantId": %d, "modality": "PLO", "name": "Kill The Fish", "buyIn": 10, "entries": 3,
                 "prize": 80, "bounty": 12.5, "ticketPrizeValue": 109, "ticketDescription": "Sunday Million",
                 "paidWithTicket": true, "notes": "Final table", "tags": ["Series", "challenge"]}"""
                .formatted(winamax, builtInVariantId("TOURNAMENT", "KO")));
        game("""
                {"playedOn": "2026-01-20", "roomId": %d, "gameType": "CASH", "buyIn": 20, "prize": 5.5}"""
                .formatted(pokerStars));

        MvcTestResult result = export("/exports/games?format=XLSX");

        assertThat(result).hasStatusOk();
        Sheet sheet = sheet(result);
        assertThat(sheet.getName()).isEqualTo("Games");
        List<Row> rows = sheet.read();
        assertThat(rows).hasSize(3);
        assertThat(texts(rows.get(0))).containsExactly("Date", "Time", "Room", "Currency", "Type", "Variant", "Game",
                "Name", "Buy-in", "Entries", "Paid with ticket", "Invested", "Prize", "Bounties", "Net", "Ticket won",
                "Ticket description", "Tags", "Notes");

        Row game = rows.get(1);
        // A real date: a number for Excel, shown as a date.
        assertThat(game.getCell(0).getType()).isEqualTo(CellType.NUMBER);
        assertThat(game.getCell(0).asDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 19));
        assertThat(game.getCell(0).getDataFormatString()).isEqualTo("yyyy-mm-dd");
        assertThat(game.getCell(1).getType()).isEqualTo(CellType.NUMBER);
        assertThat(game.getCell(1).asDate().toLocalTime()).hasToString("21:30");
        assertThat(game.getCell(1).getDataFormatString()).isEqualTo("hh:mm");
        assertThat(game.getCellText(2)).isEqualTo("Winamax");
        assertThat(game.getCellText(3)).isEqualTo("EUR");
        assertThat(game.getCellText(4)).isEqualTo("Tournament");
        assertThat(game.getCellText(5)).isEqualTo("KO");
        assertThat(game.getCellText(6)).isEqualTo("Omaha");
        assertThat(game.getCellText(7)).isEqualTo("Kill The Fish");
        assertThat(amount(game, 8)).isEqualByComparingTo("10");
        assertThat(game.getCell(8).getDataFormatString()).isEqualTo("#,##0.00");
        assertThat(game.getCell(9).getType()).isEqualTo(CellType.NUMBER);
        assertThat(game.getCell(9).asNumber()).isEqualByComparingTo("3");
        assertThat(game.getCell(9).getDataFormatString()).isEqualTo("0");
        assertThat(game.getCellText(10)).isEqualTo("Yes");
        // Two of the three entries were paid in cash; the ticket won is not money.
        assertThat(amount(game, 11)).isEqualByComparingTo("20");
        assertThat(amount(game, 12)).isEqualByComparingTo("80");
        assertThat(amount(game, 13)).isEqualByComparingTo("12.5");
        assertThat(amount(game, 14)).isEqualByComparingTo("72.5");
        assertThat(amount(game, 15)).isEqualByComparingTo("109");
        assertThat(game.getCellText(16)).isEqualTo("Sunday Million");
        assertThat(game.getCellText(17)).isEqualTo("challenge, Series");
        assertThat(game.getCellText(18)).isEqualTo("Final table");

        Row cash = rows.get(2);
        // No time, variant, name, ticket description, tags or notes: empty cells.
        assertThat(List.of(1, 5, 7, 16, 17, 18)).allSatisfy(empty -> assertThat(cash.getOptionalCell(empty)
                .filter(cell -> cell.getType() != CellType.EMPTY)).isEmpty());
        assertThat(cash.getCellText(3)).isEqualTo("USD");
        assertThat(cash.getCellText(4)).isEqualTo("Cash");
        assertThat(cash.getCellText(6)).isEqualTo("Hold'em");
        assertThat(cash.getCellText(10)).isEqualTo("No");
        assertThat(amount(cash, 14)).isEqualByComparingTo("-14.5");
    }

    @Test
    void theExcelOfGamesIsInTheLanguageOfTheRequestButNotWhatTheUserWrote() throws IOException {
        long turbo = insertCustomVariant("TOURNAMENT", "Turbo");
        game("""
                {"playedOn": "2026-01-19", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 10,
                 "name": "Regular Speed", "paidWithTicket": true, "status": "FINISHED"}"""
                .formatted(winamax, builtInVariantId("TOURNAMENT", "REGULAR")));
        game("""
                {"playedOn": "2026-01-20", "roomId": %d, "gameType": "TOURNAMENT", "variantId": %d, "buyIn": 10,
                 "status": "FINISHED", "notes": "Yes"}""".formatted(winamax, turbo));

        Sheet sheet = sheet(mvc.get().uri("/exports/games?format=XLSX").header("Accept-Language", "es-ES").exchange());

        assertThat(sheet.getName()).isEqualTo("Partidas");
        List<Row> rows = sheet.read();
        assertThat(texts(rows.get(0))).containsExactly("Fecha", "Hora", "Sala", "Moneda", "Tipo", "Variante", "Juego",
                "Nombre", "Buy-in", "Entradas", "Pagada con ticket", "Invertido", "Premio", "Bounties", "Neto",
                "Ticket ganado", "Descripción del ticket", "Etiquetas", "Notas");
        Row regular = rows.get(1);
        assertThat(regular.getCell(0).asDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 19));
        assertThat(regular.getCell(0).getDataFormatString()).isEqualTo("dd/mm/yyyy");
        assertThat(regular.getCellText(4)).isEqualTo("Torneo");
        assertThat(regular.getCellText(5)).isEqualTo("Normal");
        assertThat(regular.getCellText(7)).isEqualTo("Regular Speed");
        assertThat(regular.getCellText(10)).isEqualTo("Sí");
        Row custom = rows.get(2);
        assertThat(custom.getCellText(5)).isEqualTo("Turbo");
        assertThat(custom.getCellText(10)).isEqualTo("No");
        assertThat(custom.getCellText(18)).isEqualTo("Yes");
    }

    @Test
    void withoutGamesTheExcelIsOnlyItsHeader() throws IOException {
        List<Row> rows = sheet(export("/exports/games?format=XLSX")).read();

        assertThat(rows).hasSize(1);
        assertThat(texts(rows.get(0))).hasSize(19);
    }

    @Test
    void everyBuiltInVariantAndEveryCodeHasItsLabelInEachLanguage() {
        List<String> keys = new ArrayList<>();
        jdbc.queryForList("select code from variant where code is not null", String.class)
                .forEach(code -> keys.add("variant." + code));
        jdbc.queryForList("select code from game_type", String.class).forEach(code -> keys.add("gameType." + code));
        jdbc.queryForList("select code from modality", String.class).forEach(code -> keys.add("modality." + code));
        for (MovementType type : MovementType.values()) {
            keys.add("movementType." + type);
        }
        assertThat(keys).hasSizeGreaterThan(15);

        for (Locale locale : List.of(Locale.ENGLISH, Locale.of("es"))) {
            assertThat(keys).allSatisfy(key ->
                    // Without a default: a missing label is an exception, not the code.
                    assertThat(messages.getMessage(ExportService.LABELS + key, null, null, locale))
                            .as("%s%s in %s: add it to messages*.properties", ExportService.LABELS, key, locale)
                            .isNotBlank());
        }
    }

    // ---- movements ----

    @Test
    void theCsvOfMovementsHasThoseOfARoomAndThoseOfNoneWithSignedAmounts() {
        movement("""
                {"occurredOn": "2026-01-05", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 500,
                 "notes": "Initial, \\"all in\\""}""");
        movement("""
                {"occurredOn": "2026-01-19", "type": "WITHDRAWAL", "roomId": %d, "amount": 30}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-01-19", "type": "ADJUSTMENT", "roomId": %d, "amount": -4.5}""".formatted(pokerStars));
        movement("""
                {"occurredOn": "2026-01-02", "type": "BONUS", "roomId": %d, "amount": 12.34}""".formatted(winamax));

        MvcTestResult result = export("/exports/movements?format=CSV");

        assertThat(result).hasStatusOk().headers().hasValue("Content-Type", "text/csv;charset=UTF-8");
        // Oldest first; the one without a room has no room, but its currency.
        assertThat(lines(result)).containsExactly(
                "occurredOn,type,room,currency,amount,notes",
                "2026-01-02,BONUS,Winamax,EUR,12.34,",
                "2026-01-05,DEPOSIT,,EUR,500.00,\"Initial, \"\"all in\"\"\"",
                "2026-01-19,WITHDRAWAL,Winamax,EUR,-30.00,",
                "2026-01-19,ADJUSTMENT,PokerStars,USD,-4.50,");
    }

    @Test
    void theFiltersOfTheListSelectTheMovements() {
        movement("""
                {"occurredOn": "2026-01-05", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 1}""");
        movement("""
                {"occurredOn": "2026-02-05", "type": "DEPOSIT", "roomId": %d, "amount": 2}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-03-05", "type": "WITHDRAWAL", "roomId": %d, "amount": 3}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-04-05", "type": "BONUS", "roomId": %d, "amount": 4}""".formatted(pokerStars));

        assertThat(amountsOf("")).containsExactly("1.00", "2.00", "-3.00", "4.00");
        assertThat(amountsOf("&from=2026-02-05&to=2026-03-05")).containsExactly("2.00", "-3.00");
        assertThat(amountsOf("&type=DEPOSIT")).containsExactly("1.00", "2.00");
        assertThat(amountsOf("&roomId=" + winamax)).containsExactly("2.00", "-3.00");
        assertThat(amountsOf("&roomId=" + winamax + "," + pokerStars)).containsExactly("2.00", "-3.00", "4.00");
        assertThat(amountsOf("&withoutRoom=true")).containsExactly("1.00");
        assertThat(amountsOf("&currency=EUR")).containsExactly("1.00", "2.00", "-3.00");
        assertThat(amountsOf("&currency=USD&type=DEPOSIT")).isEmpty();
    }

    @Test
    void theExcelOfMovementsHasTypedCellsInTheLanguageOfTheRequest() throws IOException {
        movement("""
                {"occurredOn": "2026-01-05", "type": "DEPOSIT", "currencyCode": "EUR", "amount": 500,
                 "notes": "Bankroll inicial"}""");
        movement("""
                {"occurredOn": "2026-01-19", "type": "WITHDRAWAL", "roomId": %d, "amount": 30}""".formatted(winamax));
        movement("""
                {"occurredOn": "2026-01-20", "type": "BONUS", "roomId": %d, "amount": 2.25}""".formatted(pokerStars));
        movement("""
                {"occurredOn": "2026-01-21", "type": "ADJUSTMENT", "roomId": %d, "amount": -1}""".formatted(pokerStars));

        Sheet english = sheet(export("/exports/movements?format=XLSX"));
        Sheet spanish = sheet(mvc.get().uri("/exports/movements?format=XLSX").header("Accept-Language", "es").exchange());

        assertThat(english.getName()).isEqualTo("Movements");
        List<Row> rows = english.read();
        assertThat(texts(rows.get(0))).containsExactly("Date", "Type", "Room", "Currency", "Amount", "Notes");
        assertThat(texts(english, 1)).containsExactly("Type", "Deposit", "Withdrawal", "Rakeback / bonus", "Adjustment");
        Row deposit = rows.get(1);
        assertThat(deposit.getCell(0).getType()).isEqualTo(CellType.NUMBER);
        assertThat(deposit.getCell(0).asDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(deposit.getCell(0).getDataFormatString()).isEqualTo("yyyy-mm-dd");
        // It belongs to no room.
        assertThat(deposit.getOptionalCell(2).filter(cell -> cell.getType() != CellType.EMPTY)).isEmpty();
        assertThat(deposit.getCellText(3)).isEqualTo("EUR");
        assertThat(amount(deposit, 4)).isEqualByComparingTo("500");
        assertThat(deposit.getCell(4).getDataFormatString()).isEqualTo("#,##0.00");
        assertThat(deposit.getCellText(5)).isEqualTo("Bankroll inicial");
        Row withdrawal = rows.get(2);
        assertThat(withdrawal.getCellText(2)).isEqualTo("Winamax");
        assertThat(amount(withdrawal, 4)).isEqualByComparingTo("-30");
        assertThat(amount(rows.get(4), 4)).isEqualByComparingTo("-1");

        assertThat(spanish.getName()).isEqualTo("Movimientos");
        List<Row> filas = spanish.read();
        assertThat(texts(filas.get(0))).containsExactly("Fecha", "Tipo", "Sala", "Moneda", "Importe", "Notas");
        assertThat(texts(spanish, 1)).containsExactly("Tipo", "Ingreso", "Retirada", "Rakeback / bono", "Ajuste");
        assertThat(filas.get(1).getCell(0).getDataFormatString()).isEqualTo("dd/mm/yyyy");
    }

    // ---- requests ----

    @Test
    void theFormatIsRequiredAndOneOfTheTwo() {
        assertThat(mvc.get().uri("/exports/games")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/exports/games?format=PDF")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertThat(mvc.get().uri("/exports/movements?format=")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- helpers ----

    private void game(String json) {
        assertThat(postJson("/games", json)).hasStatus(HttpStatus.CREATED);
    }

    private void finished(String name, String playedOn, long roomId, String gameType, Long variantId, String modality,
            String notes) {
        game("""
                {"playedOn": "%s", "roomId": %d, "gameType": "%s", "variantId": %s, "modality": "%s", "name": "%s",
                 "buyIn": 5, "status": "FINISHED", "notes": %s}"""
                .formatted(playedOn, roomId, gameType, variantId, modality, name,
                        notes == null ? null : "\"" + notes + "\""));
    }

    private void movement(String json) {
        assertThat(postJson("/bankroll/movements", json)).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult export(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private List<String> namesOf(String filters) {
        return column(lines(export("/exports/games?format=CSV" + filters)), GameCsv.NAME);
    }

    private List<String> amountsOf(String filters) {
        return column(lines(export("/exports/movements?format=CSV" + filters)), "amount");
    }

    /** The lines of a CSV file whose values have no line breaks. */
    private static List<String> lines(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        String text = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).endsWith("\r\n");
        return List.of(text.split("\r\n"));
    }

    /** The values of a column of a CSV file without quoted values, the header apart. */
    private static List<String> column(List<String> lines, String name) {
        int index = List.of(lines.get(0).split(",")).indexOf(name);
        assertThat(index).isNotNegative();
        return lines.stream().skip(1).map(line -> line.split(",", -1)[index]).toList();
    }

    /** The finished games as the list gives them, without what a new database changes: ids and times. */
    private List<Map<String, Object>> listedGames() throws IOException {
        MvcTestResult result = mvc.get().uri("/games?status=FINISHED&size=200").exchange();
        assertThat(result).hasStatusOk();
        Map<String, Object> page = JSON.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() { });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> games = (List<Map<String, Object>>) page.get("items");
        for (Map<String, Object> game : games) {
            game.keySet().removeAll(List.of("id", "createdAt", "updatedAt"));
            withoutId(game.get("room"));
            withoutId(game.get("variant"));
            ((List<?>) game.get("tags")).forEach(ExportApiTests::withoutId);
        }
        return games;
    }

    @SuppressWarnings("unchecked")
    private static void withoutId(Object reference) {
        if (reference != null) {
            ((Map<String, Object>) reference).remove("id");
        }
    }

    private static Sheet sheet(MvcTestResult result) throws IOException {
        assertThat(result).hasStatusOk().headers().hasValue("Content-Type", XLSX_TYPE);
        // With the formats of the cells, which the reader leaves out by default.
        ReadableWorkbook workbook = new ReadableWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()), new ReadingOptions(true, false));
        assertThat(workbook.getSheets()).hasSize(1);
        return workbook.getFirstSheet();
    }

    private static List<String> texts(Row row) {
        return row.stream().map(cell -> cell == null ? null : cell.getText()).toList();
    }

    /** The texts of a column, header included. */
    private static List<String> texts(Sheet sheet, int column) throws IOException {
        return sheet.read().stream().map(row -> row.getCellText(column)).toList();
    }

    private static BigDecimal amount(Row row, int column) {
        Optional<Cell> cell = row.getOptionalCell(column);
        assertThat(cell).isPresent();
        assertThat(cell.get().getType()).isEqualTo(CellType.NUMBER);
        return cell.get().asNumber();
    }
}
