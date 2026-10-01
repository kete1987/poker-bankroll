package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** A request that can carry a ticket won as a prize; lets {@link TicketDescriptionNeedsValue} check it. */
interface TicketPrize {

    BigDecimal ticketPrizeValueOrZero();

    @Nullable String ticketDescription();

    /** Cash games allow no ticket fields at all, which another rule reports. */
    default boolean isCashGame() {
        return false;
    }
}
