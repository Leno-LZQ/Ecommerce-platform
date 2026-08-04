package com.ecommerce.messageservice.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.messageservice.dto.WebSocketFrame;
import com.ecommerce.messageservice.entity.Conversation;
import com.ecommerce.messageservice.entity.enums.FrameType;
import com.ecommerce.messageservice.entity.enums.MessageType;
import com.ecommerce.messageservice.entity.enums.SenderType;
import com.ecommerce.messageservice.entity.Message;
import com.ecommerce.messageservice.mapper.ConversationMapper;
import com.ecommerce.messageservice.service.MessageService;
import com.ecommerce.messageservice.service.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * MessageWebSocketHandler
 *
 * WebSocket 连接建立后的"接线员"。
 * 负责处理连接的生命周期事件和消息收发。
 */
@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final ConversationMapper conversationMapper;
    private final PushService pushService;

    // 构造函数注入（Spring 推荐方式，比 @Autowired 字段注入更利于单元测试）
    public MessageWebSocketHandler(SessionManager sessionManager,
                                   MessageService messageService,
                                   ConversationMapper conversationMapper,
                                   PushService pushService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.conversationMapper = conversationMapper;
        this.pushService = pushService;
    }

    /**
     * 连接建立成功时触发
     * 相当于电话接通的那一刻
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");
        Long merchantId = (Long) session.getAttributes().get("merchantId");

        if ("USER".equals(role)) {
            sessionManager.registerUser(userId, session);
            log.info("用户 {} 连接成功", userId);
            // 用户上线后立即推送离线期间的消息
            pushService.pushOfflineMessages(userId, session);
        } else if ("MERCHANT".equals(role)) {
            sessionManager.registerMerchant(merchantId, session);
            log.info("商家 {} 连接成功", merchantId);
        } else {
            log.warn("未知角色: {}, 关闭连接", role);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 收到消息时触发
     * 相当于电话接通后，对方说话了
     *
     * message 的格式是 JSON 字符串，例如：
     * {"type":"MESSAGE", "conversationId":1001, "content":"你好", "messageType":"TEXT"}
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String json = message.getPayload();
        log.debug("收到消息: {}", json);

        WebSocketFrame frame;
        try {
            frame = JSON.parseObject(json, WebSocketFrame.class);
        } catch (JSONException e) {
            log.error("JSON 解析失败: {}", json, e);
            return;
        }

        if (frame == null || frame.getType() == null) {
            log.warn("消息格式错误，缺少 type 字段");
            return;
        }

        switch (frame.getType()) {
            case MESSAGE -> handleChatMessage(session, frame);
            case READ_ACK -> handleReadAck(session, frame);
            case TYPING -> handleTyping(session, frame);
            default -> log.warn("未知消息类型: {}", frame.getType());
        }
    }


    /**
     * 连接关闭时触发
     * 相当于挂断电话
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("连接关闭, status: {}", status);
        sessionManager.removeSession(session);
    }

    /**
     * 连接出错时触发
     * 相当于电话线断了
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("连接异常", exception);
        sessionManager.removeSession(session);
    }


    // ================================================================
    //  各类 WebSocket 帧的具体处理逻辑
    // ================================================================

    /**
     * 处理普��聊天消息（MESSAGE 帧）
     *
     * <p>这是整个 Handler 最核心的方法。完整流程：</p>
     * <ol>
     *   <li>从 WebSocket session 中取出握手时存入的身份信息</li>
     *   <li>组装发送者身份（买家 or 商家）</li>
     *   <li>调用 MessageService.sendMessage() 执行"先落库→再推送→离线兜底"</li>
     *   <li>用 try/catch 区分正常和异常回复：成功回 ACK 帧，失败回错误信息</li>
     * </ol>
     *
     * @param session 发送方连接（谁的浏览器/App 发的消息）
     * @param frame   前端发来的 JSON 帧，至少包含 type/conversationId/content
     */
    private void handleChatMessage(WebSocketSession session, WebSocketFrame frame) {

        // ----------------------------------------------------------
        // 第①步：从连接属性中取出身份信息
        // 这些信息是 HandshakeInterceptor 在 WS 握手时存入的
        // ----------------------------------------------------------
        Long userId = (Long) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");
        Long merchantId = (Long) session.getAttributes().get("merchantId");

        // ----------------------------------------------------------
        // 第②步：确认发送者身份
        // "USER"  → 买家在发消息，senderId = userId
        // 其他    → 商家在发消息，senderId = merchantId
        // ----------------------------------------------------------
        Long senderId;
        SenderType senderType;

        if ("USER".equals(role)) {
            senderId = userId;
            senderType = SenderType.USER;
        } else {
            senderId = merchantId;
            senderType = SenderType.MERCHANT;
        }

        // ----------------------------------------------------------
        // 第③步：调用核心发送逻辑
        // MessageService.sendMessage() 已经封装好：
        //   校验 → 落库 → 更新会话 → 推送 → 离线兜底
        // 全部在一个 @Transactional 里，保证数据一致性
        // ----------------------------------------------------------
        try {
            Message savedMsg = messageService.sendMessage(
                senderId,                     // 谁发的
                senderType,                   // 是什么身份
                frame.getConversationId(),    // 发给哪个会话
                frame.getContent(),           // 消息内容
                frame.getMessageType(),       // 文本 / 图片 / 商品卡片 / 订单卡片
                frame.getPayload()            // 附加数据（卡片等）
            );

            // ----------------------------------------------------------
            // 第④步：回复 ACK 帧 — 告诉浏览器"消息已收到并保存"
            //
            // 注意区分：ACK 是服务端对"接收成功"的确认，是业务层回应。
            //   它和 TCP 层的 ACK 不是一回事。前端收到这个帧后可以：
            //     - 去掉消息气泡上的加载动画
            //     - 拿到 messageId 用于后续重发/去重
            // ----------------------------------------------------------
            WebSocketFrame ack = new WebSocketFrame();
            ack.setType(FrameType.ACK);
            ack.setMessageId(savedMsg.getId());
            ack.setTimestamp(System.currentTimeMillis());

            String ackJson = JSON.toJSONString(ack);
            session.sendMessage(new TextMessage(ackJson));

        } catch (BusinessException e) {
            // 业务异常：比如会话不存在、权限不足、已归档等
            // 前端需要知道失败原因，所以把错误信息塞回 ACK
            log.warn("消息发送失败: conversationId={}, senderId={}, reason={}",
                frame.getConversationId(), senderId, e.getMessage());

            WebSocketFrame errorAck = new WebSocketFrame();
            errorAck.setType(FrameType.ACK);
            errorAck.setContent("发送失败: " + e.getMessage());
            errorAck.setTimestamp(System.currentTimeMillis());

            String errorJson = JSON.toJSONString(errorAck);
            try {
                session.sendMessage(new TextMessage(errorJson));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

        } catch (Exception e) {
            // 非预期异常：比如数据库挂了、JSON 序列化失败等
            // 前端不需要知道技术细节，只告知"服务器错误"
            log.error("消息发送异常", e);

            WebSocketFrame errorAck = new WebSocketFrame();
            errorAck.setType(FrameType.ACK);
            errorAck.setContent("服务器内部错误，请稍后重试");
            errorAck.setTimestamp(System.currentTimeMillis());

            String errorJson = JSON.toJSONString(errorAck);
            try {
                session.sendMessage(new TextMessage(errorJson));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * 处理已读回执（READ_ACK 帧）
     *
     * <p>当用户打开某个会话窗口时，前端发送 READ_ACK。</p>
     * <p>本方法做两件事：</p>
     * <ol>
     *   <li>把该会话中所有"对方发来且未读"的消息标记为已读</li>
     *   <li>把自己在该会话的未读数重置为 0</li>
     * </ol>
     *
     * @param session 当前连接
     * @param frame   前端发来的 READ_ACK 帧（必含 conversationId）
     */
    private void handleReadAck(WebSocketSession session, WebSocketFrame frame) {
        Long conversationId = frame.getConversationId();
        if (conversationId == null) {
            log.warn("READ_ACK 缺少 conversationId");
            return;
        }

        // ----------------------------------------------------------
        // 判断是谁在读消息，决定清零哪个 unread 字段
        // ----------------------------------------------------------
        String role = (String) session.getAttributes().get("role");

        // 调用 Service 层批量更新
        // 这个方法会在 SQL 层执行：
        //   UPDATE message SET is_read = 1
        //   WHERE conversation_id = ? AND sender_type != ? AND is_read = 0
        messageService.markConversationRead(conversationId, role);

        log.debug("已读确认: conversationId={}, readerRole={}", conversationId, role);
    }

    /**
     * 处理"正在输入"状态（TYPING 帧）
     *
     * <p>这是轻量级状态同步，不需要持久化。处理��程：</p>
     * <ol>
     *   <li>根据 conversationId 查出会话，找到"另一方"是谁</li>
     *   <li>把 TYPING 帧原样转发给对方</li>
     *   <li>对方前端收到后显示"对方正在输入..."</li>
     * </ol>
     *
     * <p>安全注意：必须校验发送者确实是该会话的参与者，
     * 不能随便把 typing 状态转发给不相关的人。</p>
     *
     * @param session 当前连接
     * @param frame   前端发来的 TYPING 帧（conversationId 必填）
     */
    private void handleTyping(WebSocketSession session, WebSocketFrame frame) {
        Long conversationId = frame.getConversationId();
        if (conversationId == null) {
            return;
        }

        // ----------------------------------------------------------
        // 第①步：查出会话，拿到双方 ID
        // ----------------------------------------------------------
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            return;  // 会话不存在，静默忽略
        }

        // ----------------------------------------------------------
        // 第②步：确定发送者是谁（买家 or 商家）
        // ----------------------------------------------------------
        Long userId = (Long) session.getAttributes().get("userId");
        Long merchantId = (Long) session.getAttributes().get("merchantId");

        // ----------------------------------------------------------
        // 第③步：安全校验 — 发送者必须是会话参与者
        // 防止用户 A 往用户 B 的会话里伪造 typing 状态
        // ----------------------------------------------------------
        boolean isUserInConv = conv.getUserId().equals(userId);
        boolean isMerchantInConv = conv.getMerchantId().equals(merchantId);
        if (!isUserInConv && !isMerchantInConv) {
            log.warn("TYPING 转发被拦截: 发送者不在会话中, convId={}, userId={}, merchantId={}",
                conversationId, userId, merchantId);
            return;
        }

        // ----------------------------------------------------------
        // 第④步：转发 TYPING 帧给另一方
        // ----------------------------------------------------------
        WebSocketFrame typingForward = new WebSocketFrame();
        typingForward.setType(FrameType.TYPING);
        typingForward.setConversationId(conversationId);
        typingForward.setTimestamp(System.currentTimeMillis());

        String json = JSON.toJSONString(typingForward);

        // 如果发送者是买家 → 转发给商家
        // 如果发送者是商家 → 转发给买家
        if (isUserInConv) {
            sessionManager.sendToMerchant(conv.getMerchantId(), json);
        } else {
            sessionManager.sendToUser(conv.getUserId(), json);
        }
    }

}
