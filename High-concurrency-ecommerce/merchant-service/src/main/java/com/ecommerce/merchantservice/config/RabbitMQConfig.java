package com.ecommerce.merchantservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String MERCHANT_EXCHANGE = "merchant.exchange";

    // ============ Exchange ============
    @Bean
    public TopicExchange merchantExchange() {
        return new TopicExchange(MERCHANT_EXCHANGE);
    }

    // ============ 发布端 Queue ============
    @Bean
    public Queue merchantApprovedQueue() {
        return QueueBuilder.durable("merchant.approved.queue").build();
    }

    @Bean
    public Queue merchantRejectedQueue() {
        return QueueBuilder.durable("merchant.rejected.queue").build();
    }

    @Bean
    public Queue merchantFrozenQueue() {
        return QueueBuilder.durable("merchant.frozen.queue").build();
    }

    @Bean
    public Queue merchantUnfrozenQueue() {
        return QueueBuilder.durable("merchant.unfrozen.queue").build();
    }

    @Bean
    public Queue merchantClosedQueue() {
        return QueueBuilder.durable("merchant.closed.queue").build();
    }

    // ============ Binding ============
    @Bean
    public Binding bindApproved() {
        return BindingBuilder.bind(merchantApprovedQueue())
                .to(merchantExchange()).with("merchant.approved");
    }

    @Bean
    public Binding bindRejected() {
        return BindingBuilder.bind(merchantRejectedQueue())
                .to(merchantExchange()).with("merchant.rejected");
    }

    @Bean
    public Binding bindFrozen() {
        return BindingBuilder.bind(merchantFrozenQueue())
                .to(merchantExchange()).with("merchant.frozen");
    }

    @Bean
    public Binding bindUnfrozen() {
        return BindingBuilder.bind(merchantUnfrozenQueue())
                .to(merchantExchange()).with("merchant.unfrozen");
    }

    @Bean
    public Binding bindClosed() {
        return BindingBuilder.bind(merchantClosedQueue())
                .to(merchantExchange()).with("merchant.closed");
    }
}
