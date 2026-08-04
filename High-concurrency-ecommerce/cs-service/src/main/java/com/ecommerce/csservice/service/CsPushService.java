package com.ecommerce.csservice.service;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.csservice.dto.WebSocketFrame;
import com.ecommerce.csservice.entity.TicketMessage;
import com.ecommerce.csservice.websocket.CsSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 客服 WebSocket 推送服务
 */
@Slf4j
@Service
public class CsPushService {

    private final CsSessionManager csSessionManager;

    public CsPushService(CsSessionManager csSessionManager) {
        this.csSessionManager = csSessionManager;
    }

    /**
     * 推送给用户
     */
    public void pushToUser(Long userId, TicketMessage message) {
        if (!csSessionManager.isUserOnline(userId)) {
            log.debug("用户 {} 不在线，客服消息暂存于数据库待上线补推", userId);
            return;
        }
        csSessionManager.sendToUser(userId, buildJson(message));
    }

    /**
     * 推送给客服
     */
    public void pushToAgent(Long agentId, TicketMessage message) {
        if (!csSessionManager.isAgentOnline(agentId)) {
            log.debug("客服 {} 不在线，工单消息暂存于数据库待上线补推", agentId);
            return;
        }
        csSessionManager.sendToAgent(agentId, buildJson(message));
    }

    /**
     * 推送系统通知（如工单状态变更）
     */
    public void pushSystemNotifyToUser(Long userId, String type, Long ticketId, String content) {
        WebSocketFrame frame = new WebSocketFrame();
        frame.setType(type);
        frame.setTicketId(ticketId);
        frame.setContent(content);
        frame.setTimestamp(System.currentTimeMillis());
        csSessionManager.sendToUser(userId, JSON.toJSONString(frame));
    }

    private String buildJson(TicketMessage message) {
        WebSocketFrame frame = new WebSocketFrame();
        frame.setType("MESSAGE");
        frame.setTicketId(message.getTicketId());
        frame.setMessageId(message.getId());
        frame.setSenderId(message.getSenderId());
        frame.setSenderType(message.getSenderType());
        frame.setContent(message.getContent());
        frame.setTimestamp(System.currentTimeMillis());
        return JSON.toJSONString(frame);
    }
}
