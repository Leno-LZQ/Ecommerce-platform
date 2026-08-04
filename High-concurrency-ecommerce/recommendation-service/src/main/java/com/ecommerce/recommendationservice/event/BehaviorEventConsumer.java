package com.ecommerce.recommendationservice.event;

import com.ecommerce.recommendationservice.config.RabbitConfig;
import com.ecommerce.recommendationservice.dto.BehaviorEvent;
import com.ecommerce.recommendationservice.dto.UserBehaviorRequest;
import com.ecommerce.recommendationservice.service.UserBehaviorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 消费 behavior.* 事件并写入行为日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventConsumer {

    private final UserBehaviorService userBehaviorService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.RECO_QUEUE)
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            BehaviorEvent event = objectMapper.readValue(body, BehaviorEvent.class);
            if (event.getUserId() == null || event.getProductId() == null || event.getBehaviorType() == null) {
                log.warn("行为事件字段缺失，忽略: {}", body);
                return;
            }
            UserBehaviorRequest request = new UserBehaviorRequest();
            request.setUserId(event.getUserId());
            request.setProductId(event.getProductId());
            request.setBehaviorType(event.getBehaviorType());
            request.setSessionId(event.getSessionId());
            request.setSearchKeyword(event.getSearchKeyword());
            request.setDurationMs(event.getDurationMs());
            userBehaviorService.record(request);
        } catch (Exception e) {
            log.error("处理行为事件失败: {}", body, e);
            // 不抛异常，避免 MQ 反复重试导致日志风暴
        }
    }

}
