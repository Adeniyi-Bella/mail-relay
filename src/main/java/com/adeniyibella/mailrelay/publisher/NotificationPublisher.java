package com.adeniyibella.mailrelay.publisher;

import com.adeniyibella.mailrelay.config.rabbit.NotificationRabbitConfig;
import com.adeniyibella.mailrelay.event.EmailNotificationEvent;
import com.adeniyibella.mailrelay.properties.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single public API for sending notifications from a consuming application.
 *
 * Inject this bean and call one of its methods to enqueue an email
 * asynchronously — the caller returns immediately while RabbitMQ and the
 * library's listener handle delivery and retries in the background.
 *
 * Usage in a consuming app:
 *
 * private final NotificationPublisher notifications;
 *
 * public void register(RegisterRequest req) {
 * User user = userRepository.save(new User(req));
 * notifications.sendWelcomeEmail(user.getId(), user.getEmail(),
 * user.getUsername());
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final NotificationProperties properties;

    /**
     * Enqueues a welcome email for a newly registered user.
     */
    public void sendWelcomeEmail(UUID userId, String email, String username) {
        publish(EmailNotificationEvent.welcome(userId, email, username, properties.appName()));
    }

    /**
     * Publishes any EmailNotificationEvent to the notification queue.
     * When notifications are disabled the event is silently dropped.
     */
    public void publish(EmailNotificationEvent event) {
        if (event == null)
            throw new IllegalArgumentException("event must not be null");

        if (!properties.enabled()) {
            log.warn("[mail-relay] Notifications disabled — dropping eventType={} to={}",
                    event.eventType(), event.to());
            return;
        }

        log.debug("[mail-relay] Publishing eventType={} to={} correlationId={}",
                event.eventType(), event.to(), event.correlationId());

        rabbitTemplate.convertAndSend(
                NotificationRabbitConfig.NOTIFICATION_EXCHANGE,
                NotificationRabbitConfig.NOTIFICATION_ROUTING_KEY,
                event);
    }
}