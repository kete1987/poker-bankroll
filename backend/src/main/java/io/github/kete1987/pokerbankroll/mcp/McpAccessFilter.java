package io.github.kete1987.pokerbankroll.mcp;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Applies only to MCP, before the SDK reads a body or enumerates tools. No cookies or query tokens. */
final class McpAccessFilter extends OncePerRequestFilter {

    static final int MAX_BYTES = 64 * 1024;

    private final McpProperties properties;
    private final byte[] authorization;
    private final JsonMapper mapper;
    private final MessageSource messages;

    McpAccessFilter(McpProperties properties, JsonMapper mapper, MessageSource messages) {
        this.properties = properties;
        this.authorization = ("Bearer " + properties.token()).getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
        this.messages = messages;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!properties.enabled()) {
            reject(request, response, ErrorCode.NOT_FOUND);
            return;
        }
        var origins = Collections.list(request.getHeaders("Origin"));
        if (!origins.isEmpty() && (origins.size() != 1 || !properties.allowedOrigins().contains(origins.getFirst()))) {
            reject(request, response, ErrorCode.MCP_ORIGIN_FORBIDDEN);
            return;
        }
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1 || !MessageDigest.isEqual(authorization,
                headers.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"poker-bankroll-mcp\"");
            reject(request, response, ErrorCode.MCP_UNAUTHORIZED);
            return;
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            reject(request, response, ErrorCode.MCP_REQUEST_TOO_LARGE);
            return;
        }
        // Also bounds chunked requests and bodies that did not declare Content-Length.
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            reject(request, response, ErrorCode.MCP_REQUEST_TOO_LARGE);
            return;
        }
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "status", code.status().value(),
                "title", code.status().getReasonPhrase(), "code", code.name(),
                "detail", messages.getMessage(code.messageKey(), null, request.getLocale())));
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("MCP uses blocking request reads");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
