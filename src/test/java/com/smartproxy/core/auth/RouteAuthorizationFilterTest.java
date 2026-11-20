package com.smartproxy.core.auth;

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

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteAuthorizationFilterTest {

    private RouteAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RouteAuthorizationFilter();
    }

    @Test
    void shouldAllowUnauthenticatedRequestOnPublicRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/public/catalog").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition publicRoute = RouteDefinition.builder()
                .routeId("public-route")
                .pathPattern("/public/**")
                .authRequired(false)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, publicRoute);
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, ClientIdentity.anonymous("127.0.0.1"));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void shouldRejectAnonymousRequestWhenRouteRequiresAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/secure/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition secureRoute = RouteDefinition.builder()
                .routeId("secure-route")
                .pathPattern("/secure/**")
                .authRequired(true)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, secureRoute);
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, ClientIdentity.anonymous("127.0.0.1"));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(err -> err instanceof GatewayException ge && ge.getStatus() == HttpStatus.UNAUTHORIZED)
                .verify();
    }

    @Test
    void shouldAllowAuthenticatedRequestWhenRouteRequiresAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/secure/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RouteDefinition secureRoute = RouteDefinition.builder()
                .routeId("secure-route")
                .pathPattern("/secure/**")
                .authRequired(true)
                .build();
        exchange.getAttributes().put(RouteMatchingFilter.MATCHED_ROUTE_ATTR, secureRoute);

        ClientIdentity authIdentity = ClientIdentity.authenticated(
                "user-100", "tenant-1", Set.of("USER"), Set.of("read"), AuthType.JWT, Map.of()
        );
        exchange.getAttributes().put(AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR, authIdentity);

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }
}
