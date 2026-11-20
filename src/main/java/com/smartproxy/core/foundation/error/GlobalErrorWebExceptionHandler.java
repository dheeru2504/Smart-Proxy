package com.smartproxy.core.foundation.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartproxy.core.foundation.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/**
 * Global reactive error handler translating exceptions into structured RFC 7807 problem details JSON.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String traceId = exchange.getAttributeOrDefault(TraceIdFilter.TRACE_ID_ATTR, "UNKNOWN");
        String path = exchange.getRequest().getURI().getPath();

        GatewayErrorResponse errorResponse = buildErrorResponse(ex, path, traceId);

        HttpStatusCode httpStatus = HttpStatusCode.valueOf(errorResponse.status());
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(TraceIdFilter.HEADER_REQUEST_ID, traceId);
        response.getHeaders().set(TraceIdFilter.HEADER_GATEWAY_TRACE_ID, traceId);

        log.error("[{}] Error processing request {} {}: status={}, code={}, message={}",
                traceId, exchange.getRequest().getMethod(), path,
                errorResponse.status(), errorResponse.code(), errorResponse.message(), ex);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            bytes = "{\"status\":500,\"message\":\"Internal Error\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private GatewayErrorResponse buildErrorResponse(Throwable ex, String path, String traceId) {
        if (ex instanceof GatewayException ge) {
            return GatewayErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(ge.getStatus().value())
                    .error(resolveErrorPhrase(ge.getStatus()))
                    .code(ge.getErrorCode())
                    .message(ge.getMessage())
                    .path(path)
                    .traceId(traceId)
                    .details(ge.getDetails())
                    .build();
        }

        if (ex instanceof ResponseStatusException rse) {
            return GatewayErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(rse.getStatusCode().value())
                    .error(resolveErrorPhrase(rse.getStatusCode()))
                    .code(rse.getStatusCode().toString())
                    .message(rse.getReason() != null ? rse.getReason() : rse.getMessage())
                    .path(path)
                    .traceId(traceId)
                    .build();
        }

        if (ex instanceof TimeoutException) {
            return GatewayErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.GATEWAY_TIMEOUT.value())
                    .error(HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase())
                    .code("GATEWAY_TIMEOUT")
                    .message("The upstream server took too long to respond.")
                    .path(path)
                    .traceId(traceId)
                    .build();
        }

        if (ex instanceof ConnectException) {
            return GatewayErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.BAD_GATEWAY.value())
                    .error(HttpStatus.BAD_GATEWAY.getReasonPhrase())
                    .code("UPSTREAM_CONNECTION_REFUSED")
                    .message("Failed to establish connection to upstream server.")
                    .path(path)
                    .traceId(traceId)
                    .build();
        }

        return GatewayErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .code("INTERNAL_GATEWAY_ERROR")
                .message(ex.getMessage() != null ? ex.getMessage() : "An unexpected internal gateway error occurred.")
                .path(path)
                .traceId(traceId)
                .build();
    }

    private String resolveErrorPhrase(HttpStatusCode statusCode) {
        if (statusCode instanceof HttpStatus hs) {
            return hs.getReasonPhrase();
        }
        return "HTTP " + statusCode.value();
    }
}
