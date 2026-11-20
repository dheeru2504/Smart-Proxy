package com.smartproxy.admin.snapshot;

import java.io.Serializable;
import java.time.Instant;

/**
 * Message payload published to RabbitMQ exchange when configuration is modified in PostgreSQL.
 */
public record ConfigUpdateEvent(
        long version,
        Instant publishedAt,
        String checksum,
        String reason
) implements Serializable {}
