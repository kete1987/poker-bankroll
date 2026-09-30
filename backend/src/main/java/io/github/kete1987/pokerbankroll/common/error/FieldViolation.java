package io.github.kete1987.pokerbankroll.common.error;

/**
 * One invalid field of a request.
 *
 * @param field   field or parameter name
 * @param code    constraint name (e.g. {@code NotNull}, {@code Positive}), stable for clients
 * @param message localized fallback message
 */
public record FieldViolation(String field, String code, String message) {
}
