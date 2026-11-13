package io.github.kete1987.pokerbankroll.backup;

import java.time.Instant;
import java.util.List;

import io.github.kete1987.pokerbankroll.backup.BackupData.TagData;
import io.github.kete1987.pokerbankroll.tag.TagColor;
import org.jspecify.annotations.Nullable;

/** Format 2 adds a catalog of tags and colors. The other representations stay identical to format 1. */
final class BackupV2 {
    static final int VERSION = 2;

    private BackupV2() {
    }

    record File(
            @Nullable Integer formatVersion,
            @Nullable String appVersion,
            @Nullable Instant exportedAt,
            @Nullable List<BackupV1.@Nullable Room> rooms,
            @Nullable List<BackupV1.@Nullable Variant> variants,
            @Nullable List<BackupV1.@Nullable Game> games,
            @Nullable List<BackupV1.@Nullable Movement> movements,
            @Nullable List<BackupV1.@Nullable Template> templates,
            @Nullable String baseCurrencyCode,
            @Nullable List<BackupV1.@Nullable ExchangeRate> exchangeRates,
            @Nullable List<@Nullable Tag> tags) {

        private BackupV1.File legacyFields() {
            return new BackupV1.File(BackupV1.VERSION, appVersion, exportedAt, rooms, variants, games,
                    movements, templates, baseCurrencyCode, exchangeRates);
        }

        static File of(BackupData data) {
            BackupV1.File legacy = BackupV1.File.of(data);
            return new File(VERSION, data.appVersion(), data.exportedAt(), legacy.rooms(), legacy.variants(),
                    legacy.games(), legacy.movements(), legacy.templates(), data.baseCurrencyCode(),
                    legacy.exchangeRates(), data.tags().stream().map(tag -> new Tag(tag.name(), tag.color())).toList());
        }

        @Nullable String missingList() {
            String missing = legacyFields().missingList();
            return missing != null ? missing : tags == null ? "tags" : null;
        }

        BackupData toData() {
            BackupData legacy = legacyFields().toData();
            return new BackupData(VERSION, appVersion, exportedAt, legacy.rooms(), legacy.variants(), legacy.games(),
                    legacy.movements(), legacy.templates(), baseCurrencyCode, legacy.exchangeRates(),
                    tags.stream().map(tag -> tag == null ? null : new TagData(tag.name(), tag.color())).toList());
        }
    }

    record Tag(@Nullable String name, @Nullable TagColor color) {
    }
}
