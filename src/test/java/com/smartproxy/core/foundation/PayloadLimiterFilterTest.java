package com.smartproxy.core.foundation;

import com.smartproxy.core.foundation.error.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class PayloadLimiterFilterTest {

    private PayloadLimiterFilter filter;

    @BeforeEach
    void setUp() {
        // 1 KB limit for testing
        filter = new PayloadLimiterFilter(1024L);
    }

    @Test
    void shouldPermitRequestsWithinPayloadLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/upload")
                .header(HttpHeaders.CONTENT_LENGTH, "512")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldRejectOversizedPayloadsWith413() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/upload")
                .header(HttpHeaders.CONTENT_LENGTH, "2048")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.PAYLOAD_TOO_LARGE)
                .verify();
    }
}
