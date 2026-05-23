package com.adeniyibella.mailrelay.service;

import com.adeniyibella.mailrelay.event.EmailNotificationEvent;
import com.adeniyibella.mailrelay.exception.RetryableNotificationException;
import com.adeniyibella.mailrelay.properties.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls the Resend API to deliver a transactional email.
 *
 * Retryable errors (5xx, 429) throw RetryableNotificationException so the
 * RabbitMQ retry interceptor can re-deliver with backoff before routing to DLQ.
 *
 * Non-retryable errors (4xx except 429) are logged and swallowed —
 * no point retrying a bad request.
 */
@Slf4j
@Service
public class ResendEmailService {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final NotificationProperties properties;
    private final RestTemplate           restTemplate;

    public ResendEmailService(NotificationProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        NotificationProperties.Resend resend = properties.resend();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(resend.connectTimeoutMs());
        factory.setReadTimeout(resend.readTimeoutMs());

        this.restTemplate = builder.requestFactory(() -> factory).build();
    }

    public void send(EmailNotificationEvent event) {
        NotificationProperties.Resend resend = properties.resend();

        if (resend == null || isBlank(resend.apiKey()) || isBlank(resend.fromEmail())) {
            log.warn("[mail-relay] Resend not configured — skipping eventType={} to={}",
                    event.eventType(), event.to());
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resend.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    RESEND_ENDPOINT,
                    new HttpEntity<>(buildPayload(event, resend.fromEmail()), headers),
                    String.class);

            HttpStatusCode status = response.getStatusCode();

            if (status.is2xxSuccessful()) {
                log.info("[mail-relay] Sent eventType={} to={} correlationId={} status={}",
                        event.eventType(), event.to(), event.correlationId(), status);
                return;
            }

            if (isRetryable(status)) {
                throw new RetryableNotificationException(
                        "Retryable Resend error status=" + status
                        + " eventType=" + event.eventType()
                        + " to=" + event.to());
            }

            log.warn("[mail-relay] Non-retryable Resend error eventType={} to={} status={} body={}",
                    event.eventType(), event.to(), status, response.getBody());

        } catch (RestClientException ex) {
            throw new RetryableNotificationException(
                    "Transient network failure sending email to " + event.to(), ex);
        }
    }

    private Map<String, Object> buildPayload(EmailNotificationEvent event, String fromEmail) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("from",    fromEmail);
        payload.put("to",      event.to());
        payload.put("subject", event.subject());
        payload.put("html",    event.htmlBody());
        if (event.textBody() != null && !event.textBody().isBlank()) {
            payload.put("text", event.textBody());
        }
        return payload;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }
}