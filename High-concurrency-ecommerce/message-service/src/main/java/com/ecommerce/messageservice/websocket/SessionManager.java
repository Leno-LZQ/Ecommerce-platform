package com.ecommerce.messageservice.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    private final Map<Long, Set<WebSocketSession>> userSessions
        = new ConcurrentHashMap<>();

    private final Map<Long, Set<WebSocketSession>> merchantSessions
        = new ConcurrentHashMap<>();

    // 反向映射：通过连接找用户 ID（断开连接时需要清理）
    private final Map<String, SessionInfo> sessionToUser
        = new ConcurrentHashMap<>();

    /**
     * 用户连接建立时，注册到通讯录
     */
    public void registerUser(Long userId, WebSocketSession session){
        // computeIfAbsent: 如果 key 不存在，创建一个新的空 Set
        // 相当于：如果通讯录里没有这个人，就新建一页
        Set<WebSocketSession> sessions = userSessions
            .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());

        sessions.add(session);

        // 记录反向映射
        sessionToUser.put(session.getId(), new SessionInfo("USER", userId));
    }

    /**
     * 商家连接建立时，注册到通讯录
     */
    public void registerMerchant(Long merchantId, WebSocketSession session) {
        Set<WebSocketSession> sessions = merchantSessions
            .computeIfAbsent(merchantId, k -> ConcurrentHashMap.newKeySet());
        sessions.add(session);
        sessionToUser.put(session.getId(), new SessionInfo("MERCHANT", merchantId));
    }

    /**
     * 连接断开时，从通讯录移除
     */
    public void removeSession(WebSocketSession session){
        SessionInfo info = sessionToUser.remove(session.getId());
        if(info == null)
            return;

        if(info.getRole().equals("USER")){
            Set<WebSocketSession> sessions = userSessions.get(info.getId());
            if(sessions != null){
                sessions.remove(session);
                if(sessions.isEmpty())
                    userSessions.remove(info.getId());
            }
        }
        else{
            Set<WebSocketSession> sessions = merchantSessions.get(info.getId());
            if(sessions != null){
                sessions.remove(session);
                if(sessions.isEmpty())
                    merchantSessions.remove(info.getId());
            }
        }

    }

    /**
     * 向指定用户的所有设备推送消息
     */
    public void sendToUser(Long userId,String messageJson){
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty())
            return;

        for(WebSocketSession session : sessions){
            if(session.isOpen()){
                try {
                    session.sendMessage(new TextMessage(messageJson));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 向指定商家的所有设备推送消息
     */

    public void sendToMerchant(Long merchantId, String messageJson) {
        Set<WebSocketSession> sessions = merchantSessions.get(merchantId);
        if (sessions == null || sessions.isEmpty()) return;

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

    /**
     * 判断用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<WebSocketSession> getMerchantSessions(Long merchantId){
        return merchantSessions.get(merchantId);
    }

}
