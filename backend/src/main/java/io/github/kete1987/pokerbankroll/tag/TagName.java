package io.github.kete1987.pokerbankroll.tag;

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
 * The name of a tag: from 1 to {@value #MAX_LENGTH} characters once the surrounding spaces are
 * removed, and no {@value #SEPARATOR}, which separates the tags of a game in the CSV files, nor a
 * comma, which separates them in the Excel files and ends one in the forms. A {@code null} is
 * valid: combine it with {@code @NotNull}.
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TagName.Validator.class)
public @interface TagName {

    int MAX_LENGTH = 40;
    char SEPARATOR = ';';

    String message() default "must have from 1 to 40 characters and no comma or semicolon";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TagName, String> {

        @Override
        public boolean isValid(String name, ConstraintValidatorContext context) {
            // As with the standard constraints, a missing value is left to @NotNull.
            if (name == null) {
                return true;
            }
            String stripped = name.strip();
            return !stripped.isEmpty() && stripped.length() <= MAX_LENGTH
                    && stripped.indexOf(SEPARATOR) < 0 && stripped.indexOf(',') < 0;
        }
    }
}
