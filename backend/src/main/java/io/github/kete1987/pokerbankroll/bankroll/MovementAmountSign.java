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
 * The amount of a movement is greater than zero, its type giving the direction; only an adjustment
 * can be negative, and none can be zero.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MovementAmountSign.Validator.class)
public @interface MovementAmountSign {

    String message() default "must be greater than zero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MovementAmountSign, MovementRequest> {

        @Override
        public boolean isValid(MovementRequest movement, ConstraintValidatorContext context) {
            if (movement.amount() == null) {
                return true;
            }
            int sign = movement.amount().signum();
            if (sign > 0 || (sign < 0 && movement.type() == MovementType.ADJUSTMENT)) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("amount")
                    .addConstraintViolation();
            return false;
        }
    }
}
