package com.adeniyibella.mailrelay.event;

import java.util.UUID;

/**
 * Immutable event published to RabbitMQ by the consuming application.
 *
 * Quick usage:
 *
 * // Welcome email shortcut
 * EmailNotificationEvent.welcome(user.getId(), user.getEmail(), user.getUsername());
 *
 * // Fully custom
 * EmailNotificationEvent.builder()
 *         .to("alice@example.com")
 *         .subject("Your invoice is ready")
 *         .htmlBody("<p>Download it here</p>")
 *         .build();
 */
public record EmailNotificationEvent(

        /** Optional correlation ID for tracing back to the originating user. */
        UUID correlationId,

        /** Recipient address. Required. */
        String to,

        /** Optional display name for the recipient e.g. "Alice". */
        String recipientName,

        /** Email subject line. Required. */
        String subject,

        /** HTML body. Required. */
        String htmlBody,

        /** Optional plain-text fallback for email clients that don't render HTML. */
        String textBody,

        /**
         * Logical event type tag for metrics and logging.
         * Examples: "WELCOME", "PASSWORD_RESET", "INVOICE".
         * Defaults to "GENERIC" when not set.
         */
        String eventType

) {

    public EmailNotificationEvent {
        if (to       == null || to.isBlank())       throw new IllegalArgumentException("'to' is required");
        if (subject  == null || subject.isBlank())  throw new IllegalArgumentException("'subject' is required");
        if (htmlBody == null || htmlBody.isBlank()) throw new IllegalArgumentException("'htmlBody' is required");
        if (eventType == null || eventType.isBlank()) eventType = "GENERIC";
    }

    // ── Static factory helpers ───────────────────────────────────────────────

    public static EmailNotificationEvent welcome(UUID userId, String email, String username) {
        return builder()
                .correlationId(userId)
                .to(email)
                .recipientName(username)
                .subject("Welcome!")
                .htmlBody("<p>Welcome, " + username + "! We're glad you're here.</p>")
                .eventType("WELCOME")
                .build();
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID   correlationId;
        private String to;
        private String recipientName;
        private String subject;
        private String htmlBody;
        private String textBody;
        private String eventType;

        private Builder() {}

        public Builder correlationId(UUID v)  { this.correlationId = v;  return this; }
        public Builder to(String v)            { this.to            = v;  return this; }
        public Builder recipientName(String v) { this.recipientName = v;  return this; }
        public Builder subject(String v)       { this.subject       = v;  return this; }
        public Builder htmlBody(String v)      { this.htmlBody      = v;  return this; }
        public Builder textBody(String v)      { this.textBody      = v;  return this; }
        public Builder eventType(String v)     { this.eventType     = v;  return this; }

        public EmailNotificationEvent build() {
            return new EmailNotificationEvent(
                    correlationId, to, recipientName,
                    subject, htmlBody, textBody, eventType);
        }
    }
}