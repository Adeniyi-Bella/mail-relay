package com.adeniyibella.mailrelay.config;

import com.adeniyibella.mailrelay.config.rabbit.NotificationRabbitConfig;
import com.adeniyibella.mailrelay.config.rabbit.RabbitCoreConfig;
import com.adeniyibella.mailrelay.listener.NotificationListener;
import com.adeniyibella.mailrelay.properties.NotificationProperties;
import com.adeniyibella.mailrelay.publisher.NotificationPublisher;
import com.adeniyibella.mailrelay.service.ResendEmailService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration entry point for mail-relay.
 *
 * Consuming apps do not need to import or reference this class directly —
 * Spring Boot discovers it automatically via
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@EnableConfigurationProperties(NotificationProperties.class)
@Import({
        RabbitCoreConfig.class,
        NotificationRabbitConfig.class
})
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResendEmailService resendEmailService(
            NotificationProperties properties,
            org.springframework.boot.web.client.RestTemplateBuilder builder) {
        return new ResendEmailService(properties, builder);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationPublisher notificationPublisher(
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
            NotificationProperties properties) {
        return new NotificationPublisher(rabbitTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationListener notificationListener(ResendEmailService resendEmailService) {
        return new NotificationListener(resendEmailService);
    }
}