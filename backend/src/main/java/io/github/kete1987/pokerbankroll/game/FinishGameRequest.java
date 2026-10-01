package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Result of a game in play. Everything is optional: an empty result means nothing was won. */
@TicketDescriptionNeedsValue
public record FinishGameRequest(
        @Schema(description = "Cash won, bounties apart. Cash game: amount when leaving the table. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal prize,

        @Schema(description = "Bounties won. Not for cash games. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal bounty,

        @Schema(description = "Value of a tournament ticket won. Not for cash games. Defaults to 0")
        @Nullable @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal ticketPrizeValue,

        @Nullable @Size(max = 150) String ticketDescription) implements TicketPrize {

    static final FinishGameRequest NOTHING_WON = new FinishGameRequest(null, null, null, null);

    BigDecimal prizeOrZero() {
        return prize == null ? BigDecimal.ZERO : prize;
    }

    BigDecimal bountyOrZero() {
        return bounty == null ? BigDecimal.ZERO : bounty;
    }

    @Override
    public BigDecimal ticketPrizeValueOrZero() {
        return ticketPrizeValue == null ? BigDecimal.ZERO : ticketPrizeValue;
    }

    boolean hasTicketOrBounty() {
        return bountyOrZero().signum() > 0 || ticketPrizeValueOrZero().signum() > 0
                || (ticketDescription != null && !ticketDescription.isBlank());
    }
}
