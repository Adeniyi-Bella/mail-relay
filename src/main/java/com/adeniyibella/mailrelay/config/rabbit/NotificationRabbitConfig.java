package com.adeniyibella.mailrelay.config.rabbit;

import com.adeniyibella.mailrelay.properties.NotificationProperties;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/**
 * Declares the notification exchange, queue, and binding.
 * The queue is automatically wired to the global DLQ on failure.
 */
@Configuration
public class NotificationRabbitConfig extends RabbitBaseClass {

    public static final String NOTIFICATION_EXCHANGE    = "notifications.exchange";
    public static final String NOTIFICATION_QUEUE       = "notifications.email.queue";
    public static final String NOTIFICATION_ROUTING_KEY = "notifications.email";

    private final NotificationProperties properties;

    public NotificationRabbitConfig(NotificationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Declarables notificationTopology() {
        return buildTopology(NOTIFICATION_EXCHANGE, NOTIFICATION_QUEUE, NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public TaskExecutor notificationTaskExecutor() {
        return buildVirtualTaskExecutor("notif-listener-");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        NotificationProperties.Rabbit rabbit = properties.rabbit();
        return buildRetryingFactory(
                connectionFactory,
                messageConverter,
                notificationTaskExecutor(),
                rabbit.concurrency(),
                rabbit.maxConcurrency());
    }
}