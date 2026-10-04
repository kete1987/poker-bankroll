package io.github.kete1987.pokerbankroll.common.error;

import org.jspecify.annotations.Nullable;

/**
 * Business error with a stable {@link ErrorCode}. The HTTP status comes from the code;
 * {@code args} fill the placeholders of the localized message. In a request with several items
 * (a batch of games), {@code index} says which one caused it.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object[] args;
    private final @Nullable Integer index;

    public ApiException(ErrorCode code, Object... args) {
        this(code, null, args);
    }

    private ApiException(ErrorCode code, @Nullable Integer index, Object[] args) {
        super(code.name());
        this.code = code;
        this.index = index;
        this.args = args;
    }

    /** The same error, said of the item at this position (zero-based) of a request with several. */
    public ApiException atIndex(int index) {
        return new ApiException(code, index, args);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }

    /** Position of the item that caused it in a request with several; {@code null} otherwise. */
    public @Nullable Integer getIndex() {
        return index;
    }
}
