package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.tag.TagName;
import org.jspecify.annotations.Nullable;

/** Data to create or update a game. Amounts are in the currency of the room. */
@CashGameFields
@TicketDescriptionNeedsValue
@InPlayGameHasNoResult
public record GameRequest(
        @NotNull LocalDate playedOn,

        @Schema(description = "Optional local start time (HH:mm), to order the games of a day",
                type = "string", example = "21:30")
        @Nullable LocalTime playedAt,

        @NotNull Long roomId,

        @NotNull GameType gameType,

        @Schema(description = "Defaults to NLHE")
        @Nullable Modality modality,

        @Schema(description = "Optional; must be a variant of the game type")
        @Nullable Long variantId,

        @Schema(description = "IN_PLAY (registered when it starts, no result yet) or FINISHED. When omitted: "
                + "FINISHED if a result is sent (prize, bounty or ticket); otherwise IN_PLAY for a new game "
                + "and unchanged for an existing one")
        @Nullable GameStatus status,

        @Nullable @Size(max = 150) String name,

        @Schema(description = "Price of one entry, also when it was paid with a ticket. "
                + "Cash game: amount brought to the table")
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal buyIn,

        @Schema(description = "Entries including re-entries. Defaults to 1")
        @Nullable @Min(1) Integer entries,

        @Schema(description = "Cash won, bounties apart. Cash game: amount when leaving the table. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal prize,

        @Schema(description = "Bounties won. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal bounty,

        @Schema(description = "Value of a tournament ticket won as a prize; informative, not part of net. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal ticketPrizeValue,

        @Nullable @Size(max = 150) String ticketDescription,

        @Schema(description = "One entry was paid with a ticket instead of cash. Defaults to false")
        @Nullable Boolean paidWithTicket,

        @Nullable @Size(max = 5000) String notes,

        @Schema(description = "Names of its tags, at most " + GameRequest.MAX_TAGS + ", each from 1 to 40 characters "
                + "without commas or semicolons. Matched with the existing tags ignoring case and surrounding spaces; the "
                + "missing ones are created. Names repeated ignoring case are one tag. Omitted or null: no tags "
                + "(an update replaces the tags of the game)")
        @Nullable @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> tags) implements TicketPrize {

    /** Tags of one game at most. */
    public static final int MAX_TAGS = 10;

    /** Something was won: a prize, a bounty or a ticket. */
    boolean hasResult() {
        return prizeOrZero().signum() > 0 || bountyOrZero().signum() > 0 || hasTicketPrize();
    }

    boolean hasTicketPrize() {
        return ticketPrizeValueOrZero().signum() > 0 || (ticketDescription != null && !ticketDescription.isBlank());
    }

    @Override
    public boolean isCashGame() {
        return gameType == GameType.CASH;
    }

    int entriesOrDefault() {
        return entries == null ? 1 : entries;
    }

    BigDecimal prizeOrZero() {
        return orZero(prize);
    }

    BigDecimal bountyOrZero() {
        return orZero(bounty);
    }

    @Override
    public BigDecimal ticketPrizeValueOrZero() {
        return orZero(ticketPrizeValue);
    }

    boolean paidWithTicketOrDefault() {
        return Boolean.TRUE.equals(paidWithTicket);
    }

    Modality modalityOrDefault() {
        return modality == null ? Modality.NLHE : modality;
    }

    private static BigDecimal orZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
