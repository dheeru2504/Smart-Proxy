package com.smartproxy.core.foundation.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Base custom runtime exception for all Smart-Proxy domain errors.
 */
public class GatewayException extends RuntimeException {

    private final HttpStatusCode status;
    private final String errorCode;
    private final Map<String, Object> details;

    public GatewayException(HttpStatusCode status, String errorCode, String message) {
        this(status, errorCode, message, null, null);
    }

    public GatewayException(HttpStatusCode status, String errorCode, String message, Throwable cause) {
        this(status, errorCode, message, null, cause);
    }

    public GatewayException(HttpStatusCode status, String errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatusCode getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getDetails() { return details; }

    public static GatewayException notFound(String message) {
        return new GatewayException(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", message);
    }

    public static GatewayException badRequest(String message) {
        return new GatewayException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    public static GatewayException unauthorized(String message) {
        return new GatewayException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static GatewayException forbidden(String message) {
        return new GatewayException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static GatewayException rateLimitExceeded(String message) {
        return new GatewayException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
    }

    public static GatewayException badGateway(String message, Throwable cause) {
        return new GatewayException(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", message, cause);
    }

    public static GatewayException serviceUnavailable(String message) {
        return new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    public static GatewayException gatewayTimeout(String message) {
        return new GatewayException(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", message);
    }

    public static GatewayException payloadTooLarge(String message) {
        return new GatewayException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
    }
}
