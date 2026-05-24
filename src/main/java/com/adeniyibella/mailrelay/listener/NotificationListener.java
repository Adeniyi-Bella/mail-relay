package com.adeniyibella.mailrelay.listener;

import com.adeniyibella.mailrelay.event.EmailNotificationEvent;
import com.adeniyibella.mailrelay.service.MailSender;
import com.adeniyibella.mailrelay.config.rabbit.NotificationRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes EmailNotificationEvent messages from the notification queue
 * and delegates to ResendEmailService for delivery.
 *
 * Retries are handled automatically by the notificationListenerContainerFactory
 * (3 attempts, jitter exponential backoff). After all attempts are exhausted
 * the message is routed to the global DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final MailSender mailSender;

    @RabbitListener(queues = NotificationRabbitConfig.NOTIFICATION_QUEUE, containerFactory = "notificationListenerContainerFactory")
    public void handle(EmailNotificationEvent event) {
        log.debug("[mail-relay] Received eventType={} to={} correlationId={}",
                event.eventType(), event.to(), event.correlationId());

        mailSender.send(event);
    }
}