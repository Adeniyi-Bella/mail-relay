package com.adeniyibella.mailrelay.exception;

/**
 * Thrown when an email send attempt fails with a transient / retryable error
 * (5xx from Resend, HTTP 429 rate-limit, or network timeout).
 *
 * The RabbitMQ retry interceptor recognises this exception and will
 * re-deliver the message up to the configured maxAttempts before
 * routing it to the Dead Letter Queue.
 */
public class RetryableNotificationException extends RuntimeException {

    public RetryableNotificationException(String message) {
        super(message);
    }

    public RetryableNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}