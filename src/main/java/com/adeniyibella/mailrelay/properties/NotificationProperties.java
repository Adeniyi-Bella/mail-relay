package com.adeniyibella.mailrelay.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All library configuration lives under the {@code mail.relay} prefix.
 *
 * Minimal application.yml for a consuming app:
 *
 * mail:
 * relay:
 * resend:
 * api-key: re_xxxxxxxxxxxx
 * from-email: hello@yourdomain.com
 */
@ConfigurationProperties(prefix = "mail.relay")
public record NotificationProperties(

        /**
         * Master switch. Set to false to silently drop all notifications
         * useful in local / test environments. Defaults to true.
         */
        boolean enabled,
        String appName,
        Resend resend,
        Rabbit rabbit

) {

    public NotificationProperties {
        if (resend == null)
            resend = new Resend(null, null, 5_000, 10_000);
        if (rabbit == null)
            rabbit = new Rabbit(2, 5);
        if (appName == null || appName.isBlank())
            appName = "Our App";
    }

    public static NotificationProperties defaults() {
        return new NotificationProperties(true, null, null, null);
    }

    /**
     * @param apiKey           Your Resend API key (re_xxxx...).
     * @param fromEmail        Verified sender address or "Name <addr>" format.
     * @param connectTimeoutMs HTTP connect timeout in milliseconds (default 5000).
     * @param readTimeoutMs    HTTP read timeout in milliseconds (default 10000).
     */
    public record Resend(
            String apiKey,
            String fromEmail,
            int connectTimeoutMs,
            int readTimeoutMs) {
        public Resend {
            if (connectTimeoutMs <= 0)
                connectTimeoutMs = 5_000;
            if (readTimeoutMs <= 0)
                readTimeoutMs = 10_000;
        }
    }

    /**
     * @param concurrency    Minimum concurrent consumers (default 2).
     * @param maxConcurrency Maximum concurrent consumers (default 5).
     */
    public record Rabbit(
            int concurrency,
            int maxConcurrency) {
        public Rabbit {
            if (concurrency <= 0)
                concurrency = 2;
            if (maxConcurrency <= 0)
                maxConcurrency = 5;
        }
    }
}