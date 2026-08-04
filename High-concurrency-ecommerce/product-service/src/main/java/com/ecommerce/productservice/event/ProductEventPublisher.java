package com.ecommerce.productservice.event;

import com.ecommerce.productservice.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class ProductEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String PRODUCT_EXCHANGE = "product.exchange";

    // ==================== publishCreated ====================
    public void publishCreated(Product product) {
        ProductCreatedEvent event = ProductCreatedEvent.builder()
            .productId(product.getId())
            .merchantId(product.getMerchantId())
            .name(product.getName())
            .categoryId(product.getCategoryId())
            .minPrice(product.getMinPrice())
            .createTime(product.getCreateTime())
            .build();
        send("product.created", event);
    }

    // ==================== publishUpdated ====================
    public void publishUpdated(Product product) {
        ProductUpdatedEvent event = ProductUpdatedEvent.builder()
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .images(product.getImages())
            .build();
        send("product.updated", event);
    }

    // ==================== publishPriceChanged ====================
    public void publishPriceChanged(Product product, BigDecimal oldMinPrice) {
        ProductPriceChangedEvent event = ProductPriceChangedEvent.builder()
            .productId(product.getId())
            .oldMinPrice(oldMinPrice)
            .newMinPrice(product.getMinPrice())
            .build();
        send("product.price.changed", event);
    }

    // ==================== publishStatusChanged ====================
    public void publishStatusChanged(Product product, Integer oldStatus) {
        ProductStatusChangedEvent event = ProductStatusChangedEvent.builder()
            .productId(product.getId())
            .oldStatus(oldStatus)
            .newStatus(product.getStatus())
            .build();
        send("product.status.changed", event);
    }

    /** 统一发送方法：使用 RabbitTemplate + 发布者确认 */
    private void send(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(PRODUCT_EXCHANGE, routingKey, event);
        log.debug("发布事件: routingKey={}, event={}", routingKey, event);
    }
}
