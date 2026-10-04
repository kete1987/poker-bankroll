package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.tag.Tag;
import io.github.kete1987.pokerbankroll.tag.TagRef;
import io.github.kete1987.pokerbankroll.variant.Variant;
import org.jspecify.annotations.Nullable;

public record GameResponse(
        long id,
        LocalDate playedOn,
        @Schema(type = "string", example = "21:30:00") @Nullable LocalTime playedAt,
        RoomRef room,
        GameType gameType,
        Modality modality,
        @Nullable VariantRef variant,
        @Schema(description = "IN_PLAY while the result is not known yet; its buy-in already counts in net")
        GameStatus status,
        @Nullable String name,
        @Schema(description = "Currency of every amount of this game (the one of its room)")
        String currencyCode,
        BigDecimal buyIn,
        int entries,
        BigDecimal prize,
        BigDecimal bounty,
        BigDecimal ticketPrizeValue,
        @Nullable String ticketDescription,
        boolean paidWithTicket,
        @Schema(description = "Money paid for the entries (an entry paid with a ticket costs nothing)")
        BigDecimal invested,
        @Schema(description = "Money won: prize + bounty")
        BigDecimal won,
        @Schema(description = "Real money won or lost: won - invested")
        BigDecimal net,
        @Nullable String notes,
        @Schema(description = "Its tags, by name ignoring case")
        List<TagRef> tags,
        Instant createdAt,
        Instant updatedAt) {

    public record RoomRef(long id, String name) {
    }

    /** Either {@code code} (built-in, translated by the client) or {@code name} (user-defined). */
    public record VariantRef(long id, @Nullable String code, @Nullable String name) {
    }

    static GameResponse of(Game game) {
        Variant variant = game.getVariant();
        return new GameResponse(
                game.getId(),
                game.getPlayedOn(),
                game.getPlayedAt(),
                new RoomRef(game.getRoom().getId(), game.getRoom().getName()),
                game.getGameType(),
                game.getModality(),
                variant == null ? null : new VariantRef(variant.getId(), variant.getCode(), variant.getName()),
                game.getStatus(),
                game.getName(),
                game.getRoom().getCurrencyCode(),
                game.getBuyIn(),
                game.getEntries(),
                game.getPrize(),
                game.getBounty(),
                game.getTicketPrizeValue(),
                game.getTicketDescription(),
                game.isPaidWithTicket(),
                game.getInvested(),
                game.getWon(),
                game.getNet(),
                game.getNotes(),
                game.getTags().stream().map(Tag::toRef).sorted(TagRef.BY_NAME).toList(),
                game.getCreatedAt(),
                game.getUpdatedAt());
    }
}
