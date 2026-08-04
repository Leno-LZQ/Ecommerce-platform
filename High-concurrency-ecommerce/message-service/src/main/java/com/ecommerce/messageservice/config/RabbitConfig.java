package com.ecommerce.messageservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — 声明 message-service 使用的交换机、队列和绑定
 *
 * <p>mq 约定（与项目一致）：</p>
 * <ul>
 *   <li>交换机命名：{domain}.exchange（topic 类型）</li>
 *   <li>路由键：{domain}.{action}（如 message.new）</li>
 *   <li>本服务发布事件到 message.exchange</li>
 *   <li>本服务消费来自 order.exchange 的事件</li>
 * </ul>
 *
 * <p>拓扑图：</p>
 * <pre>
 *   order-service                 message-service
 *      │                               │
 *      │  order.paid                   │
 *      │  order.shipped ──→ order.exchange ──→ message.queue → MessageEventConsumer
 *      │  order.refunded               │
 *      │                               │
 *      │                     message.new ──→ message.exchange → 离线通知队列
 * </pre>
 */
@Configuration
public class RabbitConfig {

    /** 本服务发布事件用的交换机 */
    public static final String MESSAGE_EXCHANGE = "message.exchange";

    /** 本服务消费用的队列（接收 order-service 的订单事件） */
    public static final String MESSAGE_QUEUE = "message.queue";

    // ================================================================
    //  声明交换机
    // ================================================================

    @Bean
    public TopicExchange messageExchange() {
        return new TopicExchange(MESSAGE_EXCHANGE, true, false);
        // 参数说明：durable=true（服务重启不丢），autoDelete=false（不自动删除）
    }

    // ================================================================
    //  声明消费队列
    // ================================================================

    @Bean
    public Queue messageQueue() {
        return QueueBuilder.durable(MESSAGE_QUEUE).build();
    }

    // ================================================================
    //  声明外部交换机（order-service 创建，此处引用）
    // ================================================================

    /**
     * 引用 order.exchange（由 order-service 创建），统一声明为一个 Bean 以避免重复创建
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange", true, false);
    }

    // ================================================================
    //  绑定：消费来自 order.exchange 的订单事件
    // ================================================================

    /**
     * 绑定 order.paid → message.queue
     * 订单支付成功后，注入系统消息"订单已支付"
     */
    @Bean
    public Binding bindOrderPaid() {
        return BindingBuilder
            .bind(messageQueue())
            .to(orderExchange())
            .with("order.paid");
    }

    /**
     * 绑定 order.shipped → message.queue
     * 订单发货后，注入系统消息"订单已发货，快递单号 xxx"
     */
    @Bean
    public Binding bindOrderShipped() {
        return BindingBuilder
            .bind(messageQueue())
            .to(orderExchange())
            .with("order.shipped");
    }

    /**
     * 绑定 order.refunded → message.queue
     * 订单退款后，注入系统消息"订单已退款"
     */
    @Bean
    public Binding bindOrderRefunded() {
        return BindingBuilder
            .bind(messageQueue())
            .to(orderExchange())
            .with("order.refunded");
    }
}
