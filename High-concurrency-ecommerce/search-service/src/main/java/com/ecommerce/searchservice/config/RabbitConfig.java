package com.ecommerce.searchservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * search-service 消息队列配置。
 *
 * 消费队列：search.product.sync.queue
 * 绑定到 product.exchange（topic 类型），订阅三个 routingKey：
 *   - product.created
 *   - product.updated
 *   - product.status.changed
 *
 * product.exchange 在 product-service 已创建，这里直接 new 一个临时引用绑定即可，不重复声明 Bean。
 */
@Configuration
public class RabbitConfig {

    /** 消费队列：统一接收商品相关事件，按 routingKey 分发 */
    public static final String SEARCH_PRODUCT_SYNC_QUEUE = "search.product.sync.queue";

    public static final String PRODUCT_EXCHANGE = "product.exchange";

    @Bean
    public Queue searchProductSyncQueue() {
        return QueueBuilder.durable(SEARCH_PRODUCT_SYNC_QUEUE).build();
    }

    @Bean
    public Binding bindProductCreated() {
        return BindingBuilder.bind(searchProductSyncQueue())
            .to(new TopicExchange(PRODUCT_EXCHANGE))
            .with("product.created");
    }

    @Bean
    public Binding bindProductUpdated() {
        return BindingBuilder.bind(searchProductSyncQueue())
            .to(new TopicExchange(PRODUCT_EXCHANGE))
            .with("product.updated");
    }

    @Bean
    public Binding bindProductStatusChanged() {
        return BindingBuilder.bind(searchProductSyncQueue())
            .to(new TopicExchange(PRODUCT_EXCHANGE))
            .with("product.status.changed");
    }
}
