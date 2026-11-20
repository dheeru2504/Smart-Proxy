package com.smartproxy.core.model;

import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the entire request-response lifecycle state across the gateway pipeline.
 */
public class GatewayContext {

    private final String traceId;
    private final Instant requestStartTime;
    private final ServerWebExchange exchange;

    private RouteDefinition matchedRoute;
    private URI targetUpstreamUri;
    private String selectedInstanceId;

    private boolean rateLimitExceeded;
    private long rateLimitRemaining;

    private boolean circuitBreakerOpen;
    private boolean authenticated;
    private String clientPrincipal;

    private int responseStatusCode;
    private long executionDurationMs;
    private Throwable error;

    private final Map<String, Object> attributes = new HashMap<>();

    public GatewayContext(String traceId, Instant requestStartTime, ServerWebExchange exchange) {
        this.traceId = traceId;
        this.requestStartTime = requestStartTime;
        this.exchange = exchange;
    }

    public String getTraceId() { return traceId; }
    public Instant getRequestStartTime() { return requestStartTime; }
    public ServerWebExchange getExchange() { return exchange; }

    public RouteDefinition getMatchedRoute() { return matchedRoute; }
    public void setMatchedRoute(RouteDefinition matchedRoute) { this.matchedRoute = matchedRoute; }

    public URI getTargetUpstreamUri() { return targetUpstreamUri; }
    public void setTargetUpstreamUri(URI targetUpstreamUri) { this.targetUpstreamUri = targetUpstreamUri; }

    public String getSelectedInstanceId() { return selectedInstanceId; }
    public void setSelectedInstanceId(String selectedInstanceId) { this.selectedInstanceId = selectedInstanceId; }

    public boolean isRateLimitExceeded() { return rateLimitExceeded; }
    public void setRateLimitExceeded(boolean rateLimitExceeded) { this.rateLimitExceeded = rateLimitExceeded; }

    public long getRateLimitRemaining() { return rateLimitRemaining; }
    public void setRateLimitRemaining(long rateLimitRemaining) { this.rateLimitRemaining = rateLimitRemaining; }

    public boolean isCircuitBreakerOpen() { return circuitBreakerOpen; }
    public void setCircuitBreakerOpen(boolean circuitBreakerOpen) { this.circuitBreakerOpen = circuitBreakerOpen; }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    public String getClientPrincipal() { return clientPrincipal; }
    public void setClientPrincipal(String clientPrincipal) { this.clientPrincipal = clientPrincipal; }

    public int getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(int responseStatusCode) { this.responseStatusCode = responseStatusCode; }

    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public Throwable getError() { return error; }
    public void setError(Throwable error) { this.error = error; }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
