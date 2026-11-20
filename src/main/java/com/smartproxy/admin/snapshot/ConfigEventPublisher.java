package com.smartproxy.admin.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes CONFIG_UPDATED events to RabbitMQ fanout exchange for distributed gateway nodes.
 */
@Component
public class ConfigEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigEventPublisher.class);

    public static final String CONFIG_EXCHANGE = "smartproxy.config.exchange";
    public static final String CONFIG_ROUTING_KEY = "config.updated";

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ConfigEventPublisher(@Autowired(required = false) RabbitTemplate rabbitTemplate,
                                ApplicationEventPublisher applicationEventPublisher) {
        this.rabbitTemplate = rabbitTemplate;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Publishes a CONFIG_UPDATED event to RabbitMQ and Spring Event bus.
     */
    public void publish(ConfigUpdateEvent event) {
        log.info("Broadcasting CONFIG_UPDATED event v{} (checksum: {})", event.version(), event.checksum());

        // 1. Publish to Spring ApplicationEvent (intra-JVM)
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(event);
        }

        // 2. Publish to RabbitMQ exchange (inter-node cluster)
        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(CONFIG_EXCHANGE, CONFIG_ROUTING_KEY, event);
            } catch (Exception e) {
                log.warn("Failed to publish CONFIG_UPDATED to RabbitMQ exchange '{}': {}",
                        CONFIG_EXCHANGE, e.getMessage());
            }
        }
    }
}
