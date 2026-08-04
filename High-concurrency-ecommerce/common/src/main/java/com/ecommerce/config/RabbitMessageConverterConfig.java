package com.ecommerce.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息转换器统一配置。
 * <p>默认 SimpleMessageConverter 只支持 String/byte[]/Serializable，
 * 项目事件均为 POJO（ProductCreatedEvent/OrderPaidEvent 等），
 * 统一使用 Jackson JSON 序列化，避免发布事件时抛 IllegalArgumentException。
 */
@Configuration
@ConditionalOnClass(MessageConverter.class)
public class RabbitMessageConverterConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
