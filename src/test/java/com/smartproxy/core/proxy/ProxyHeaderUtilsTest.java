package com.smartproxy.core.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ProxyHeaderUtilsTest {

    @Test
    void shouldStripHopByHopHeadersAndInjectProxyHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users")
                .header("Connection", "keep-alive")
                .header("Upgrade", "websocket")
                .header("Transfer-Encoding", "chunked")
                .header("Keep-Alive", "timeout=5")
                .header("Authorization", "Bearer my-token")
                .header("User-Agent", "SmartProxy-TestClient")
                .header("Host", "gateway.example.com")
                .build();

        URI targetUri = URI.create("http://upstream-host:8080/api/v1/users");
        String traceId = "test-trace-123";

        HttpHeaders upstreamHeaders = ProxyHeaderUtils.buildUpstreamHeaders(request, targetUri, traceId);

        // Hop-by-hop headers must be stripped
        assertFalse(upstreamHeaders.containsKey("Connection"));
        assertFalse(upstreamHeaders.containsKey("Upgrade"));
        assertFalse(upstreamHeaders.containsKey("Transfer-Encoding"));
        assertFalse(upstreamHeaders.containsKey("Keep-Alive"));

        // Regular safe headers must remain
        assertEquals("Bearer my-token", upstreamHeaders.getFirst("Authorization"));
        assertEquals("SmartProxy-TestClient", upstreamHeaders.getFirst("User-Agent"));

        // Host header should be rewritten to upstream target
        assertEquals("upstream-host:8080", upstreamHeaders.getFirst("Host"));

        // Standard proxy headers must be injected
        assertEquals("gateway.example.com", upstreamHeaders.getFirst("X-Forwarded-Host"));
        assertNotNull(upstreamHeaders.getFirst("X-Forwarded-For"));
        assertEquals("http", upstreamHeaders.getFirst("X-Forwarded-Proto"));
        assertEquals("test-trace-123", upstreamHeaders.getFirst("X-Request-ID"));
        assertEquals("test-trace-123", upstreamHeaders.getFirst("X-Gateway-Trace-ID"));
    }

    @Test
    void shouldFilterResponseHopByHopHeaders() {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Connection", "close");
        responseHeaders.add("Transfer-Encoding", "chunked");
        responseHeaders.add("Content-Type", "application/json");
        responseHeaders.add("X-Custom-Header", "value123");

        HttpHeaders cleanHeaders = ProxyHeaderUtils.filterResponseHeaders(responseHeaders);

        assertFalse(cleanHeaders.containsKey("Connection"));
        assertFalse(cleanHeaders.containsKey("Transfer-Encoding"));
        assertEquals("application/json", cleanHeaders.getFirst("Content-Type"));
        assertEquals("value123", cleanHeaders.getFirst("X-Custom-Header"));
    }
}
