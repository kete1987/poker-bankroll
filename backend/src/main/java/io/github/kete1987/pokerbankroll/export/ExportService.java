package io.github.kete1987.pokerbankroll.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import io.github.kete1987.pokerbankroll.bankroll.BankrollService;
import io.github.kete1987.pokerbankroll.bankroll.MovementFilter;
import io.github.kete1987.pokerbankroll.bankroll.MovementResponse;
import io.github.kete1987.pokerbankroll.export.ExcelSheet.Column;
import io.github.kete1987.pokerbankroll.export.ExcelSheet.Kind;
import io.github.kete1987.pokerbankroll.game.GameFilter;
import io.github.kete1987.pokerbankroll.game.GameResponse;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.game.GameService;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import io.github.kete1987.pokerbankroll.gameimport.GameCsv;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes the games and the bankroll movements a filter selects as a file, all of them: as CSV
 * (plain data with codes; for games, the format of the import) or as an Excel workbook made to be
 * read, with its texts in the language of the request.
 *
 * <p>The rows are read from the database a few at a time; the file is built in memory, where
 * tens of thousands of games take a few megabytes.
 */
@Service
public class ExportService {

    /** Columns of the CSV file of movements: there is no import for them, so they are only these. */
    static final List<String> MOVEMENT_CSV_COLUMNS = List.of("occurredOn", "type", "room", "currency", "amount", "notes");

    /** Prefix of the texts of the Excel files in {@code messages*.properties}. */
    static final String LABELS = "export.";

    private final GameService games;
    private final BankrollService bankroll;
    private final MessageSource messages;

    ExportService(GameService games, BankrollService bankroll, MessageSource messages) {
        this.games = games;
        this.bankroll = bankroll;
        this.messages = messages;
    }

    /**
     * The finished games the filter selects, oldest first. Games in play are never exported,
     * whatever the filter says: they have no result yet, and the import would record them as
     * finished.
     */
    public ExportFile games(ExportFormat format, GameFilter filter) {
        GameFilter finished = new GameFilter(filter.from(), filter.to(), filter.gameTypes(), filter.modality(),
                filter.roomIds(), filter.variantIds(), GameStatus.FINISHED, filter.currencyCode(), filter.text());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            switch (format) {
                case CSV -> {
                    Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                    GameCsv.Writer csv = new GameCsv.Writer(writer);
                    games.forEach(finished, unchecked(csv::write));
                    csv.flush();
                }
                case XLSX -> {
                    Labels labels = new Labels(LocaleContextHolder.getLocale());
                    try (ExcelSheet<GameResponse> sheet = new ExcelSheet<>(out, labels.text("sheet.games"),
                            labels.text("dateFormat"), gameColumns(labels))) {
                        games.forEach(finished, sheet::add);
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return file("games", format, out);
    }

    /** The movements the filter selects, oldest first. */
    public ExportFile movements(ExportFormat format, MovementFilter filter) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            switch (format) {
                case CSV -> {
                    Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                    CSVPrinter csv = new CSVPrinter(writer, CSVFormat.RFC4180);
                    csv.printRecord(MOVEMENT_CSV_COLUMNS);
                    bankroll.forEach(filter, unchecked(movement -> csv.printRecord(
                            movement.occurredOn(),
                            movement.type(),
                            movement.room() == null ? null : movement.room().name(),
                            movement.currencyCode(),
                            // Signed, so the amounts of a file add up to what they did to the bankroll.
                            movement.signedAmount().toPlainString(),
                            movement.notes())));
                    csv.flush();
                }
                case XLSX -> {
                    Labels labels = new Labels(LocaleContextHolder.getLocale());
                    try (ExcelSheet<MovementResponse> sheet = new ExcelSheet<>(out, labels.text("sheet.movements"),
                            labels.text("dateFormat"), movementColumns(labels))) {
                        bankroll.forEach(filter, sheet::add);
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return file("movements", format, out);
    }

    private static ExportFile file(String content, ExportFormat format, ByteArrayOutputStream out) {
        String name = "poker-bankroll-" + content + "-" + LocalDate.now() + "." + format.extension();
        return new ExportFile(name, format.contentType(), out.toByteArray());
    }

    private static List<Column<GameResponse>> gameColumns(Labels labels) {
        String yes = labels.text("yes");
        String no = labels.text("no");
        return List.of(
                gameColumn(labels, "playedOn", Kind.DATE, 12, GameResponse::playedOn),
                gameColumn(labels, "playedAt", Kind.TIME, 8, GameResponse::playedAt),
                gameColumn(labels, "room", Kind.TEXT, 18, game -> game.room().name()),
                gameColumn(labels, "currency", Kind.TEXT, 9, GameResponse::currencyCode),
                gameColumn(labels, "gameType", Kind.TEXT, 16, game -> labels.text("gameType." + game.gameType())),
                gameColumn(labels, "variant", Kind.TEXT, 22, game -> labels.variant(game.variant())),
                gameColumn(labels, "modality", Kind.TEXT, 10, game -> labels.text("modality." + game.modality())),
                gameColumn(labels, "name", Kind.TEXT, 30, GameResponse::name),
                gameColumn(labels, "buyIn", Kind.MONEY, 12, GameResponse::buyIn),
                gameColumn(labels, "entries", Kind.INTEGER, 9, GameResponse::entries),
                gameColumn(labels, "paidWithTicket", Kind.TEXT, 17, game -> game.paidWithTicket() ? yes : no),
                gameColumn(labels, "invested", Kind.MONEY, 12, GameResponse::invested),
                gameColumn(labels, "prize", Kind.MONEY, 12, GameResponse::prize),
                gameColumn(labels, "bounty", Kind.MONEY, 12, GameResponse::bounty),
                gameColumn(labels, "net", Kind.MONEY, 12, GameResponse::net),
                gameColumn(labels, "ticketPrizeValue", Kind.MONEY, 14, GameResponse::ticketPrizeValue),
                gameColumn(labels, "ticketDescription", Kind.TEXT, 24, GameResponse::ticketDescription),
                gameColumn(labels, "notes", Kind.TEXT, 40, GameResponse::notes));
    }

    private static Column<GameResponse> gameColumn(Labels labels, String name, Kind kind, double width,
            Function<GameResponse, @Nullable Object> value) {
        return new Column<>(labels.text("games." + name), kind, width, value);
    }

    private static List<Column<MovementResponse>> movementColumns(Labels labels) {
        return List.of(
                movementColumn(labels, "occurredOn", Kind.DATE, 12, MovementResponse::occurredOn),
                movementColumn(labels, "type", Kind.TEXT, 18,
                        movement -> labels.text("movementType." + movement.type())),
                movementColumn(labels, "room", Kind.TEXT, 18,
                        movement -> movement.room() == null ? null : movement.room().name()),
                movementColumn(labels, "currency", Kind.TEXT, 9, MovementResponse::currencyCode),
                movementColumn(labels, "amount", Kind.MONEY, 14, MovementResponse::signedAmount),
                movementColumn(labels, "notes", Kind.TEXT, 40, MovementResponse::notes));
    }

    private static Column<MovementResponse> movementColumn(Labels labels, String name, Kind kind, double width,
            Function<MovementResponse, @Nullable Object> value) {
        return new Column<>(labels.text("movements." + name), kind, width, value);
    }

    /** The texts of an Excel file in one language. What the user wrote is never translated. */
    private final class Labels {

        private final Locale locale;

        Labels(Locale locale) {
            this.locale = locale;
        }

        String text(String key) {
            return messages.getMessage(LABELS + key, null, locale);
        }

        /** The name of a variant: translated when it is built-in, as the user wrote it otherwise. */
        @Nullable String variant(@Nullable VariantRef variant) {
            if (variant == null) {
                return null;
            }
            String code = variant.code();
            return code == null ? variant.name() : messages.getMessage(LABELS + "variant." + code, null, code, locale);
        }
    }

    /** Something that writes a row and may fail as writing does. */
    @FunctionalInterface
    private interface RowWriter<T> {
        void write(T row) throws IOException;
    }

    private static <T> java.util.function.Consumer<T> unchecked(RowWriter<T> writer) {
        return row -> {
            try {
                writer.write(row);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }
}
