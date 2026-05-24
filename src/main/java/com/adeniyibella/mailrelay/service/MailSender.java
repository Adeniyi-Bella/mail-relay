package com.adeniyibella.mailrelay.service;

import com.adeniyibella.mailrelay.event.EmailNotificationEvent;

/**
 * Strategy interface for email delivery.
 *
 * The library provides ResendMailSender as the default implementation.
 * To use a different provider (SendGrid, Mailgun, AWS SES etc.), implement
 * this interface and declare it as a Spring bean in your app — the library
 * will use your implementation automatically via @ConditionalOnMissingBean.
 *
 * Example custom implementation:
 *
 *   @Service
 *   public class SendGridMailSender implements MailSender {
 *       public void send(EmailNotificationEvent event) {
 *           // your SendGrid logic here
 *       }
 *   }
 */
public interface MailSender {
    void send(EmailNotificationEvent event);
}