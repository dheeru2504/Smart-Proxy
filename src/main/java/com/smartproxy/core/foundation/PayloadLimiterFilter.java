package com.smartproxy.core.foundation;

import com.smartproxy.core.foundation.error.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filter that protects upstream backends and gateway resources from oversized payloads.
 * Returns HTTP 413 Payload Too Large if Content-Length exceeds the maximum permitted payload size.
 */
@Component
public class PayloadLimiterFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PayloadLimiterFilter.class);

    // Default max payload: 10 Megabytes
    public static final long DEFAULT_MAX_PAYLOAD_BYTES = 10 * 1024 * 1024L;

    private final long maxPayloadBytes;

    public PayloadLimiterFilter() {
        this(DEFAULT_MAX_PAYLOAD_BYTES);
    }

    public PayloadLimiterFilter(long maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes > 0 ? maxPayloadBytes : DEFAULT_MAX_PAYLOAD_BYTES;
    }

    @Override
    public int getOrder() {
        // Runs early, right after TraceId and AccessLogging filters
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        long contentLength = headers.getContentLength();

        if (contentLength > maxPayloadBytes) {
            log.warn("Rejected oversized payload: Content-Length {} bytes > Maximum {} bytes (Path: {})",
                    contentLength, maxPayloadBytes, exchange.getRequest().getURI().getPath());
            return Mono.error(GatewayException.payloadTooLarge(
                    "Payload size (" + contentLength + " bytes) exceeds maximum limit of " + maxPayloadBytes + " bytes."
            ));
        }

        return chain.filter(exchange);
    }
}
