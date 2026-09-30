package io.github.kete1987.pokerbankroll.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTests.ErrorTestController.class)
@Import(GlobalExceptionHandlerTests.ErrorTestController.class)
class GlobalExceptionHandlerTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void invalidBodyReturnsFieldViolations() {
        assertThat(mvc.post().uri("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.detail", detail -> detail.assertThat().isEqualTo("The request contains invalid data."))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("name"))
                .hasPathSatisfying("$.errors[0].code", code -> code.assertThat().isEqualTo("NotBlank"));
    }

    @Test
    void objectLevelViolationsAreReportedWithoutField() {
        assertThat(mvc.post().uri("/test/range").contentType(MediaType.APPLICATION_JSON).content("{\"min\":5,\"max\":1}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.errors.length()", size -> size.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isNull())
                .hasPathSatisfying("$.errors[0].code", code -> code.assertThat().isEqualTo("OrderedRange"))
                .hasPathSatisfying("$.errors[0].message", message -> message.assertThat().isEqualTo("min must not exceed max"));
    }

    @Test
    void detailIsLocalizedFromAcceptLanguage() {
        assertThat(mvc.post().uri("/test/body").header("Accept-Language", "es-ES")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("La petición contiene datos no válidos.");
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertThat(mvc.get().uri("/test/api-error").header("Accept-Language", "fr"))
                .bodyJson().extractingPath("$.detail").isEqualTo("The requested resource was not found.");
    }

    @Test
    void invalidRequestParamReturnsFieldViolations() {
        assertThat(mvc.get().uri("/test/param").param("page", "-1"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("page"))
                .hasPathSatisfying("$.errors[0].code", code -> code.assertThat().isEqualTo("Min"));
    }

    /**
     * Spring 7.0 only raises method validation errors when a parameter itself is invalid
     * ({@code MethodValidationResult#hasErrors} ignores cross-parameter results), so both kinds fail here.
     */
    @Test
    void crossParameterViolationsAreReportedWithoutField() {
        assertThat(mvc.get().uri("/test/cross-param").param("from", "-1").param("to", "-5"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.errors.length()", size -> size.assertThat().isEqualTo(2))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isNull())
                .hasPathSatisfying("$.errors[0].code", code -> code.assertThat().isEqualTo("OrderedParams"))
                .hasPathSatisfying("$.errors[0].message", message -> message.assertThat().isEqualTo("from must not be after to"))
                .hasPathSatisfying("$.errors[1].field", field -> field.assertThat().isEqualTo("from"))
                .hasPathSatisfying("$.errors[1].code", code -> code.assertThat().isEqualTo("Min"));
    }

    @Test
    void invalidReturnValueIsAnInternalError() {
        assertThat(mvc.get().uri("/test/invalid-return"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INTERNAL_ERROR"))
                .doesNotHavePath("$.errors");
    }

    @Test
    void malformedJsonReturnsMalformedRequest() {
        assertThat(mvc.post().uri("/test/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void apiExceptionUsesItsCodeAndStatus() {
        assertThat(mvc.get().uri("/test/api-error"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void unexpectedExceptionReturnsInternalError() {
        assertThat(mvc.get().uri("/test/boom"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void wrongMethodReturnsMethodNotAllowed() {
        assertThat(mvc.delete().uri("/test/boom"))
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    record NamedBody(@NotBlank String name) {
    }

    @OrderedRange
    record Range(int min, int max) {
    }

    /** Class-level constraint: its violation is a global error, not bound to any field. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OrderedRangeValidator.class)
    @interface OrderedRange {
        String message() default "min must not exceed max";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class OrderedRangeValidator implements ConstraintValidator<OrderedRange, Range> {

        @Override
        public boolean isValid(Range range, ConstraintValidatorContext context) {
            return range == null || range.min() <= range.max();
        }
    }

    /** Cross-parameter constraint: validates several method parameters together. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OrderedParamsValidator.class)
    @interface OrderedParams {
        String message() default "from must not be after to";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    static class OrderedParamsValidator implements ConstraintValidator<OrderedParams, Object[]> {

        @Override
        public boolean isValid(Object[] args, ConstraintValidatorContext context) {
            return (int) args[0] <= (int) args[1];
        }
    }

    @RestController
    static class ErrorTestController {

        @OrderedParams
        @GetMapping("/test/cross-param")
        int crossParam(@RequestParam @Min(0) int from, @RequestParam int to) {
            return to - from;
        }

        @GetMapping("/test/invalid-return")
        @Min(10)
        int invalidReturn() {
            return 1;
        }

        @PostMapping("/test/body")
        NamedBody body(@Valid @RequestBody NamedBody body) {
            return body;
        }

        @PostMapping("/test/range")
        Range range(@Valid @RequestBody Range range) {
            return range;
        }

        @GetMapping("/test/param")
        int param(@RequestParam @Min(0) int page) {
            return page;
        }

        @GetMapping("/test/api-error")
        void apiError() {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
