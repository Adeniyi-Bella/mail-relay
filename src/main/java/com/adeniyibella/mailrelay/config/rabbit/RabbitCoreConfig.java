package com.adeniyibella.mailrelay.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the global Dead Letter Exchange and Dead Letter Queue shared by
 * all queues in the library.
 *
 * All beans are guarded with @ConditionalOnMissingBean so a consuming
 * application can override any individual bean without conflict.
 */
@Configuration
public class RabbitCoreConfig {

    public static final String GLOBAL_DLX             = "system.dlx";
    public static final String GLOBAL_DLQ             = "system.dlq";
    public static final String GLOBAL_DLQ_ROUTING_KEY = "system.dead.letter";

    @Bean
    @ConditionalOnMissingBean(name = "globalDlx")
    public DirectExchange globalDlx() {
        return new DirectExchange(GLOBAL_DLX);
    }

    @Bean
    @ConditionalOnMissingBean(name = "globalDlq")
    public Queue globalDlq() {
        return QueueBuilder.durable(GLOBAL_DLQ).build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "globalDlqBinding")
    public Binding globalDlqBinding(DirectExchange globalDlx, Queue globalDlq) {
        return BindingBuilder.bind(globalDlq).to(globalDlx).with(GLOBAL_DLQ_ROUTING_KEY);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter notificationMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter notificationMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(notificationMessageConverter);
        return template;
    }
}