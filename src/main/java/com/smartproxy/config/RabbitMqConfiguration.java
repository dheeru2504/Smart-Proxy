package com.smartproxy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares RabbitMQ exchanges, queues, and bindings automatically if they do not exist.
 * Ensures that gateway configuration sync and telemetry queues are automatically provisioned on boot.
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMqConfiguration {

    // Configuration Sync Messaging
    public static final String CONFIG_EXCHANGE = "smartproxy.config.exchange";
    public static final String CONFIG_QUEUE = "smartproxy.config.queue";
    public static final String CONFIG_ROUTING_KEY = "config.updated";

    // Telemetry & Access Log Messaging
    public static final String TELEMETRY_EXCHANGE = "smartproxy.telemetry.exchange";
    public static final String TELEMETRY_QUEUE = "smartproxy.telemetry.queue";
    public static final String TELEMETRY_ROUTING_KEY = "telemetry.#";

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // =========================================================================
    // 1. Config Exchange, Queue & Binding (Auto-created if not exists)
    // =========================================================================

    @Bean
    public TopicExchange configExchange() {
        return ExchangeBuilder.topicExchange(CONFIG_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue configQueue() {
        return QueueBuilder.durable(CONFIG_QUEUE)
                .build();
    }

    @Bean
    public Binding configBinding(Queue configQueue, TopicExchange configExchange) {
        return BindingBuilder.bind(configQueue)
                .to(configExchange)
                .with(CONFIG_ROUTING_KEY);
    }

    // =========================================================================
    // 2. Telemetry Exchange, Queue & Binding (Auto-created if not exists)
    // =========================================================================

    @Bean
    public TopicExchange telemetryExchange() {
        return ExchangeBuilder.topicExchange(TELEMETRY_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue telemetryQueue() {
        return QueueBuilder.durable(TELEMETRY_QUEUE)
                .build();
    }

    @Bean
    public Binding telemetryBinding(Queue telemetryQueue, TopicExchange telemetryExchange) {
        return BindingBuilder.bind(telemetryQueue)
                .to(telemetryExchange)
                .with(TELEMETRY_ROUTING_KEY);
    }
}
