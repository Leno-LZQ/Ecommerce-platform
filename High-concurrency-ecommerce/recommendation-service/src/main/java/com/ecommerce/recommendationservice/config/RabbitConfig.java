package com.ecommerce.recommendationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 推荐服务 RabbitMQ 配置：消费 behavior.* 事件。
 */
@Configuration
public class RabbitConfig {

    public static final String RECO_EXCHANGE = "ecommerce.event.topic";
    public static final String RECO_QUEUE = "recommendation.queue";
    public static final String BEHAVIOR_ROUTING_KEY = "behavior.*";

    @Bean
    public TopicExchange recoExchange() {
        return ExchangeBuilder.topicExchange(RECO_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue recoQueue() {
        return QueueBuilder.durable(RECO_QUEUE).build();
    }

    @Bean
    public Binding recoBinding(Queue recoQueue, TopicExchange recoExchange) {
        return BindingBuilder.bind(recoQueue).to(recoExchange).with(BEHAVIOR_ROUTING_KEY);
    }

}
