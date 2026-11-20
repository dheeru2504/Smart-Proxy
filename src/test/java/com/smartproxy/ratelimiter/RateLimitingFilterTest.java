package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.core.auth.AuthenticationFilter;
import com.smartproxy.core.auth.ClientIdentity;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimiter mockLimiter;
    private RateLimiterFactory limiterFactory;
    private RateLimitKeyResolver keyResolver;
    private MultiTierPolicyResolver multiTierPolicyResolver;
    private MultiTierRateLimiter multiTierRateLimiter;
    private SmartProxyProperties properties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        mockLimiter = mock(RateLimiter.class);
        when(mockLimiter.getAlgorithmType()).thenReturn(RateLimiterAlgorithmType.TOKEN_BUCKET);

        limiterFactory = new RateLimiterFactory(List.of(mockLimiter));
        keyResolver = new RateLimitKeyResolver();
        multiTierPolicyResolver = new MultiTierPolicyResolver();
        multiTierRateLimiter = mock(MultiTierRateLimiter.class);

        properties = new SmartProxyProperties();
        properties.getRatelimiter().setEnabled(true);

        filter = new RateLimitingFilter(limiterFactory, keyResolver, multiTierPolicyResolver, multiTierRateLimiter, properties);
    }

    @Test
    void shouldAllowRequestAndInjectRateLimitHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/items").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("items-route")
                .pathPattern("/api/v1/items")
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, ClientIdentity.anonymous("127.0.0.1"));

        when(multiTierRateLimiter.isAllowed(anyList()))
                .thenReturn(Mono.just(RateLimitResult.permitted(100, 95, 45, "test-key", RateLimiterAlgorithmType.TOKEN_BUCKET)));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("100", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_LIMIT));
        assertEquals("95", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_REMAINING));
        assertEquals("45", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_RESET));
    }

    @Test
    void shouldRejectRequestWith429WhenRateLimitExceeded() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/items").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("items-route")
                .pathPattern("/api/v1/items")
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, ClientIdentity.anonymous("127.0.0.1"));

        when(multiTierRateLimiter.isAllowed(anyList()))
                .thenReturn(Mono.just(RateLimitResult.throttled(100, 30, "test-key", RateLimiterAlgorithmType.TOKEN_BUCKET)));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.TOO_MANY_REQUESTS)
                .verify();

        assertEquals("100", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_LIMIT));
        assertEquals("0", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_REMAINING));
        assertEquals("30", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RATELIMIT_RESET));
        assertEquals("30", exchange.getResponse().getHeaders().getFirst(RateLimitingFilter.HEADER_RETRY_AFTER));
    }
}
