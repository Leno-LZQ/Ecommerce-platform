package com.ecommerce.messageservice.event;

import com.ecommerce.messageservice.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MessageEventPublisher — 消息服务事件发布者
 *
 * <p>发布 message.new 事件，供"离线推送通知服务"等外部消费者监听。</p>
 *
 * <p>典型消费方：</p>
 * <ul>
 *   <li>站内推送服务（通知栏小红点）</li>
 *   <li>APP 离线推送服务（APNs / FCM）</li>
 *   <li>audit 审计日志服务</li>
 * </ul>
 *
 * <p>发布时机：每次 MessageService.sendMessage() 成功落库后调用。</p>
 */
@Slf4j
@Component
public class MessageEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MessageEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发布"新消息"事件
     *
     * <p>事件字段：</p>
     * <pre>
     *   messageId     — 消息 ID
     *   conversationId — 会话 ID
     *   senderType    — 发送者类型（USER/MERCHANT/SYSTEM）
     *   senderId      — 发送者 ID
     *   content       — 消息摘要（用于推送预览）
     *   userId        — 接收方用户 ID（用于离线推送）
     *   merchantId    — 接收方商家 ID
     *   timestamp     — 事件时间戳
     * </pre>
     */
    public void publishNewMessage(Long messageId, Long conversationId,
                                   String senderType, Long senderId,
                                   String content, Long userId, Long merchantId) {

        Map<String, Object> event = new HashMap<>();
        event.put("messageId", messageId);
        event.put("conversationId", conversationId);
        event.put("senderType", senderType);
        event.put("senderId", senderId);
        event.put("content", truncate(content, 80));  // 推送预览不需要完整内容
        event.put("userId", userId);
        event.put("merchantId", merchantId);
        event.put("timestamp", System.currentTimeMillis());

        rabbitTemplate.convertAndSend(
            RabbitConfig.MESSAGE_EXCHANGE,
            "message.new",
            event
        );

        log.debug("发布事件 message.new: messageId={}, userId={}", messageId, userId);
    }

    /** 截断字符串，防止事件体过大 */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
