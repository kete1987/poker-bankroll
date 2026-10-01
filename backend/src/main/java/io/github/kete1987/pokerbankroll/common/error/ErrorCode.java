package io.github.kete1987.pokerbankroll.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable error codes returned in the {@code code} property of every error response.
 * Clients translate them; the {@code detail} message is only a localized fallback,
 * resolved from {@code messages*.properties} with the key {@code error.<CODE>}.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REQUEST_FAILED(HttpStatus.BAD_REQUEST),
    /** Generic conflict with the stored data (e.g. a database constraint), when no specific code applies. */
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // Catalog
    UNKNOWN_CURRENCY(HttpStatus.BAD_REQUEST),

    // Rooms
    ROOM_NAME_TAKEN(HttpStatus.CONFLICT),
    /** The room has games: it cannot be deleted, only deactivated. */
    ROOM_IN_USE(HttpStatus.CONFLICT),
    /** The room has games: its currency cannot change. */
    ROOM_CURRENCY_LOCKED(HttpStatus.CONFLICT),

    // Variants
    VARIANT_NAME_TAKEN(HttpStatus.CONFLICT),
    /** The variant is used by games: it cannot be deleted, only deactivated. */
    VARIANT_IN_USE(HttpStatus.CONFLICT),
    /** Built-in variants can only be activated or deactivated. */
    VARIANT_BUILT_IN(HttpStatus.CONFLICT),

    // Games
    UNKNOWN_ROOM(HttpStatus.BAD_REQUEST),
    UNKNOWN_VARIANT(HttpStatus.BAD_REQUEST),
    /** The variant belongs to another game type. */
    VARIANT_GAME_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
    INVALID_SORT(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return "error." + name();
    }

    /** Code for errors raised by the framework itself, where only the HTTP status is known. */
    static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? REQUEST_FAILED : INTERNAL_ERROR;
        };
    }
}
