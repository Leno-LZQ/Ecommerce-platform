package com.ecommerce.messageservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.enums.ConvStatus;
import com.ecommerce.messageservice.mapper.ConversationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }


    /**
     * 获取我的会话列表
     * 思路：按 user_id 查询，按更新时间倒序排（最近聊的排前面）
     */
    public List<Conversation> getMyConversations(Long userId){
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId)
            .eq("status", ConvStatus.ACTIVE.getCode())
            .orderByDesc("updated_at");
        return conversationMapper.selectList(wrapper);
    }

    /**
     * 获取商家会话列表
     * 思路：按 merchant_id 查询，按更新时间倒序排（最近聊的排前面）
     */
    public List<Conversation> getMerchantConversations(Long merchantId){
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id",merchantId)
            .eq("status",ConvStatus.ACTIVE.getCode())
            .orderByDesc("updated_at");
        return conversationMapper.selectList(wrapper);
    }

    /**
     * 创建新会话
     * 思路：先查是否已存在，存在则直接返回，不存在才新建
     * 这样同一个买家对同一个商家的同一个商品，只有一个会话窗口
     */

    public Conversation createConversation(Long userId,Long merchantId,Long productId,Long orderId){
        Conversation existing = conversationMapper.findByUserAndMerchant(userId,merchantId,productId,orderId);
        if(existing != null)
            return existing;
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setMerchantId(merchantId);
        conversation.setProductId(productId);
        conversation.setOrderId(orderId);
        conversation.setStatus(ConvStatus.ACTIVE.getCode());
        conversation.setUnreadUser(0);
        conversation.setUnreadMerchant(0);
        conversationMapper.insert(conversation);
        return conversation;
    }


    /**
     * 校验会话归属权（安全检查）
     * 防止用户 A 查看用户 B 的聊天记录
     */
    public void checkOwnership(Long conversationId,Long userId,Long merchantId){
        Conversation conversation  = conversationMapper.selectById(conversationId);
        if(conversation == null){
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        if(!conversation.getUserId().equals(userId) && !conversation.getMerchantId().equals(merchantId)){
            throw new BusinessException(ErrorCode.CONVERSATION_NO_PERMISSION);
        }
    }

}
