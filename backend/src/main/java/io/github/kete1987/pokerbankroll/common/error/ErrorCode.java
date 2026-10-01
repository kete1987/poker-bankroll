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
    /** The room has games or bankroll movements: it cannot be deleted, only deactivated. */
    ROOM_IN_USE(HttpStatus.CONFLICT),
    /** The room has games or bankroll movements: its currency cannot change. */
    ROOM_CURRENCY_LOCKED(HttpStatus.CONFLICT),
    LOGO_EMPTY(HttpStatus.BAD_REQUEST),
    LOGO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    /** The content is not a PNG, JPEG or WebP image, whatever the declared content type. */
    LOGO_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** Not an http or https URL. */
    LOGO_URL_INVALID(HttpStatus.BAD_REQUEST),
    /** The URL points to this machine or to a private network, which is never fetched. */
    LOGO_URL_NOT_PUBLIC(HttpStatus.BAD_REQUEST),
    /** The image could not be downloaded: unknown host, timeout, an error of the other server. */
    LOGO_URL_UNREACHABLE(HttpStatus.BAD_GATEWAY),

    // Variants
    VARIANT_NAME_TAKEN(HttpStatus.CONFLICT),
    /** The variant is used by games: it cannot be deleted, only deactivated. */
    VARIANT_IN_USE(HttpStatus.CONFLICT),
    /** Built-in variants can only be activated or deactivated. */
    VARIANT_BUILT_IN(HttpStatus.CONFLICT),

    // Games
    UNKNOWN_ROOM(HttpStatus.BAD_REQUEST),
    UNKNOWN_VARIANT(HttpStatus.BAD_REQUEST),
    /** New games cannot be recorded in an inactive room (existing ones can still be edited). */
    ROOM_INACTIVE(HttpStatus.CONFLICT),
    /** New games cannot use an inactive variant (existing ones can keep it). */
    VARIANT_INACTIVE(HttpStatus.CONFLICT),
    /** The variant belongs to another game type. */
    VARIANT_GAME_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
    INVALID_SORT(HttpStatus.BAD_REQUEST),
    /** Finishing, re-entering and rebuying are only possible while the game is in play. */
    GAME_NOT_IN_PLAY(HttpStatus.CONFLICT),
    /** Cash games have no re-entries: more money at the table is a rebuy. */
    RE_ENTRY_NOT_FOR_CASH_GAMES(HttpStatus.CONFLICT),
    REBUY_ONLY_FOR_CASH_GAMES(HttpStatus.CONFLICT),
    /** A cash game finishes with a prize only: no bounties or tickets. */
    CASH_GAME_RESULT(HttpStatus.BAD_REQUEST);

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
