package com.ecommerce.csservice.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服 WebSocket 会话管理器
 *
 * <p>维护用户连接和客服连接的映射关系。</p>
 */
@Component
public class CsSessionManager {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> agentSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionToInfo = new ConcurrentHashMap<>();

    public void registerUser(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToInfo.put(session.getId(), new SessionInfo("USER", userId));
    }

    public void registerAgent(Long agentId, WebSocketSession session) {
        agentSessions.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToInfo.put(session.getId(), new SessionInfo("AGENT", agentId));
    }

    public void removeSession(WebSocketSession session) {
        SessionInfo info = sessionToInfo.remove(session.getId());
        if (info == null) {
            return;
        }
        if ("USER".equals(info.getRole())) {
            Set<WebSocketSession> sessions = userSessions.get(info.getId());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(info.getId());
                }
            }
        } else {
            Set<WebSocketSession> sessions = agentSessions.get(info.getId());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    agentSessions.remove(info.getId());
                }
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public boolean isAgentOnline(Long agentId) {
        Set<WebSocketSession> sessions = agentSessions.get(agentId);
        return sessions != null && !sessions.isEmpty();
    }

    public void sendToUser(Long userId, String messageJson) {
        send(userSessions.get(userId), messageJson);
    }

    public void sendToAgent(Long agentId, String messageJson) {
        send(agentSessions.get(agentId), messageJson);
    }

    private void send(Set<WebSocketSession> sessions, String messageJson) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(messageJson));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
