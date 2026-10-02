package io.github.kete1987.pokerbankroll.backup;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

import io.github.kete1987.pokerbankroll.backup.BackupData.GameData;
import io.github.kete1987.pokerbankroll.backup.BackupData.LogoData;
import io.github.kete1987.pokerbankroll.backup.BackupData.MovementData;
import io.github.kete1987.pokerbankroll.backup.BackupData.RoomData;
import io.github.kete1987.pokerbankroll.backup.BackupData.VariantData;
import io.github.kete1987.pokerbankroll.bankroll.MovementType;
import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.game.GameStatus;
import org.jspecify.annotations.Nullable;

/**
 * Version 1 of the format of a backup file: the JSON document, component by component. These
 * records are that format and <strong>never change</strong> once released: a file written today
 * must be readable for ever. A change of the format is a new class ({@code BackupV2}) with its own
 * records and its own mapping to {@link BackupData}, next to this one, and a new case in
 * {@link BackupFormat#read}.
 *
 * <p>Everything is nullable because it is read from a file; what is missing is reported by the
 * restore. Properties the records do not have are ignored when reading.
 */
final class BackupV1 {

    static final int VERSION = 1;

    private BackupV1() {
    }

    record File(
            @Nullable Integer formatVersion,
            @Nullable String appVersion,
            @Nullable Instant exportedAt,
            @Nullable List<@Nullable Room> rooms,
            @Nullable List<@Nullable Variant> variants,
            @Nullable List<@Nullable Game> games,
            @Nullable List<@Nullable Movement> movements) {

        static File of(BackupData data) {
            return new File(VERSION, data.appVersion(), data.exportedAt(),
                    map(data.rooms(), Room::of),
                    map(data.variants(), Variant::of),
                    map(data.games(), Game::of),
                    map(data.movements(), Movement::of));
        }

        /** The lists must be there, even empty: a document without them is not a backup. */
        @Nullable String missingList() {
            if (rooms == null) {
                return "rooms";
            }
            if (variants == null) {
                return "variants";
            }
            if (games == null) {
                return "games";
            }
            return movements == null ? "movements" : null;
        }

        BackupData toData() {
            return new BackupData(VERSION, appVersion, exportedAt,
                    map(rooms, Room::toData),
                    map(variants, Variant::toData),
                    map(games, Game::toData),
                    map(movements, Movement::toData));
        }
    }

    record Room(
            @Nullable Long id,
            @Nullable String name,
            @Nullable String currencyCode,
            @Nullable Boolean active,
            @Nullable Logo logo) {

        static Room of(RoomData room) {
            LogoData logo = room.logo();
            return new Room(room.id(), room.name(), room.currencyCode(), room.active(),
                    logo == null ? null : new Logo(logo.contentType(), logo.content()));
        }

        RoomData toData() {
            return new RoomData(id, name, currencyCode, active,
                    logo == null ? null : new LogoData(logo.contentType(), logo.content()));
        }
    }

    /** The image in Base64. */
    record Logo(@Nullable String contentType, byte @Nullable [] content) {
    }

    record Variant(
            @Nullable Long id,
            @Nullable GameType gameType,
            @Nullable String code,
            @Nullable String name,
            @Nullable Boolean active) {

        static Variant of(VariantData variant) {
            return new Variant(variant.id(), variant.gameType(), variant.code(), variant.name(), variant.active());
        }

        VariantData toData() {
            return new VariantData(id, gameType, code, name, active);
        }
    }

    record Game(
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
            @Nullable String notes) {

        static Game of(GameData game) {
            return new Game(game.playedOn(), game.playedAt(), game.roomId(), game.gameType(), game.modality(),
                    game.variantId(), game.status(), game.name(), game.buyIn(), game.entries(), game.prize(),
                    game.bounty(), game.ticketPrizeValue(), game.ticketDescription(), game.paidWithTicket(),
                    game.notes());
        }

        GameData toData() {
            return new GameData(playedOn, playedAt, roomId, gameType, modality, variantId, status, name, buyIn,
                    entries, prize, bounty, ticketPrizeValue, ticketDescription, paidWithTicket, notes);
        }
    }

    record Movement(
            @Nullable LocalDate occurredOn,
            @Nullable MovementType type,
            @Nullable Long roomId,
            @Nullable String currencyCode,
            @Nullable BigDecimal amount,
            @Nullable String notes) {

        static Movement of(MovementData movement) {
            return new Movement(movement.occurredOn(), movement.type(), movement.roomId(), movement.currencyCode(),
                    movement.amount(), movement.notes());
        }

        MovementData toData() {
            return new MovementData(occurredOn, type, roomId, currencyCode, amount, notes);
        }
    }

    /** Maps a list keeping its positions (they say where an error is) and its missing elements. */
    private static <T, R> List<@Nullable R> map(@Nullable List<@Nullable T> list, Function<T, R> mapper) {
        if (list == null) {
            return List.of();
        }
        return list.stream().<@Nullable R>map(one -> one == null ? null : mapper.apply(one)).toList();
    }
}
