package io.github.kete1987.pokerbankroll.game;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import io.github.kete1987.pokerbankroll.catalog.GameType;

/** A ticket description only makes sense when a ticket was won ({@code ticketPrizeValue > 0}). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TicketDescriptionNeedsValue.Validator.class)
public @interface TicketDescriptionNeedsValue {

    String message() default "needs a ticket prize value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TicketDescriptionNeedsValue, GameRequest> {

        @Override
        public boolean isValid(GameRequest game, ConstraintValidatorContext context) {
            // Cash games allow no ticket field at all: CashGameFields reports the description there.
            if (game.gameType() == GameType.CASH) {
                return true;
            }
            boolean hasDescription = game.ticketDescription() != null && !game.ticketDescription().isBlank();
            if (!hasDescription || game.ticketPrizeValueOrZero().signum() > 0) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("ticketDescription")
                    .addConstraintViolation();
            return false;
        }
    }
}
