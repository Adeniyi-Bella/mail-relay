package com.adeniyibella.mailrelay.config.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Shared base class with reusable helpers for:
 * - Building standard exchange / queue / binding topology wired to the global DLQ.
 * - Creating a TaskExecutor backed by Java 21 virtual threads.
 * - Building a retrying listener factory (3 attempts, jitter exponential backoff).
 * - Building a fail-fast listener factory (no retries, immediate DLQ routing).
 */
public abstract class RabbitBaseClass {

    protected Declarables buildTopology(String exchangeName, String queueName, String routingKey) {
        DirectExchange exchange = new DirectExchange(exchangeName);
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange",    RabbitCoreConfig.GLOBAL_DLX)
                .withArgument("x-dead-letter-routing-key", RabbitCoreConfig.GLOBAL_DLQ_ROUTING_KEY)
                .build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        return new Declarables(exchange, queue, binding);
    }

    protected TaskExecutor buildVirtualTaskExecutor(String threadNamePrefix) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(threadNamePrefix);
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * Retries up to 3 times with jitter exponential backoff before routing to DLQ.
     * Backoff profile:
     *   Initial interval : 1000ms
     *   Multiplier       : 2.0
     *   Max interval     : 8000ms
     *   Max jitter       : ±300ms
     */
    protected SimpleRabbitListenerContainerFactory buildRetryingFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TaskExecutor executor,
            int concurrency,
            int maxConcurrency) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setTaskExecutor(executor);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(3)
                        .backOffOptions(1_000, 2.0, 8_000)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build());
        return factory;
    }

    /**
     * No retries — message is immediately rejected and routed to DLQ on first failure.
     */
    protected SimpleRabbitListenerContainerFactory buildFailFastFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TaskExecutor executor,
            int concurrency,
            int maxConcurrency) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setTaskExecutor(executor);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    // private static final class JitterExponentialBackOffPolicy implements BackOffPolicy {

    //     private final long   initialInterval;
    //     private final double multiplier;
    //     private final long   maxInterval;
    //     private final long   maxJitterMs;
    //     private final Sleeper sleeper = new ThreadWaitSleeper();

    //     JitterExponentialBackOffPolicy(long initialInterval, double multiplier,
    //                                    long maxInterval, long maxJitterMs) {
    //         this.initialInterval = initialInterval;
    //         this.multiplier      = multiplier;
    //         this.maxInterval     = maxInterval;
    //         this.maxJitterMs     = maxJitterMs;
    //     }

    //     @Override
    //     public BackOffContext start(RetryContext context) {
    //         return new Context(initialInterval);
    //     }

    //     @Override
    //     public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
    //         Context ctx  = (Context) backOffContext;
    //         long jitter  = (maxJitterMs <= 0) ? 0
    //                 : java.util.concurrent.ThreadLocalRandom.current().nextLong(maxJitterMs + 1);
    //         long sleepTime = ctx.currentInterval + jitter;
    //         try {
    //             sleeper.sleep(sleepTime);
    //         } catch (InterruptedException ex) {
    //             Thread.currentThread().interrupt();
    //             throw new BackOffInterruptedException("Thread interrupted during back-off", ex);
    //         }
    //         ctx.currentInterval = Math.min(maxInterval, (long) (ctx.currentInterval * multiplier));
    //     }

    //     private static final class Context implements BackOffContext {
    //         long currentInterval;
    //         Context(long currentInterval) { this.currentInterval = currentInterval; }
    //     }
    // }



}