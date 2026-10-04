package io.github.kete1987.pokerbankroll.gameimport;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.game.GameResponse;
import io.github.kete1987.pokerbankroll.game.GameResponse.VariantRef;
import io.github.kete1987.pokerbankroll.tag.TagName;
import io.github.kete1987.pokerbankroll.tag.TagRef;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

/**
 * The CSV format of the games, read by the import and written by the export: UTF-8, comma
 * separated, quoted as in RFC 4180, with a header row naming the columns (see
 * {@code docs/import.md}). Reading: what is wrong with the file as a whole (encoding, quoting,
 * header, size) is an {@link ApiException}; what is wrong with a row is left to whoever reads its
 * values.
 */
public final class GameCsv {

    public static final String PLAYED_ON = "playedOn";
    public static final String PLAYED_AT = "playedAt";
    public static final String ROOM = "room";
    public static final String CURRENCY = "currency";
    public static final String GAME_TYPE = "gameType";
    public static final String VARIANT = "variant";
    public static final String MODALITY = "modality";
    public static final String NAME = "name";
    public static final String BUY_IN = "buyIn";
    public static final String ENTRIES = "entries";
    public static final String PRIZE = "prize";
    public static final String BOUNTY = "bounty";
    public static final String TICKET_PRIZE_VALUE = "ticketPrizeValue";
    public static final String TICKET_DESCRIPTION = "ticketDescription";
    public static final String PAID_WITH_TICKET = "paidWithTicket";
    public static final String NOTES = "notes";
    public static final String TAGS = "tags";

    /** Every column of the format, in the order of the documentation. */
    public static final List<String> COLUMNS = List.of(PLAYED_ON, PLAYED_AT, ROOM, CURRENCY, GAME_TYPE, VARIANT, MODALITY,
            NAME, BUY_IN, ENTRIES, PRIZE, BOUNTY, TICKET_PRIZE_VALUE, TICKET_DESCRIPTION, PAID_WITH_TICKET, NOTES, TAGS);

    /** Separates the tags of a game in its cell; a tag cannot contain it. */
    static final String TAG_SEPARATOR = String.valueOf(TagName.SEPARATOR);

    /** The columns a file must have; the others are optional. */
    static final List<String> REQUIRED_COLUMNS = List.of(PLAYED_ON, ROOM, GAME_TYPE, BUY_IN);

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            // Empty lines are read, so that rows keep the number they have in a spreadsheet.
            .setIgnoreEmptyLines(false)
            .get();

    private GameCsv() {
    }

    /**
     * One game of the file.
     *
     * @param number     its row in a spreadsheet: the header is row 1
     * @param values     the values that are not blank, stripped, by column
     * @param wellFormed it has as many values as the header has columns
     */
    record Row(int number, Map<String, String> values, boolean wellFormed) {

        @Nullable String get(String column) {
            return values.get(column);
        }
    }

    /** The tags of a cell: separated by semicolons, stripped, the empty ones left out. */
    static List<String> tags(@Nullable String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(TAG_SEPARATOR)).map(String::strip).filter(tag -> !tag.isEmpty()).toList();
    }

    /** The rows of the file that have something in them, in their order. */
    static List<Row> read(byte[] content, int maxRows) {
        String text = decode(content);
        if (text.isBlank()) {
            throw new ApiException(ErrorCode.IMPORT_FILE_EMPTY);
        }
        List<Row> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), FORMAT)) {
            List<String> header = null;
            try {
                for (CSVRecord csvRecord : parser) {
                    if (header == null) {
                        header = header(csvRecord);
                        continue;
                    }
                    Row row = row((int) csvRecord.getRecordNumber(), header, csvRecord);
                    if (row != null) {
                        if (rows.size() == maxRows) {
                            throw new ApiException(ErrorCode.IMPORT_TOO_MANY_ROWS, maxRows);
                        }
                        rows.add(row);
                    }
                }
            } catch (UncheckedIOException | IllegalStateException ex) {
                // A quote that is never closed, or text after a closing quote.
                throw new ApiException(ErrorCode.IMPORT_FILE_MALFORMED, parser.getCurrentLineNumber());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.IMPORT_FILE_EMPTY);
        }
        return rows;
    }

    private static String decode(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            // The byte order mark that spreadsheets write is not part of the first column name.
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException ex) {
            throw new ApiException(ErrorCode.IMPORT_FILE_NOT_UTF8);
        }
    }

    private static List<String> header(CSVRecord csvRecord) {
        List<String> header = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : csvRecord) {
            String column = value.strip();
            if (!COLUMNS.contains(column)) {
                throw new ApiException(ErrorCode.IMPORT_UNKNOWN_COLUMN, column);
            }
            if (!seen.add(column)) {
                throw new ApiException(ErrorCode.IMPORT_DUPLICATE_COLUMN, column);
            }
            header.add(column);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!seen.contains(required)) {
                throw new ApiException(ErrorCode.IMPORT_MISSING_COLUMN, required);
            }
        }
        return header;
    }

    /** The row of a record, or nothing for an empty line or a row of empty cells. */
    private static @Nullable Row row(int recordNumber, List<String> header, CSVRecord csvRecord) {
        Map<String, String> values = new HashMap<>();
        boolean blank = true;
        for (int i = 0; i < csvRecord.size(); i++) {
            String value = csvRecord.get(i).strip();
            if (!value.isEmpty()) {
                blank = false;
                if (i < header.size()) {
                    values.put(header.get(i), value);
                }
            }
        }
        if (blank) {
            return null;
        }
        return new Row(recordNumber, values, csvRecord.size() == header.size());
    }

    /**
     * Writes games as a file of this format, with every column: importing what it writes records
     * the same games. Rooms go by name with their currency, variants by code (built-in) or name.
     */
    public static final class Writer {

        private final CSVPrinter printer;

        /** Starts the file: writes the header row. */
        public Writer(Appendable out) throws IOException {
            printer = new CSVPrinter(out, FORMAT);
            printer.printRecord(COLUMNS);
        }

        public void write(GameResponse game) throws IOException {
            VariantRef variant = game.variant();
            Map<String, Object> values = new HashMap<>();
            values.put(PLAYED_ON, game.playedOn());
            values.put(PLAYED_AT, game.playedAt());
            values.put(ROOM, game.room().name());
            values.put(CURRENCY, game.currencyCode());
            values.put(GAME_TYPE, game.gameType());
            values.put(VARIANT, variant == null ? null : variant.code() != null ? variant.code() : variant.name());
            values.put(MODALITY, game.modality());
            values.put(NAME, game.name());
            values.put(BUY_IN, game.buyIn().toPlainString());
            values.put(ENTRIES, game.entries());
            values.put(PRIZE, game.prize().toPlainString());
            values.put(BOUNTY, game.bounty().toPlainString());
            values.put(TICKET_PRIZE_VALUE, game.ticketPrizeValue().toPlainString());
            values.put(TICKET_DESCRIPTION, game.ticketDescription());
            values.put(PAID_WITH_TICKET, game.paidWithTicket());
            values.put(NOTES, game.notes());
            values.put(TAGS, game.tags().isEmpty() ? null
                    : String.join(TAG_SEPARATOR, game.tags().stream().map(TagRef::name).toList()));
            // Value by value in the order of the header, so they cannot drift apart.
            for (String column : COLUMNS) {
                printer.print(values.get(column));
            }
            printer.println();
        }

        public void flush() throws IOException {
            printer.flush();
        }
    }
}
