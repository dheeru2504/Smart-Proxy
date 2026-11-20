package com.smartproxy.core.proxy;

import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.routing.RouteMatchingFilter;
import com.smartproxy.loadbalancer.LoadBalancerService;
import com.smartproxy.loadbalancer.LoadBalancerStrategyFactory;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import com.smartproxy.core.proxy.retry.OutboundConcurrencyLimiter;
import com.smartproxy.core.proxy.retry.ReactiveRetryExecutor;
import com.smartproxy.loadbalancer.strategy.RoundRobinStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReverseProxyFilterTest {

    private HttpProxyClient proxyClient;
    private UpstreamPoolRegistry poolRegistry;
    private LoadBalancerService lbService;
    private ReverseProxyFilter filter;
    private UpstreamInstance testInstance;

    @BeforeEach
    void setUp() {
        proxyClient = mock(HttpProxyClient.class);
        poolRegistry = new UpstreamPoolRegistry(null);
        LoadBalancerStrategyFactory strategyFactory = new LoadBalancerStrategyFactory(List.of(new RoundRobinStrategy()));
        lbService = new LoadBalancerService(poolRegistry, strategyFactory);
        filter = new ReverseProxyFilter(proxyClient, lbService, new ReactiveRetryExecutor(), new OutboundConcurrencyLimiter());

        testInstance = new UpstreamInstance(UUID.randomUUID(), "order-service", "127.0.0.1", 8091, 1);
        poolRegistry.registerInstance("order-service", testInstance);
    }

    @Test
    void shouldForwardRequestAndStreamResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/orders/api/v1/checkout?ref=abc")
                .header("Content-Type", "application/json")
                .body("{\"item\":\"book\"}");

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("order-route")
                .pathPattern("/orders/**")
                .methods(Set.of("POST"))
                .serviceId("order-service")
                .stripPrefixCount(1)
                .responseTimeoutMs(1000)
                .enabled(true)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);

        DataBuffer buffer = new DefaultDataBufferFactory().wrap("{\"orderId\":\"123\"}".getBytes(StandardCharsets.UTF_8));
        Flux<DataBuffer> responseBody = Flux.just(buffer);
        HttpProxyClient.ProxyResponse proxyResponse = new HttpProxyClient.ProxyResponse(
                HttpStatus.CREATED,
                new HttpHeaders(),
                responseBody
        );

        when(proxyClient.forward(eq(HttpMethod.POST), any(URI.class), any(HttpHeaders.class), any(), eq(1000)))
                .thenReturn(Mono.just(proxyResponse));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.CREATED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldTranslateConnectionRefusedTo502BadGateway() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/orders/status").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("order-route")
                .pathPattern("/orders/**")
                .methods(Set.of("GET"))
                .serviceId("order-service")
                .responseTimeoutMs(1000)
                .enabled(true)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);

        when(proxyClient.forward(any(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.error(new ConnectException("Connection refused")));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.BAD_GATEWAY)
                .verify();
    }

    @Test
    void shouldTranslateTimeoutTo504GatewayTimeout() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/orders/status").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition route = RouteDefinition.builder()
                .routeId("order-route")
                .pathPattern("/orders/**")
                .methods(Set.of("GET"))
                .serviceId("order-service")
                .responseTimeoutMs(1000)
                .enabled(true)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, route);

        when(proxyClient.forward(any(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.error(new TimeoutException("Upstream did not respond in 1000ms")));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.GATEWAY_TIMEOUT)
                .verify();
    }
}
