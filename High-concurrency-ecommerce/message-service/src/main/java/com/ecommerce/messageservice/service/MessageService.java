package com.ecommerce.messageservice.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.messageservice.dto.WebSocketFrame;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.enums.ConvStatus;
import com.ecommerce.messageservice.entity.enums.FrameType;
import com.ecommerce.messageservice.entity.enums.MessageType;
import com.ecommerce.messageservice.entity.enums.SenderType;
import com.ecommerce.messageservice.entity.Message;
import com.ecommerce.messageservice.entity.PushRecord;
import com.ecommerce.messageservice.event.MessageEventPublisher;
import com.ecommerce.messageservice.mapper.ConversationMapper;
import com.ecommerce.messageservice.mapper.MessageMapper;
import com.ecommerce.messageservice.mapper.PushRecordMapper;
import com.ecommerce.messageservice.websocket.SessionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationService conversationService;
    private final SessionManager sessionManager;
    private final PushRecordMapper pushRecordMapper;
    private final RateLimitService rateLimitService;
    private final MessageEventPublisher eventPublisher;

    public MessageService(MessageMapper messageMapper,
                          ConversationMapper conversationMapper,
                          ConversationService conversationService,
                          SessionManager sessionManager,
                          PushRecordMapper pushRecordMapper,
                          RateLimitService rateLimitService,
                          MessageEventPublisher eventPublisher) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.conversationService = conversationService;
        this.sessionManager = sessionManager;
        this.pushRecordMapper = pushRecordMapper;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询会话历史消息
     * 思路：先校验权限，再分页查询
     */
    public List<Message> getHistory(Long conversationId, Long userId,
                                    Long merchantId, Integer page, Integer size){

        // 1. 校验权限（复用 ConversationService 的逻辑）
        conversationService.checkOwnership(conversationId, userId, merchantId);

        // 2. 分页查询（offset = (page-1) * size）
        Integer offset = (page - 1) * size;
        return messageMapper.findByConversationId(conversationId,offset,size);
    }


    @Transactional  // ②~⑥ 应在一个事务内，⑦（事件发布）在事务完成后生效
    public Message sendMessage(Long senderId, SenderType senderType,
                               Long conversationId, String content,
                               MessageType messageType, Object payload) {

        // ========== ① 限流检查（Redis 滑动窗口） ==========
        // 在锁库锁表之前先做限流，避免不必要的资源消耗
        // 系统消息（SYSTEM）不限流，因为是由内部事件触发的，不是用户手动发的
        if (senderType != SenderType.SYSTEM) {
            boolean allowed = rateLimitService.tryAcquire(conversationId, senderId);
            if (!allowed) {
                throw new BusinessException(ErrorCode.MESSAGE_SEND_TOO_FAST);
            }
        }

        // ========== ② 校验 ==========
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (conv.getStatus() == ConvStatus.ARCHIVED.getCode()) {
            throw new BusinessException(ErrorCode.CONVERSATION_ARCHIVED);
        }

        boolean isUserSender = (senderType == SenderType.USER
            && conv.getUserId().equals(senderId));
        boolean isMerchantSender = (senderType == SenderType.MERCHANT
            && conv.getMerchantId().equals(senderId));
        // 系统消息（SYSTEM）不受会话归属限制（内部事件触发）
        boolean isSystemSender = (senderType == SenderType.SYSTEM);
        if (!isUserSender && !isMerchantSender && !isSystemSender) {
            throw new BusinessException(ErrorCode.CONVERSATION_NO_PERMISSION);
        }

        // ========== ③ 落库（先存数据库，保证不丢消息） ==========
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderType(senderType);
        msg.setSenderId(senderId);
        msg.setMessageType(messageType);
        msg.setContent(content);
        msg.setIsRead(0);

        if (messageType == MessageType.PRODUCT_CARD
            || messageType == MessageType.ORDER_CARD) {
            msg.setAttachmentUrls(Collections.singletonList(JSON.toJSONString(payload)));
        }

        messageMapper.insert(msg);

        // ========== ④ 更新会话（原子操作） ==========
        String summary = buildSummary(messageType, content);

        LambdaUpdateWrapper<Conversation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Conversation::getId, conversationId)
            .set(Conversation::getLastMessage, summary)
            .set(Conversation::getUpdateTime, LocalDateTime.now())
            .setSql(senderType == SenderType.USER
                ? "unread_merchant = unread_merchant + 1"
                : "unread_user = unread_user + 1");
        conversationMapper.update(null, wrapper);

        // ========== ⑤ 推送 ==========
        Long receiverId = senderType == SenderType.USER
            ? conv.getMerchantId() : conv.getUserId();
        String receiverRole = senderType == SenderType.USER ? "MERCHANT" : "USER";

        WebSocketFrame pushFrame = new WebSocketFrame();
        pushFrame.setType(FrameType.MESSAGE);
        pushFrame.setConversationId(conversationId);
        pushFrame.setMessageId(msg.getId());
        pushFrame.setContent(content);
        pushFrame.setMessageType(messageType);
        pushFrame.setTimestamp(System.currentTimeMillis());

        String pushJson = JSON.toJSONString(pushFrame);
        boolean delivered = false;

        if ("USER".equals(receiverRole)) {
            if (sessionManager.isUserOnline(receiverId)) {
                sessionManager.sendToUser(receiverId, pushJson);
                delivered = true;
            }
        } else {
            Set<WebSocketSession> sessions = sessionManager.getMerchantSessions(receiverId);
            if (sessions != null && !sessions.isEmpty()) {
                sessionManager.sendToMerchant(receiverId, pushJson);
                delivered = true;
            }
        }

        // ========== ⑥ 离线处理 ==========
        if (!delivered) {
            PushRecord record = new PushRecord();
            record.setUserId(receiverId);
            record.setMessageId(msg.getId());
            record.setPushChannel("IN_APP");
            record.setPushStatus(0);
            pushRecordMapper.insert(record);
        }

        // ========== ⑦ 发布事件（异步，不阻塞主流程） ==========
        // 通知外部消费者"有新消息"，供离线推送通知服务等消费
        eventPublisher.publishNewMessage(
            msg.getId(), conversationId,
            senderType.name(), senderId,
            summary,
            receiverRole.equals("USER") ? receiverId : null,
            receiverRole.equals("MERCHANT") ? receiverId : null
        );

        return msg;
    }

    private String buildSummary(MessageType type, String content) {
        if (type == null) {
            return "[消息]";
        }

        return switch (type) {
            case TEXT -> truncate(content, 50);
            case IMAGE -> "[图片]";
            case PRODUCT_CARD -> "[商品]";
            case ORDER_CARD -> "[订单]";
            default -> "[消息]";
        };
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.trim().isEmpty()) {
            return "[空消息]";
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 标记整个会话的消息为已读 + 清零未读数
     *
     * <p>当用户打开会话窗口时调用。只标记"对方发来的"消息，不标记自己发的。</p>
     * <p>同时把己方的 unread 计数归零。</p>
     *
     * @param conversationId 会话 ID
     * @param readerRole     谁在读：USER（买家在读）或 MERCHANT（商家在读）
     */
    @Transactional
    public void markConversationRead(Long conversationId, String readerRole) {
        // ----------------------------------------------------------
        // 第①步：确定要排除的发送者类型
        // 如果买家在读（readerRole=USER），标记所有 MERCHANT 和 SYSTEM 发的消息
        // 如果商家在读（readerRole=MERCHANT），标记所有 USER 发的消息
        // ----------------------------------------------------------
        SenderType excludeType;
        String unreadField;

        if ("USER".equals(readerRole)) {
            excludeType = SenderType.USER;   // 买家自己发的不用标记
            unreadField = "unread_user";      // 清零买家的未读数
        } else {
            excludeType = SenderType.MERCHANT; // 商家自己发的不用标记
            unreadField = "unread_merchant";   // 清零商家的未读数
        }

        // ----------------------------------------------------------
        // 第②步：批量更新 message 表
        // SQL 等价于：
        //   UPDATE message SET is_read = 1
        //   WHERE conversation_id = ? AND sender_type != ? AND is_read = 0
        // ----------------------------------------------------------
        messageMapper.markAllAsRead(conversationId, excludeType);

        // ----------------------------------------------------------
        // 第③步：清零对应角色的未读数
        // ----------------------------------------------------------
        LambdaUpdateWrapper<Conversation> convWrapper = new LambdaUpdateWrapper<>();
        convWrapper.eq(Conversation::getId, conversationId)
                    .setSql(unreadField + " = 0");
        conversationMapper.update(null, convWrapper);
    }
}
