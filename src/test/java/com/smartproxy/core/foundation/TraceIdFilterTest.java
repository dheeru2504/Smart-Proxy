package com.smartproxy.core.foundation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void shouldGenerateNewTraceIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> {
            String traceId = ex.getAttribute(TraceIdFilter.TRACE_ID_ATTR);
            assertNotNull(traceId);
            assertFalse(traceId.isBlank());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String headerReqId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.HEADER_REQUEST_ID);
        String headerTraceId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.HEADER_GATEWAY_TRACE_ID);

        assertNotNull(headerReqId);
        assertEquals(headerReqId, headerTraceId);
    }

    @Test
    void shouldPropagateExistingRequestId() {
        String existingId = "req-12345-abcde";
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(TraceIdFilter.HEADER_REQUEST_ID, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> {
            assertEquals(existingId, ex.getAttribute(TraceIdFilter.TRACE_ID_ATTR));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(existingId, exchange.getResponse().getHeaders().getFirst(TraceIdFilter.HEADER_REQUEST_ID));
        assertEquals(existingId, exchange.getResponse().getHeaders().getFirst(TraceIdFilter.HEADER_GATEWAY_TRACE_ID));
    }
}
