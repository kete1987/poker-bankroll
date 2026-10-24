package io.github.kete1987.pokerbankroll.mcp;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poker-bankroll.mcp")
record McpProperties(boolean enabled, String token, List<String> allowedOrigins) {

    McpProperties {
        token = token == null ? "" : token;
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (enabled && !token.matches("[A-Za-z0-9._~-]{32,256}")) {
            throw new IllegalArgumentException("MCP requires a random token of 32 to 256 URL-safe characters");
        }
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("MCP allowed origins must be exact http(s) origins without a path");
            }
        }
    }

    @Override
    public String toString() {
        return "McpProperties[enabled=" + enabled + ", token=[redacted], allowedOrigins=" + allowedOrigins + "]";
    }
}
