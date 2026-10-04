package io.github.kete1987.pokerbankroll.backup;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.github.kete1987.pokerbankroll.bankroll.MovementType;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import org.jspecify.annotations.Nullable;

/**
 * Everything an installation holds, as the application understands it today: what a backup is made
 * from and what is restored. It is not the file: each version of the format has its own records
 * (see {@link BackupV1}) that are mapped to and from these, so the restore only knows this model.
 *
 * <p>Rooms and variants have an {@code id} that only means something inside the backup: games,
 * movements and templates name them by it. Tags are not apart: each game names its own, and the
 * restore creates them. Everything can be missing, since it may come from a file: the restore
 * checks it.
 *
 * @param formatVersion version of the format of the file it was read from
 * @param appVersion    version of the application that made the backup
 */
record BackupData(
        int formatVersion,
        @Nullable String appVersion,
        @Nullable Instant exportedAt,
        List<@Nullable RoomData> rooms,
        List<@Nullable VariantData> variants,
        List<@Nullable GameData> games,
        List<@Nullable MovementData> movements,
        List<@Nullable TemplateData> templates) {

    record RoomData(
            @Nullable Long id,
            @Nullable String name,
            @Nullable String currencyCode,
            @Nullable Boolean active,
            @Nullable LogoData logo) {
    }

    /** The image of a room. The content type is informative: the format is read from the content. */
    record LogoData(@Nullable String contentType, byte @Nullable [] content) {
    }

    /**
     * A variant: a built-in one, named by its game type and {@code code}, of which only whether it
     * is active is restored, or one of the user, with its {@code name}.
     */
    record VariantData(
            @Nullable Long id,
            @Nullable GameType gameType,
            @Nullable String code,
            @Nullable String name,
            @Nullable Boolean active) {

        boolean isBuiltIn() {
            return code != null;
        }
    }

    record GameData(
            @Nullable LocalDate playedOn,
            @Nullable LocalTime playedAt,
            @Nullable Long roomId,
            @Nullable GameType gameType,
            @Nullable Modality modality,
            @Nullable Long variantId,
            @Nullable GameStatus status,
            @Nullable String name,
            @Nullable BigDecimal buyIn,
            @Nullable Integer entries,
            @Nullable BigDecimal prize,
            @Nullable BigDecimal bounty,
            @Nullable BigDecimal ticketPrizeValue,
            @Nullable String ticketDescription,
            @Nullable Boolean paidWithTicket,
            @Nullable String notes,
            @Nullable List<@Nullable String> tags) {
    }

    /** A bankroll movement: of a room, or of no room and then with its own currency. */
    record MovementData(
            @Nullable LocalDate occurredOn,
            @Nullable MovementType type,
            @Nullable Long roomId,
            @Nullable String currencyCode,
            @Nullable BigDecimal amount,
            @Nullable String notes) {
    }

    /** A template of a game played often, in a room and, optionally, of a variant. */
    record TemplateData(
            @Nullable String label,
            @Nullable Long roomId,
            @Nullable GameType gameType,
            @Nullable Modality modality,
            @Nullable Long variantId,
            @Nullable String name,
            @Nullable BigDecimal buyIn) {
    }
}
