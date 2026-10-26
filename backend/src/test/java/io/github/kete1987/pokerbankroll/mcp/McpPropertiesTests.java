package io.github.kete1987.pokerbankroll.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class McpPropertiesTests {

    @Test
    void enablingRequiresAValidTokenAndNeverPrintsIt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new McpProperties(true, "", List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new McpProperties(true, "short", List.of(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> new McpProperties(true, "x".repeat(32) + "\n", List.of(), false));
        assertThat(new McpProperties(true, McpApiTests.TOKEN, List.of(), false).toString()).doesNotContain(McpApiTests.TOKEN);
        assertThat(new McpProperties(false, null, null, false).enabled()).isFalse();
    }

    @Test
    void originsAreExactAndCannotBeWildcardsOrContainPaths() {
        for (String origin : List.of("*", "null", "https://client.example/", "https://client.example/path", "ftp://client.example")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new McpProperties(true, McpApiTests.TOKEN, List.of(origin), false));
        }
        assertThat(new McpProperties(true, McpApiTests.TOKEN, List.of("https://client.example:8443"), false).allowedOrigins())
                .containsExactly("https://client.example:8443");
    }
}
