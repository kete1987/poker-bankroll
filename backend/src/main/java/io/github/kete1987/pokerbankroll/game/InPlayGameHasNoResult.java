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

/**
 * A game explicitly sent as {@code IN_PLAY} cannot carry a result (prize, bounty or ticket). Each
 * offending field gets its own violation.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InPlayGameHasNoResult.Validator.class)
public @interface InPlayGameHasNoResult {

    String message() default "is not allowed while the game is in play";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<InPlayGameHasNoResult, GameRequest> {

        @Override
        public boolean isValid(GameRequest game, ConstraintValidatorContext context) {
            if (game.status() != GameStatus.IN_PLAY) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            valid &= allowed(game.prizeOrZero().signum() == 0, "prize", context);
            valid &= allowed(game.bountyOrZero().signum() == 0, "bounty", context);
            valid &= allowed(game.ticketPrizeValueOrZero().signum() == 0, "ticketPrizeValue", context);
            valid &= allowed(game.ticketDescription() == null || game.ticketDescription().isBlank(),
                    "ticketDescription", context);
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
