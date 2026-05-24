# mail-relay

> Fire-and-forget transactional email starter — queues messages via RabbitMQ and delivers through your email provider of choice (Resend by default) with automatic retries and dead-letter handling.

---

## How it works

```
Your App
   │
   │  1. User signs up
   │
   ▼
NotificationPublisher.sendWelcomeEmail()
   │
   │  2. Drops message on RabbitMQ queue (instant, non-blocking)
   │     → Your app returns 200 immediately. User doesn't wait.
   │
   ▼
RabbitMQ (notifications.email.queue)
   │
   │  3. NotificationListener picks up the message (background)
   │
   ▼
MailSender (interface)
   │
   │  4. Delivers the email via your configured provider
   │     Default: ResendMailSender → Resend API
   │     Custom:  any implementation you provide
   │
   ├── Success (2xx) ────────────────────────► Email delivered ✅
   │
   ├── Retryable failure (5xx / 429) ────────► Retry up to 3x with
   │                                           jitter exponential backoff
   │                                           (1s → 2s → 4s ± 300ms jitter)
   │
   └── All retries exhausted ────────────────► Routed to Dead Letter Queue (system.dlq)
                                               for manual inspection
```

---

## Failure scenarios

### Provider is down (5xx / 429)
The library throws a `RetryableNotificationException`. RabbitMQ retries the message up to **3 times** with jitter exponential backoff:

| Attempt | Delay        |
|---------|--------------|
| 1st     | ~1000ms      |
| 2nd     | ~2000ms      |
| 3rd     | ~4000ms      |

After all retries are exhausted the message is routed to the **Dead Letter Queue** (`system.dlq`). No email is lost — it sits in the DLQ and can be replayed manually once Resend is back.

### Email address is undeliverable (bad domain, mailbox full)
Your email provider accepts the request and returns `2xx` — the library considers it a success. The provider then attempts delivery and receives a **bounce**. The bounce is sent back to your `from-email` address. The library has no visibility into bounces.

Most providers (Resend, SendGrid, Mailgun) expose a bounce webhook. You can register an endpoint in your provider's dashboard to receive bounce events and mark the user's email as invalid in your own database. For welcome emails this is often acceptable to ignore early on — handle it when delivery rates become a concern.

### App crashes mid-send
If your app crashes after the message is placed on the RabbitMQ queue, the message survives — RabbitMQ persists it to disk (durable queue). When your app restarts, the listener picks it up and delivery continues.

If your app crashes **before** the message reaches the queue, the email is lost. Ensure your notification call happens **after** your database transaction commits.

---

## Why RabbitMQ and not Spring @Async?

Spring `@Async` runs in a thread pool inside your app — no external service needed. But:

| | `@Async` | RabbitMQ |
|---|---|---|
| External service | ❌ None | ✅ Required |
| Survives app crash | ❌ No | ✅ Yes (durable queue) |
| Retries | ❌ Manual | ✅ Built-in |
| Dead letter handling | ❌ Manual | ✅ Built-in |
| Multi-instance safe | ❌ No | ✅ Yes |

For production use RabbitMQ. For local dev or hobby projects `@Async` is fine.

---

## Installation

### 1. Add JitPack repository to your `pom.xml`

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### 2. Add the dependency

```xml
<dependency>
    <groupId>com.github.Adeniyi-Bella</groupId>
    <artifactId>mail-relay</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. Add configuration to your `application.properties`

```properties
mail.relay.enabled=true
mail.relay.app-name=Your App Name
mail.relay.resend.api-key=${RESEND_API_KEY}
mail.relay.resend.from-email=${RESEND_FROM_EMAIL}
```

Or `application.yml`:

```yaml
mail:
  relay:
    enabled: true
    app-name: Your App Name
    resend:
      api-key: ${RESEND_API_KEY}
      from-email: ${RESEND_FROM_EMAIL}
```

### 4. Add RabbitMQ connection to your app

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

### 5. Run RabbitMQ

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

---

## Usage

Inject `NotificationPublisher` anywhere in your app:

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final NotificationPublisher notificationPublisher;

    public void register(RegisterRequest req) {
        User user = userRepository.save(new User(req));

        // Fire-and-forget — caller does not wait for email delivery
        notificationPublisher.sendWelcomeEmail(user.getId(), user.getEmail(), user.getUsername());
    }
}
```

### Custom email

```java
EmailNotificationEvent event = EmailNotificationEvent.builder()
        .to("alice@example.com")
        .subject("Your invoice is ready")
        .htmlBody("<p>Download it <a href='...'>here</a>.</p>")
        .textBody("Download your invoice at: ...")
        .eventType("INVOICE")
        .build();

notificationPublisher.publish(event);
```

---

## Custom email provider

The library ships with `ResendMailSender` as the default but is not tied to Resend. It delivers emails through a `MailSender` interface — swap the provider by implementing it and declaring it as a Spring bean. The library detects your bean and skips creating `ResendMailSender` automatically.

### Example: SendGrid

```java
@Service
@RequiredArgsConstructor
public class SendGridMailSender implements MailSender {

    private final SendGrid sendGrid;

    @Override
    public void send(EmailNotificationEvent event) {
        Email to      = new Email(event.to());
        Email from    = new Email("hello@yourdomain.com");
        Content body  = new Content("text/html", event.htmlBody());
        Mail mail     = new Mail(from, event.subject(), to, body);

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            sendGrid.api(request);
        } catch (IOException ex) {
            throw new RetryableNotificationException("SendGrid delivery failed", ex);
        }
    }
}
```

### Example: AWS SES

```java
@Service
@RequiredArgsConstructor
public class SesMailSender implements MailSender {

    private final SesClient sesClient;

    @Override
    public void send(EmailNotificationEvent event) {
        sesClient.sendEmail(r -> r
                .destination(d -> d.toAddresses(event.to()))
                .message(m -> m
                        .subject(c -> c.data(event.subject()))
                        .body(b -> b.html(c -> c.data(event.htmlBody()))))
                .fromEmailAddress("hello@yourdomain.com"));
    }
}
```

Throw `RetryableNotificationException` from your implementation for transient failures — RabbitMQ will retry automatically.

---

## Configuration reference

| Property | Required | Default | Description |
|---|---|---|---|
| `mail.relay.enabled` | No | `true` | Master switch. Set to `false` to silently drop all emails (useful in test environments). |
| `mail.relay.app-name` | No | `Our App` | App name used in email subject and body. |
| `mail.relay.resend.api-key` | Only with default Resend provider | — | Your Resend API key (`re_xxxx...`). |
| `mail.relay.resend.from-email` | Only with default Resend provider | — | Verified sender address or `Name <addr>` format. |
| `mail.relay.resend.connect-timeout-ms` | No | `5000` | HTTP connect timeout in ms. |
| `mail.relay.resend.read-timeout-ms` | No | `10000` | HTTP read timeout in ms. |
| `mail.relay.rabbit.concurrency` | No | `2` | Minimum concurrent RabbitMQ consumers. |
| `mail.relay.rabbit.max-concurrency` | No | `5` | Maximum concurrent RabbitMQ consumers. |

---

## RabbitMQ topology

| Resource | Name |
|---|---|
| Exchange | `notifications.exchange` |
| Queue | `notifications.email.queue` |
| Routing key | `notifications.email` |
| Dead Letter Exchange | `system.dlx` |
| Dead Letter Queue | `system.dlq` |
| DLQ Routing key | `system.dead.letter` |

Failed messages after all retries are routed to `system.dlq`. Inspect and replay them via the RabbitMQ management UI (`http://localhost:15672`).

---

## Requirements

- Java 21+
- Spring Boot 3.3+
- RabbitMQ 3.x
- An account with your email provider of choice ([Resend](https://resend.com) used by default)

---

## License

MIT