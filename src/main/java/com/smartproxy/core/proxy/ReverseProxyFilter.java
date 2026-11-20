package com.smartproxy.core.proxy;

import com.smartproxy.core.foundation.TraceIdFilter;
import com.smartproxy.core.foundation.error.GatewayException;
import com.smartproxy.core.model.GatewayContext;
import com.smartproxy.core.model.RouteDefinition;
import com.smartproxy.core.proxy.retry.OutboundConcurrencyLimiter;
import com.smartproxy.core.proxy.retry.ReactiveRetryExecutor;
import com.smartproxy.core.proxy.retry.RetryPolicy;
import com.smartproxy.core.routing.PathMatcher;
import com.smartproxy.core.routing.RouteMatchingFilter;
import com.smartproxy.loadbalancer.LoadBalancerService;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/**
 * Core HTTP reverse proxy forwarding filter.
 * Executes load-balanced routing, header sanitization, streaming request/response,
 * outbound concurrency limit enforcement, and bounded idempotent retries.
 */
@Component
public class ReverseProxyFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyFilter.class);

    private final HttpProxyClient proxyClient;
    private final LoadBalancerService loadBalancerService;
    private final ReactiveRetryExecutor retryExecutor;
    private final OutboundConcurrencyLimiter concurrencyLimiter;

    public ReverseProxyFilter(HttpProxyClient proxyClient,
                              LoadBalancerService loadBalancerService,
                              ReactiveRetryExecutor retryExecutor,
                              OutboundConcurrencyLimiter concurrencyLimiter) {
        this.proxyClient = proxyClient;
        this.loadBalancerService = loadBalancerService;
        this.retryExecutor = retryExecutor;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public int getOrder() {
        // Runs at the end of the gateway filter pipeline to perform proxy forwarding
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RouteDefinition route = exchange.getAttribute(RouteMatchingFilter.MATCHED_ROUTE_ATTR);

        // If no route was matched (e.g. internal health endpoints), continue down the chain
        if (route == null) {
            return chain.filter(exchange);
        }

        String traceId = exchange.getAttributeOrDefault(TraceIdFilter.TRACE_ID_ATTR, "UNKNOWN");
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String serviceId = route.getServiceId();

        GatewayContext context = new GatewayContext(traceId, Instant.now(), exchange);
        context.setMatchedRoute(route);

        RetryPolicy retryPolicy = RetryPolicy.defaultPolicy();
        long deadlineMs = route.getResponseTimeoutMs() > 0 ? (long) route.getResponseTimeoutMs() * 2 : 10000L;

        // 1. Enforce Outbound Concurrency Limits per service
        Mono<Void> proxyOperation = retryExecutor.executeWithRetry(
                () -> loadBalancerService.choose(serviceId, null, context)
                        .flatMap(instance -> forwardToInstance(exchange, route, instance, traceId)),
                method,
                request.getHeaders(),
                retryPolicy,
                deadlineMs,
                traceId
        ).onErrorResume(throwable -> handleProxyError(throwable, traceId, serviceId));

        return concurrencyLimiter.executeWithConcurrencyLimit(serviceId, retryPolicy.maxOutboundConcurrency(), proxyOperation);
    }

    private Mono<Void> forwardToInstance(ServerWebExchange exchange, RouteDefinition route,
                                         UpstreamInstance instance, String traceId) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpMethod method = request.getMethod();

        // 2. Rewrite Path with prefix stripping
        String originalPath = request.getURI().getPath();
        String rewrittenPath = PathMatcher.stripPrefix(originalPath, route.getStripPrefixCount());

        // 3. Construct Target URI with Query Parameters
        URI targetUri = UriComponentsBuilder.fromUri(instance.getUri())
                .path(rewrittenPath)
                .query(request.getURI().getQuery())
                .build(true)
                .toUri();

        // 3a. Validate target URI against SSRF protection policy
        SsrfProtectionValidator.validateTargetUri(targetUri);

        log.debug("[{}] Forwarding {} {} -> {}", traceId, method, originalPath, targetUri);

        // 4. Sanitize and build upstream request headers
        com.smartproxy.core.auth.ClientIdentity identity = exchange.getAttribute(
                com.smartproxy.core.auth.AuthenticationFilter.AUTHENTICATED_IDENTITY_ATTR
        );
        HttpHeaders upstreamHeaders = ProxyHeaderUtils.buildUpstreamHeaders(request, targetUri, traceId, identity);

        // 5. Execute forward through tracked load-balancer execution wrapper
        int timeoutMs = route.getResponseTimeoutMs();
        Mono<HttpProxyClient.ProxyResponse> forwardMono = proxyClient.forward(
                method, targetUri, upstreamHeaders, request.getBody(), timeoutMs
        );

        return loadBalancerService.trackExecution(instance, forwardMono)
                .flatMap(proxyResponse -> {
                    response.setStatusCode(proxyResponse.statusCode());

                    // Copy sanitized response headers back to client
                    proxyResponse.headers().forEach((name, values) -> {
                        if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                            response.getHeaders().put(name, values);
                        }
                    });

                    // Ensure trace headers remain intact on response
                    response.getHeaders().set(TraceIdFilter.HEADER_REQUEST_ID, traceId);
                    response.getHeaders().set(TraceIdFilter.HEADER_GATEWAY_TRACE_ID, traceId);

                    // Stream response body back to client
                    return response.writeWith(proxyResponse.body());
                });
    }

    private Mono<Void> handleProxyError(Throwable error, String traceId, String serviceId) {
        if (error instanceof GatewayException) {
            return Mono.error(error);
        }

        if (error instanceof TimeoutException || (error.getCause() instanceof TimeoutException)) {
            log.error("[{}] Gateway timeout while communicating with upstream service '{}'", traceId, serviceId);
            return Mono.error(GatewayException.gatewayTimeout("Upstream service '" + serviceId + "' timed out."));
        }

        if (error instanceof ConnectException || (error.getCause() instanceof ConnectException)) {
            log.error("[{}] Connection refused by upstream service '{}'", traceId, serviceId);
            return Mono.error(GatewayException.badGateway("Failed to connect to upstream service '" + serviceId + "'.", error));
        }

        log.error("[{}] Unexpected reverse proxy error for service '{}'", traceId, serviceId, error);
        return Mono.error(GatewayException.badGateway("Bad gateway error while proxying to service: " + serviceId, error));
    }
}
