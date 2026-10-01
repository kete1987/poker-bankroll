package io.github.kete1987.pokerbankroll.bankroll;

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
 * A movement belongs to a room (and is in its currency) or, without a room, says its currency:
 * exactly one of the two. With both, the currency is the offending field; with none, both are.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoomOrCurrency.Validator.class)
public @interface RoomOrCurrency {

    String message() default "give either a room or a currency";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<RoomOrCurrency, MovementRequest> {

        @Override
        public boolean isValid(MovementRequest movement, ConstraintValidatorContext context) {
            boolean hasRoom = movement.roomId() != null;
            if (hasRoom != movement.hasCurrency()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            if (!hasRoom) {
                report("roomId", context);
            }
            report("currencyCode", context);
            return false;
        }

        private static void report(String field, ConstraintValidatorContext context) {
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(field)
                    .addConstraintViolation();
        }
    }
}
