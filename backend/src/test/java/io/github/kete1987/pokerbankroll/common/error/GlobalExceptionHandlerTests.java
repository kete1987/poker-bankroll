package io.github.kete1987.pokerbankroll.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

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

    record Payload(@NotBlank String name) {
    }

    @RestController
    static class ErrorTestController {

        @PostMapping("/test/body")
        Payload body(@Valid @RequestBody Payload payload) {
            return payload;
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
