package com.smartproxy.core.foundation;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Reactive WebFilter responsible for resolving, generating, and propagating distributed trace IDs.
 * Executes with highest precedence so all downstream filters and handlers have access to the trace context.
 */
@Component
public class TraceIdFilter implements WebFilter, Ordered {

    public static final String TRACE_ID_ATTR = "com.smartproxy.traceId";
    public static final String TRACE_ID_CONTEXT_KEY = "traceId";

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_TRACE_ID = "X-Trace-ID";
    public static final String HEADER_GATEWAY_TRACE_ID = "X-Gateway-Trace-ID";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest().getHeaders());

        // 1. Store in ServerWebExchange attributes for synchronous pipeline access
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);

        // 2. Attach trace headers to downstream client response
        exchange.getResponse().getHeaders().set(HEADER_REQUEST_ID, traceId);
        exchange.getResponse().getHeaders().set(HEADER_GATEWAY_TRACE_ID, traceId);

        // 3. Propagate into Reactor Context for non-blocking reactive stream consumers
        return chain.filter(exchange)
                .contextWrite(Context.of(TRACE_ID_CONTEXT_KEY, traceId));
    }

    private String resolveTraceId(HttpHeaders headers) {
        String requestId = headers.getFirst(HEADER_REQUEST_ID);
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }

        String traceId = headers.getFirst(HEADER_TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }

        // Generate high-entropy UUID
        return UUID.randomUUID().toString().replace("-", "");
    }
}
