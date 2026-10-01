package io.github.kete1987.pokerbankroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps {@code frontend/openapi.json}, from which the frontend types are generated, equal to the
 * contract the API really serves. After changing the API, update the file with
 * {@code ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true} and regenerate the types
 * ({@code npm run api:types} in {@code frontend/}).
 */
class OpenApiContractTests extends ApiIntegrationTest {

    /** Maven runs the tests from {@code backend/}. */
    private static final Path CONTRACT = Path.of("..", "frontend", "openapi.json");

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Test
    void theCommittedContractIsTheOneServed() throws IOException {
        String served = servedContract();
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, served, StandardCharsets.UTF_8);
        }

        assertThat(CONTRACT).as("contract file, relative to backend/").exists();
        assertThat(Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("frontend/openapi.json is out of date: run ./mvnw test -Dtest=OpenApiContractTests "
                        + "-Dopenapi.update=true, then npm run api:types in frontend/")
                .isEqualTo(served);
    }

    /** The served document in a stable form: sorted keys, and without what depends on the build or the host. */
    private String servedContract() throws IOException {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        Map<String, Object> document = JSON.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() { });

        document.remove("servers");
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) document.get("info");
        info.remove("version");

        return JSON.writeValueAsString(document).replace("\r\n", "\n") + "\n";
    }
}
