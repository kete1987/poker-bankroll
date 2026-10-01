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

/**
 * A cash game is one sitting: a single entry and no bounties or tickets. Each offending field
 * gets its own violation, so clients can mark it.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CashGameFields.Validator.class)
public @interface CashGameFields {

    String message() default "is not allowed in a cash game";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CashGameFields, GameRequest> {

        @Override
        public boolean isValid(GameRequest game, ConstraintValidatorContext context) {
            if (game.gameType() != GameType.CASH) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            valid &= allowed(game.entriesOrDefault() == 1, "entries", context);
            valid &= allowed(game.bountyOrZero().signum() == 0, "bounty", context);
            valid &= allowed(game.ticketPrizeValueOrZero().signum() == 0, "ticketPrizeValue", context);
            valid &= allowed(!game.paidWithTicketOrDefault(), "paidWithTicket", context);
            return valid;
        }

        private static boolean allowed(boolean ok, String field, ConstraintValidatorContext context) {
            if (!ok) {
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(field)
                        .addConstraintViolation();
            }
            return ok;
        }
    }
}
