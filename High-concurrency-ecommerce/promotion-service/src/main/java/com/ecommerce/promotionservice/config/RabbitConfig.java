package com.ecommerce.promotionservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    /** 本服务发布事件用的交换机（topic 类型，和 product-service 一致） */
    public static final String PROMOTION_EXCHANGE = "promotion.exchange";


    @Bean
    public TopicExchange promotionExchange(){
        return new TopicExchange(PROMOTION_EXCHANGE);
    }

    // ================= 引用外部交换机（幂等声明，确保绑定可靠创建） =================

    @Bean
    public TopicExchange userExchangeRef(){
        return new TopicExchange("user.exchange", true, false);
    }

    @Bean
    public TopicExchange orderExchangeRef(){
        return new TopicExchange("order.exchange", true, false);
    }

    // ================= 发布端 =================
    @Bean
    public Queue couponUsedQueue(){
        return QueueBuilder.durable("promotion.coupon.used.queue").build();
    }

    @Bean
    public Queue couponRefundedQueue(){
        return QueueBuilder.durable("promotion.coupon.refunded.queue").build();
    }

    @Bean
    public Queue activityBudgetLowQueue(){
        return QueueBuilder.durable("promotion.activity.budget.low.queue").build();
    }

    @Bean
    public Binding bindCouponUsed(){
        return BindingBuilder.bind(couponUsedQueue())
            .to(promotionExchange()).with("promotion.coupon.used");
    }

    @Bean
    public Binding bindCouponRefunded(){
        return BindingBuilder.bind(couponRefundedQueue())
            .to(promotionExchange()).with("promotion.coupon.refunded");
    }

    @Bean
    public Binding bindActivityBudgetLow(){
        return BindingBuilder.bind(activityBudgetLowQueue())
            .to(promotionExchange()).with("promotion.activity.budget.low");
    }


    // ================= 消费端 =================

    /**
     * 统一消费队列：接收 user.registered + order.paid/cancelled/refunded
     * PromotionEventConsumer 里用 @RabbitListener(queues = "promotion.queue")
     * 再根据 routingKey 头判断具体事件类型分发处理。
     */
    @Bean
    public Queue promotionQueue() {
        return QueueBuilder.durable("promotion.queue").build();
    }

    // -- 绑定到 user-service 的 exchange（user.exchange 已在 user-service 创建） --

    @Bean
    public Binding bindUserRegistered() {
        return BindingBuilder.bind(promotionQueue())
            .to(userExchangeRef())
            .with("user.registered");
    }

    // -- 绑定到 order-service 的 exchange（order.exchange 已在 order-service 创建） --

    @Bean
    public Binding bindOrderPaid() {
        return BindingBuilder.bind(promotionQueue())
            .to(orderExchangeRef())
            .with("order.paid");
    }

    @Bean
    public Binding bindOrderCancelled() {
        return BindingBuilder.bind(promotionQueue())
            .to(orderExchangeRef())
            .with("order.cancelled");
    }

    @Bean
    public Binding bindOrderRefunded() {
        return BindingBuilder.bind(promotionQueue())
            .to(orderExchangeRef())
            .with("order.refunded");
    }
}
