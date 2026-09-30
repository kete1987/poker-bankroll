package io.github.kete1987.pokerbankroll.common.error;

/**
 * Business error with a stable {@link ErrorCode}. The HTTP status comes from the code;
 * {@code args} fill the placeholders of the localized message.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object[] args;

    public ApiException(ErrorCode code, Object... args) {
        super(code.name());
        this.code = code;
        this.args = args;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }
}
