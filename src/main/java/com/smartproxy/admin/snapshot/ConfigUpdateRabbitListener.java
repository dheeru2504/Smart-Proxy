package com.smartproxy.admin.snapshot;

import com.smartproxy.config.RabbitMqConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Listens for CONFIG_UPDATED events broadcasted on RabbitMQ exchange.
 * Ensures that peer gateway nodes in a distributed cluster receive updates and re-sync configuration.
 */
@Component
@ConditionalOnClass(RabbitListener.class)
@ConditionalOnBean(ConnectionFactory.class)
public class ConfigUpdateRabbitListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigUpdateRabbitListener.class);

    private final GatewaySnapshotManager snapshotManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ConfigUpdateRabbitListener(GatewaySnapshotManager snapshotManager,
                                      ApplicationEventPublisher applicationEventPublisher) {
        this.snapshotManager = snapshotManager;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @RabbitListener(queues = RabbitMqConfiguration.CONFIG_QUEUE)
    public void onConfigUpdateMessage(ConfigUpdateEvent event) {
        if (event == null) return;

        long activeVersion = snapshotManager.getActiveVersion();
        log.info("Received RabbitMQ CONFIG_UPDATED event: v{} (active in-memory version: v{})",
                event.version(), activeVersion);

        if (event.version() > activeVersion) {
            log.info("Triggering snapshot synchronization for version v{}", event.version());
            if (applicationEventPublisher != null) {
                applicationEventPublisher.publishEvent(event);
            }
        }
    }
}
