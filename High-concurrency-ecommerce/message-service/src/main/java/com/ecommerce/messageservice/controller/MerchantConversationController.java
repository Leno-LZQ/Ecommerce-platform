package com.ecommerce.messageservice.controller;

import com.ecommerce.Security.SecurityUtils;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.service.ConversationService;
import com.ecommerce.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages/merchant")
public class MerchantConversationController {

    private final ConversationService conversationService;

    // 构造函数注入
    public MerchantConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * GET /api/messages/merchant/conversations
     * 商家查看自己的会话列表
     */
    @GetMapping("/conversations")
    public Result<List<Conversation>> getMerchantConversations() {
        Long merchantId = SecurityUtils.getCurrentMerchantId();
        List<Conversation> list = conversationService.getMerchantConversations(merchantId);
        return Result.success(list);
    }

}
