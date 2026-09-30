package io.github.kete1987.pokerbankroll.common.error;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} response with two extra
 * properties: {@code code} (an {@link ErrorCode}) and, for validation errors, {@code errors}
 * (a list of {@link FieldViolation}). The {@code detail} is localized from {@code Accept-Language}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ErrorCode code = ex.getCode();
        ProblemDetail problem = problem(code, code.status(), ex.getArgs());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), code.status(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = problem(code, code.status());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), code.status(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        BindingResult result = ex.getBindingResult();
        // Class-level (cross-field) constraints end up in the global errors, with no field.
        List<FieldViolation> errors = Stream.concat(
                        result.getGlobalErrors().stream().map(error -> violation(null, error, locale)),
                        result.getFieldErrors().stream().map(error -> violation(error.getField(), error, locale)))
                .toList();
        return validationFailed(ex, errors, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> violation(result.getMethodParameter().getParameterName(), error, locale)))
                .toList();
        return validationFailed(ex, errors, headers, status, request);
    }

    /** Adds {@code code} and a localized {@code detail} to errors raised by Spring MVC itself. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            ErrorCode code = ErrorCode.forStatus(statusCode);
            problem.setDetail(message(code));
            problem.setProperty("code", code.name());
        }
        return response;
    }

    private ResponseEntity<Object> validationFailed(
            Exception ex, List<FieldViolation> errors, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, status);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private ProblemDetail problem(ErrorCode code, HttpStatusCode status, Object... args) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message(code, args));
        problem.setProperty("code", code.name());
        return problem;
    }

    private String message(ErrorCode code, Object... args) {
        return messages.getMessage(code.messageKey(), args, code.name(), LocaleContextHolder.getLocale());
    }

    private FieldViolation violation(@Nullable String field, MessageSourceResolvable error, Locale locale) {
        String[] codes = error.getCodes();
        // The least specific code is the bare constraint name, e.g. "NotNull".
        String constraint = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
        return new FieldViolation(field, constraint, messages.getMessage(error, locale));
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("code");
    }
}
