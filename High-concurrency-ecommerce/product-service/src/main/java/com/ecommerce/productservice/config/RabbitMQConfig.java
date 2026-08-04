package com.ecommerce.productservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PRODUCT_EXCHANGE = "product.exchange";

    // ============ 发布端 Queue 和 Binding ============
    @Bean
    public Queue productCreatedQueue() {
        return QueueBuilder.durable("product.created.queue").build();
    }

    @Bean
    public Queue productUpdatedQueue() {
        return QueueBuilder.durable("product.updated.queue").build();
    }

    @Bean
    public Queue productPriceChangedQueue() {
        return QueueBuilder.durable("product.price.changed.queue").build();
    }

    @Bean
    public Queue productStatusChangedQueue() {
        return QueueBuilder.durable("product.status.changed.queue").build();
    }

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE);
    }

    @Bean
    public Binding bindCreated() {
        return BindingBuilder.bind(productCreatedQueue()).to(productExchange()).with("product.created");
    }

    @Bean
    public Binding bindUpdated() {
        return BindingBuilder.bind(productUpdatedQueue()).to(productExchange()).with("product.updated");
    }

    @Bean
    public Binding bindPriceChanged() {
        return BindingBuilder.bind(productPriceChangedQueue()).to(productExchange()).with("product.price.changed");
    }

    @Bean
    public Binding bindStatusChanged() {
        return BindingBuilder.bind(productStatusChangedQueue()).to(productExchange()).with("product.status.changed");
    }

    // ============ 消费端 Queue 和 Binding ============
    @Bean
    public Queue merchantFrozenQueue() {
        return QueueBuilder.durable("merchant.frozen.product.queue").build();
    }

    @Bean
    public Queue stockChangedQueue() {
        return QueueBuilder.durable("product.stock.changed.queue").build();
    }

    @Bean
    public Queue stockRecoveredQueue() {
        return QueueBuilder.durable("product.stock.recovered.queue").build();
    }

    @Bean
    public Binding bindMerchantFrozen() {
        return BindingBuilder.bind(merchantFrozenQueue())
            .to(new TopicExchange("merchant.exchange"))
            .with("merchant.frozen");
    }

    @Bean
    public Binding bindStockChanged() {
        return BindingBuilder.bind(stockChangedQueue())
            .to(new TopicExchange("inventory.exchange"))
            .with("inventory.stock.changed");
    }
}
