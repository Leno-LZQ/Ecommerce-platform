package com.ecommerce.client;

import com.ecommerce.dto.message.SystemNotifyRequest;
import com.ecommerce.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 消息服务 Feign 客户端（供 order-service / payment-service 等调用）
 *
 * <p>调用方式：在调用方注入 {@code MessageClient}，直接调方法即可。
 * Feign 会自动解析 message-service 的地址并发起 HTTP 请求。</p>
 */
@FeignClient(name = "message-service")
public interface MessageClient {

    /**
     * 系统消息注入
     *
     * <p>场景：订单支付成功、发货、退款等需要通知用户的操作。</p>
     *
     * @param request userId/merchantId/content 必填，productId/orderId 可选
     * @return Result.success()
     */
    @PostMapping("/internal/messages/system-notify")
    Result<Void> systemNotify(@RequestBody SystemNotifyRequest request);

    /**
     * 校验会��归属
     *
     * <p>场景：order-service 展示订单详情时，判断是否显示"查看聊天记录"按钮。</p>
     *
     * @param conversationId 会话 ID
     * @param userId         要校验的用户 ID
     * @return true=该用户在此会话中
     */
    @GetMapping("/internal/messages/conversations/{conversationId}/exists")
    Result<Boolean> conversationExists(@PathVariable Long conversationId,
                                       @RequestParam Long userId);
}
