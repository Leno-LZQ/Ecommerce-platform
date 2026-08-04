package com.ecommerce.messageservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.messageservice.dto.CreateConversationRequest;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.Message;
import com.ecommerce.messageservice.service.ConversationService;
import com.ecommerce.messageservice.service.MessageService;
import com.ecommerce.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    // 构造函数注入（Spring 推荐的依赖注入方式）
    public ConversationController(ConversationService conversationService,
                                  MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /**
     * GET /api/messages/conversations
     * 获取当前用户的会话列表
     */
    @GetMapping("/conversations")
    public Result<List<Conversation>> getMyConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Conversation> list = conversationService.getMyConversations(userId);
        return Result.success(list);
    }

    /**
     * POST /api/messages/conversations
     * 发起新会话（比如买家点击"联系商家"按钮）
     */
    @PostMapping("/conversations")
    public Result<Conversation> createConversation(@RequestBody CreateConversationRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        Conversation conv = conversationService.createConversation(
            userId,
            request.getMerchantId(),
            request.getProductId(),
            request.getOrderId()
        );
        return Result.success(conv);
    }

    /**
     * GET /api/messages/conversations/{id}/messages?page=1&size=20
     * 查看会话历史消息
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> getHistory(
            @PathVariable("id") Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Long userId = SecurityUtils.getCurrentUserId();
        Long merchantId = SecurityUtils.getCurrentMerchantId();

        List<Message> messages = messageService.getHistory(
            conversationId, userId, merchantId, page, size
        );
        return Result.success(messages);
    }

}
