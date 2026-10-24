package io.github.kete1987.pokerbankroll.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;

class McpDisabledApiTests extends ApiIntegrationTest {

    @Autowired ApplicationContext context;

    @Test
    void disabledByDefaultAndNoToolsOrTransportAreRegistered() {
        assertThat(mvc.get().uri("/mcp")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(postJson("/mcp", "{}")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(context.getBeansOfType(McpTools.class)).isEmpty();
        assertThat(context.getBeansOfType(io.modelcontextprotocol.server.McpStatelessSyncServer.class)).isEmpty();
    }
}
