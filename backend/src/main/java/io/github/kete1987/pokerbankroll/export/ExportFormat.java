package io.github.kete1987.pokerbankroll.export;

/** The kind of file an export is written as. */
public enum ExportFormat {

    /** Plain data, with codes: for games, the format of the import. */
    CSV("csv", "text/csv;charset=UTF-8"),
    /** An Excel workbook made to be read: typed cells and texts in the language of the request. */
    XLSX("xlsx", ExportFormat.XLSX_TYPE);

    static final String CSV_TYPE = "text/csv";
    static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }
}
