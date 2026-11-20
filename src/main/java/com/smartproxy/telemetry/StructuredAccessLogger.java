package com.smartproxy.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits structured JSON access logs for log aggregator pipelines (ELK, Datadog, CloudWatch).
 */
@Component
public class StructuredAccessLogger {

    private static final Logger log = LoggerFactory.getLogger("com.smartproxy.access");
    private final ObjectMapper objectMapper;

    public StructuredAccessLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void logAccess(AccessLogEntry entry) {
        if (entry == null) return;

        try {
            String json = objectMapper.writeValueAsString(entry);
            if (entry.statusCode() >= 500) {
                log.error(json);
            } else if (entry.statusCode() >= 400) {
                log.warn(json);
            } else {
                log.info(json);
            }
        } catch (Exception e) {
            log.info("[{}] {} {} {} {}ms - IP: {}",
                    entry.traceId(), entry.method(), entry.path(), entry.statusCode(), entry.durationMs(), entry.clientIp());
        }
    }
}
