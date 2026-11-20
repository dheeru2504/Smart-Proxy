package com.smartproxy.telemetry;

import reactor.core.publisher.Mono;

/**
 * Contract for asynchronous telemetry and audit logging publishing via RabbitMQ.
 */
public interface TelemetryPublisher {

    /**
     * Asynchronously publishes a gateway access event to RabbitMQ.
     */
    Mono<Void> publishAccessEvent(GatewayAccessEvent event);

    /**
     * Asynchronously publishes a security or operational alert.
     */
    Mono<Void> publishSecurityAlert(String topic, MapEvent alertEvent);

    record MapEvent(String alertType, String details, long timestamp) {}
}
