package io.github.kete1987.pokerbankroll.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.jspecify.annotations.Nullable;

/**
 * An Excel workbook with one sheet that is a table: a header row and one row per item, with cells
 * of the type of their column (dates and amounts are numbers for Excel, not text).
 *
 * @param <T> what a row is written from
 */
final class ExcelSheet<T> implements AutoCloseable {

    /** What the cells of a column hold, which decides their type and how Excel shows them. */
    enum Kind {
        /** A {@code String}. */
        TEXT(null),
        /** A {@code LocalDate}; shown with the date format of the sheet. */
        DATE(null),
        /** A {@code LocalTime}: for Excel, the fraction of a day. */
        TIME("hh:mm"),
        /** A {@code BigDecimal}, always with two decimals. */
        MONEY("#,##0.00"),
        /** An {@code Integer}. */
        INTEGER("0");

        private final @Nullable String numberFormat;

        Kind(@Nullable String numberFormat) {
            this.numberFormat = numberFormat;
        }
    }

    /**
     * A column of the table.
     *
     * @param width in characters, as Excel measures it
     * @param value the value of a row, of the type of the kind; {@code null} leaves the cell empty
     */
    record Column<T>(String header, Kind kind, double width, Function<T, @Nullable Object> value) {
    }

    private static final double SECONDS_PER_DAY = 86_400;

    private final Workbook workbook;
    private final Worksheet sheet;
    private final List<Column<T>> columns;
    private final String dateFormat;
    /** Row of the next item; the header is row 0. */
    private int row = 1;

    /**
     * @param dateFormat how dates are shown, in the notation of Excel (e.g. {@code dd/mm/yyyy})
     */
    ExcelSheet(OutputStream out, String name, String dateFormat, List<Column<T>> columns) {
        this.workbook = new Workbook(out, "poker-bankroll", "1.0");
        this.sheet = workbook.newWorksheet(name);
        this.columns = columns;
        this.dateFormat = dateFormat;
        for (int c = 0; c < columns.size(); c++) {
            sheet.value(0, c, columns.get(c).header());
            sheet.width(c, columns.get(c).width());
        }
        sheet.range(0, 0, 0, columns.size() - 1).style().bold().set();
        // The header stays in sight while the rows scroll.
        sheet.freezePane(0, 1);
    }

    void add(T item) {
        for (int c = 0; c < columns.size(); c++) {
            Object value = columns.get(c).value().apply(item);
            switch (value) {
                case null -> { }
                case String text -> sheet.value(row, c, text);
                case LocalDate date -> sheet.value(row, c, date);
                case LocalTime time -> sheet.value(row, c, time.toSecondOfDay() / SECONDS_PER_DAY);
                case BigDecimal amount -> sheet.value(row, c, amount);
                case Integer number -> sheet.value(row, c, number);
                default -> throw new IllegalArgumentException("Not a value of a cell: " + value.getClass());
            }
        }
        row++;
    }

    /** Finishes the workbook: nothing can be added afterwards. */
    @Override
    public void close() throws IOException {
        int last = row - 1;
        if (last >= 1) {
            for (int c = 0; c < columns.size(); c++) {
                Kind kind = columns.get(c).kind();
                String format = kind == Kind.DATE ? dateFormat : kind.numberFormat;
                if (format != null) {
                    sheet.range(1, c, last, c).style().format(format).set();
                }
            }
            sheet.setAutoFilter(0, 0, last, columns.size() - 1);
        }
        workbook.finish();
    }
}
