package io.github.kete1987.pokerbankroll.common.error;

import org.jspecify.annotations.Nullable;

/**
 * One validation error of a request.
 *
 * @param field   field or parameter name; {@code null} for object-level (cross-field) constraints
 * @param code    constraint name (e.g. {@code NotNull}, {@code Positive}), stable for clients
 * @param message localized fallback message
 */
public record FieldViolation(@Nullable String field, String code, String message) {
}
