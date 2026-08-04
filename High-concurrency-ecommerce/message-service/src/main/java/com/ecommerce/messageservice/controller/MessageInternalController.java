package com.ecommerce.messageservice.controller;

import com.ecommerce.dto.message.SystemNotifyRequest;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.enums.MessageType;
import com.ecommerce.messageservice.entity.enums.SenderType;
import com.ecommerce.messageservice.mapper.ConversationMapper;
import com.ecommerce.messageservice.service.ConversationService;
import com.ecommerce.messageservice.service.MessageService;
import com.ecommerce.result.Result;
import org.springframework.web.bind.annotation.*;

/**
 * MessageInternalController — 内部接口（不走网关，供其他微服务通过 Feign 调用）
 *
 * <p>路径前缀：{@code /internal/messages}</p>
 *
 * <p>为什么叫"内部"接口？</p>
 * <ul>
 *   <li>这些接口不暴露给前端浏览器，只供服务间通信</li>
 *   <li>网关 RouteConfig 只配了 /api/messages/** 和 /ws/messages/**
 *       没有配 /internal/** 的路由，因此外部绝对访问不到</li>
 *   <li>调用方（如 order-service）通过 Feign + Nacos 服务发现直连，不经网关</li>
 * </ul>
 *
 * <p>与 MQ 事件的关系：</p>
 * <ul>
 *   <li>MQ（RabbitMQ）用于<b>异步</b>事件通知：order-service 发事件 → MQ → MessageEventConsumer</li>
 *   <li>Feign（本 Controller）用于<b>同步</b>调用：需要立刻拿到结果的场景</li>
 *   <li>两者互补，不冲突</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/messages")
public class MessageInternalController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ConversationMapper conversationMapper;

    public MessageInternalController(MessageService messageService,
                                     ConversationService conversationService,
                                     ConversationMapper conversationMapper) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 系统消息注入
     *
     * <p>由 order-service / payment-service 等调用，向用户-商家对话中注入系统消息。</p>
     *
     * <p>调用方示例（order-service 中用 Feign）：</p>
     * <pre>
     *   messageClient.systemNotify(new SystemNotifyRequest()
     *       .setUserId(order.getUserId())
     *       .setMerchantId(order.getMerchantId())
     *       .setOrderId(order.getId())
     *       .setContent("您的订单已支付成功"));
     * </pre>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>查找或创建用户与商家之间的会话</li>
     *   <li>以 SYSTEM 身份发送文本消息</li>
     *   <li>消息会通过 WebSocket 实时推送给在线用户，离线用户写入 push_record</li>
     * </ol>
     *
     * @param request 系统通知请求（userId/merchantId/content 必填）
     * @return Result.success()
     */
    @PostMapping("/system-notify")
    public Result<Void> systemNotify(@RequestBody SystemNotifyRequest request) {

        // 查找或创建会话
        Conversation conv = conversationService.createConversation(
            request.getUserId(),
            request.getMerchantId(),
            request.getProductId(),
            request.getOrderId()
        );

        // 以 SYSTEM 身份发送消息
        messageService.sendMessage(
            0L,                         // senderId=0 表示系统
            SenderType.SYSTEM,
            conv.getId(),
            request.getContent(),
            MessageType.TEXT,
            null
        );

        return Result.success();
    }

    /**
     * 校验会话归属
     *
     * <p>供 order-service 在展示订单详情时判断"这个订单有关联的会话吗？"。</p>
     *
     * <p>调用方示例：</p>
     * <pre>
     *   // order-service 中
     *   Boolean exists = messageClient.conversationExists(orderId, userId);
     *   if (exists) {
     *       // 前端显示"查看聊天记录"按钮
     *   }
     * </pre>
     *
     * @param conversationId 会话 ID
     * @param userId         要校验的用户 ID
     * @return true=该用户在此会话中，false=不在
     */
    @GetMapping("/conversations/{conversationId}/exists")
    public Result<Boolean> conversationExists(
            @PathVariable Long conversationId,
            @RequestParam Long userId) {

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            return Result.success(false);
        }

        // 用户是买家也���是商家都算"在这个会话中"
        boolean exists = conv.getUserId().equals(userId)
                      || conv.getMerchantId().equals(userId);

        return Result.success(exists);
    }
}
