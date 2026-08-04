package com.ecommerce.csservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客服服务 RabbitMQ 配置
 *
 * <p>本服务发布 cs.ticket.* 事件到 cs.exchange，
 * 消费 order.refunded 事件自动关闭纠纷工单。</p>
 */
@Configuration
public class RabbitConfig {

    public static final String CS_EXCHANGE = "cs.exchange";
    public static final String CS_QUEUE = "cs.queue";

    @Bean
    public TopicExchange csExchange() {
        return new TopicExchange(CS_EXCHANGE, true, false);
    }

    @Bean
    public Queue csQueue() {
        return QueueBuilder.durable(CS_QUEUE).build();
    }

    /**
     * 引用 order.exchange（由 order-service 创建）
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange", true, false);
    }

    /**
     * 绑定 order.refunded → cs.queue
     */
    @Bean
    public Binding bindOrderRefunded() {
        return BindingBuilder
            .bind(csQueue())
            .to(orderExchange())
            .with("order.refunded");
    }
}
