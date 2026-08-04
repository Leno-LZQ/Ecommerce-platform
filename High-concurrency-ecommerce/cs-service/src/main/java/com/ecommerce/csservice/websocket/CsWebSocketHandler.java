package com.ecommerce.csservice.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.ecommerce.csservice.dto.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 客服 WebSocket 消息处理器
 *
 * <p>当前主要做连接管理 + 系统通知推送。</p>
 * <p>工单消息的发送仍走 REST API（更便于校验、限流、审计），
 * 发送成功后由服务端通过 WS 推送给接收方。</p>
 */
@Slf4j
@Component
public class CsWebSocketHandler extends TextWebSocketHandler {

    private final CsSessionManager csSessionManager;

    public CsWebSocketHandler(CsSessionManager csSessionManager) {
        this.csSessionManager = csSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        String role = (String) session.getAttributes().get("role");

        if ("USER".equals(role)) {
            csSessionManager.registerUser(userId, session);
            log.info("用户 {} 接入客服 WS", userId);
        } else {
            Long agentId = (Long) session.getAttributes().get("agentId");
            csSessionManager.registerAgent(agentId, session);
            log.info("客服 {}(agentId={}) 接入客服 WS", userId, agentId);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String json = message.getPayload();
        log.debug("收到客服 WS 消息: {}", json);

        try {
            JSON.parseObject(json, WebSocketFrame.class);
        } catch (JSONException e) {
            log.warn("客服 WS 消息 JSON 解析失败: {}", json);
            return;
        }

        // 目前客服消息通过 REST 发送，WS 仅用于服务端推送。
        // 如需实现 TYPING 状态，可在此扩展。
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("客服 WS 连接关闭, status={}", status);
        csSessionManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("客服 WS 连接异常", exception);
        csSessionManager.removeSession(session);
    }
}
