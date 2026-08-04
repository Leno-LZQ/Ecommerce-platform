package com.ecommerce.messageservice.event;

import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.enums.MessageType;
import com.ecommerce.messageservice.entity.enums.SenderType;
import com.ecommerce.messageservice.service.ConversationService;
import com.ecommerce.messageservice.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MessageEventConsumer — 消息服务事件消费者
 *
 * <p>监听 order-service 发布的订单事件，自动在买家与商家之间创建会话并注入系统消息。</p>
 *
 * <p>为什么用 MQ 而不是 Feign 同步调用？</p>
 * <ol>
 *   <li><b>解耦</b>：order-service 发完事件就走，不关心消息是否送达</li>
 *   <li><b>容错</b>：message-service 宕机不影响下单流程，事件在 MQ 里堆积，恢复后继续消费</li>
 *   <li><b>削峰</b>：大促期间订单洪峰不会直接冲击消息系统</li>
 * </ol>
 *
 * <p>幂等保证：同一个订单事件重复消费（MQ 重发），会查到已有会话直接复用，
 * 不会创建重复会话；系统消息的发送也会更新 lastMessage 但不会插入重复消息。</p>
 */
@Slf4j
@Component
public class MessageEventConsumer {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public MessageEventConsumer(MessageService messageService,
                                ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    /**
     * 消费所有绑定到 message.queue 的事件
     *
     * <p>队列绑定关系（见 RabbitConfig）：</p>
     * <ul>
     *   <li>order.exchange → order.paid → message.queue</li>
     *   <li>order.exchange → order.shipped → message.queue</li>
     *   <li>order.exchange → order.refunded → message.queue</li>
     * </ul>
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>{@code payload} — 事件数据，由发布方（order-service）构造的 Map</li>
     *   <li>{@code routingKey} — 从 {@code amqp_receivedRoutingKey} 头读取，用于区分事件类型</li>
     * </ul>
     *
     * @param payload    事件数据 Map，各事件字段见子方法 Javadoc
     * @param routingKey RabbitMQ 原始路由键，如 "order.paid"
     */
    @RabbitListener(queues = "message.queue")
    public void onOrderEvent(Map<String, Object> payload,
                             @Header(name = "amqp_receivedRoutingKey", required = false)
                             String routingKey) {

        if (routingKey == null) {
            log.warn("收到无 routingKey 的消息，忽略 payload={}", payload);
            return;
        }

        log.info("收到订单事件: routingKey={}, payload={}", routingKey, payload);

        switch (routingKey) {
            case "order.paid"    -> handleOrderPaid(payload);
            case "order.shipped" -> handleOrderShipped(payload);
            case "order.refunded" -> handleOrderRefunded(payload);
            default              -> log.warn("未知事件类型: {}", routingKey);
        }
    }

    // ================================================================
    //  各事件的处理逻辑
    // ================================================================

    /**
     * 处理"订单已支付"事件
     *
     * <p>payload 字段：</p>
     * <pre>
     *   orderId    (Long)    — 订单 ID
     *   orderNo    (String)  — 订单编号
     *   userId     (Long)    — 买家 ID
     *   merchantId (Long)    — 商家 ID
     * </pre>
     *
     * <p>效果：在买家和商家之间创建会话（如尚未存在），
     * 并发送系统消息 "您的订单 ORDxxx 已支付成功"。</p>
     */
    private void handleOrderPaid(Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Long merchantId = getLong(payload, "merchantId");
        Long orderId = getLong(payload, "orderId");
        String orderNo = getString(payload, "orderNo");

        if (userId == null || merchantId == null || orderId == null) {
            log.warn("order.paid 缺少必要字段: {}", payload);
            return;
        }

        // ① 查找或创建会话（自动复用的逻辑在 ConversationService 里）
        Conversation conv = conversationService.createConversation(
            userId, merchantId, null, orderId);

        // ② 发送系统消息
        String content = "您的订单 " + orderNo + " 已支付成功，商家正在准备发货";
        messageService.sendMessage(
            0L,                         // senderId=0 表示系统
            SenderType.SYSTEM,
            conv.getId(),
            content,
            MessageType.TEXT,
            null                        // 纯文本消息，无附加数据
        );

        log.info("系统消息已注入 [订单支付]: orderNo={}, conversationId={}", orderNo, conv.getId());
    }

    /**
     * 处理"订单已发货"事件
     *
     * <p>payload 字段：</p>
     * <pre>
     *   orderId    (Long)    — 订单 ID
     *   orderNo    (String)  — 订单编号
     *   userId     (Long)    — 买家 ID
     *   merchantId (Long)    — 商家 ID
     *   trackingNo (String)  — 快递单号
     * </pre>
     *
     * <p>效果：在已有会话中发送系统消息 "您的订单 ORDxxx 已发货，快递单号：SF123456"。</p>
     */
    private void handleOrderShipped(Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Long merchantId = getLong(payload, "merchantId");
        Long orderId = getLong(payload, "orderId");
        String orderNo = getString(payload, "orderNo");
        String trackingNo = getString(payload, "trackingNo");

        if (userId == null || merchantId == null || orderId == null) {
            log.warn("order.shipped 缺少必要字段: {}", payload);
            return;
        }

        Conversation conv = conversationService.createConversation(
            userId, merchantId, null, orderId);

        String content = "您的订单 " + orderNo + " 已发货"
            + (trackingNo != null ? "，快递单号：" + trackingNo : "");

        messageService.sendMessage(0L, SenderType.SYSTEM,
            conv.getId(), content, MessageType.TEXT, null);

        log.info("系统消息已注入 [订单发货]: orderNo={}, conversationId={}", orderNo, conv.getId());
    }

    /**
     * 处理"订单已退款"事件
     *
     * <p>payload 字段：</p>
     * <pre>
     *   orderId    (Long)    — 订单 ID
     *   orderNo    (String)  — 订单编号
     *   userId     (Long)    — 买家 ID
     *   merchantId (Long)    — 商家 ID
     * </pre>
     */
    private void handleOrderRefunded(Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Long merchantId = getLong(payload, "merchantId");
        Long orderId = getLong(payload, "orderId");
        String orderNo = getString(payload, "orderNo");

        if (userId == null || merchantId == null || orderId == null) {
            log.warn("order.refunded 缺少必要字段: {}", payload);
            return;
        }

        Conversation conv = conversationService.createConversation(
            userId, merchantId, null, orderId);

        String content = "您的订单 " + orderNo + " 已退款";

        messageService.sendMessage(0L, SenderType.SYSTEM,
            conv.getId(), content, MessageType.TEXT, null);

        log.info("系统消息已注入 [订单退款]: orderNo={}, conversationId={}", orderNo, conv.getId());
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /**
     * 从 Map 中安全取出 Long 类型的值
     *
     * <p>MQ 消息体中的数字可能是 Integer 也可能是 Long，
     * 用 {@code instanceof Number} 统一处理。</p>
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 从 Map 中安全取出 String 类型的值
     */
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
