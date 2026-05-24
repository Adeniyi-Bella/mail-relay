package com.adeniyibella.mailrelay.service;

import com.adeniyibella.mailrelay.event.EmailNotificationEvent;
import com.adeniyibella.mailrelay.exception.RetryableNotificationException;
import com.adeniyibella.mailrelay.properties.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ResendMailSender implements MailSender {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final NotificationProperties properties;
    private final RestClient restClient;

    public ResendMailSender(NotificationProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(RESEND_ENDPOINT)
                .build();
    }

    @Override
    public void send(EmailNotificationEvent event) {
        NotificationProperties.Resend resend = properties.resend();

        if (resend == null || isBlank(resend.apiKey()) || isBlank(resend.fromEmail())) {
            log.warn("[mail-relay] Resend not configured — skipping eventType={} to={}",
                    event.eventType(), event.to());
            return;
        }

        try {
            var response = restClient.post()
                    .header("Authorization", "Bearer " + resend.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(event, resend.fromEmail()))
                    .retrieve()
                    .toBodilessEntity();

            log.info("[mail-relay] Sent eventType={} to={} correlationId={} status={}",
                    event.eventType(), event.to(), event.correlationId(),
                    response.getStatusCode());

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
}